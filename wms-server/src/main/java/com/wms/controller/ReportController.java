package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.InventoryTransaction;
import com.wms.model.entity.Item;
import com.wms.repository.InventoryRepository;
import com.wms.repository.InventoryTransactionRepository;
import com.wms.repository.ItemRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final InventoryRepository inventories;
    private final InventoryTransactionRepository transactions;
    private final ItemRepository items;

    public ReportController(InventoryRepository i, InventoryTransactionRepository t, ItemRepository items) {
        inventories = i;
        transactions = t;
        this.items = items;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        List<Inventory> all = inventories.findAllDetailed();
        List<Map<String, Object>> alerts = smartAlerts(all);
        BigDecimal qty = all.stream().map(Inventory::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = all.stream().map(Inventory::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        List<InventoryTransaction> recent = transactions.findRecentDetailed();
        BigDecimal inbound = recent.stream()
                .filter(t -> t.getTransactionType().equals("in") && t.getTransactionAt().toLocalDate().equals(today))
                .map(InventoryTransaction::getTotalCostAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outbound = recent.stream()
                .filter(t -> t.getTransactionType().equals("out") && t.getTransactionAt().toLocalDate().equals(today))
                .map(InventoryTransaction::getSaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> categoryDist = categoryDistribution(all);
        List<Map<String, Object>> valueByCategory = valueByCategory(all);
        List<Map<String, Object>> dailyTrend = dailyTrendData();
        List<Map<String, Object>> monthlyProfit = monthlyProfitData();
        List<Map<String, Object>> topItemsByValue = topItemsByValue(all, 8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockItemCount", all.stream().filter(x -> x.getQuantity().signum() > 0).count());
        result.put("totalQuantity", qty);
        result.put("totalAmount", amount);
        result.put("todayInboundAmount", inbound);
        result.put("todayOutboundAmount", outbound);
        result.put("alertCount", (long) alerts.size());
        result.put("alerts", alerts);
        result.put("recentTransactions", recent.stream().limit(8).map(this::tx).toList());
        result.put("categoryDistribution", categoryDist);
        result.put("valueByCategory", valueByCategory);
        result.put("dailyTrend", dailyTrend);
        result.put("monthlyProfit", monthlyProfit);
        result.put("topItemsByValue", topItemsByValue);
        return ApiResponse.ok(result);
    }

    @GetMapping("/stock-alert")
    public ApiResponse<List<Map<String, Object>>> stockAlert() {
        return ApiResponse.ok(smartAlerts(inventories.findAllDetailed()));
    }

    @GetMapping("/profit")
    public ApiResponse<List<Map<String, Object>>> profit() {
        return ApiResponse.ok(transactions.findByTransactionType("out").stream().map(this::tx).toList());
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<Map<String, Object>>> anomalies() {
        List<Inventory> all = inventories.findAllDetailed();
        List<InventoryTransaction> recentTxns = transactions.findRecentDetailed();
        List<Map<String, Object>> result = new ArrayList<>();

        // 检测1：连续3天库存下降
        result.addAll(detectContinuousDecline(all, recentTxns));

        // 检测2：出库单缺少批次号
        result.addAll(detectMissingBatch(recentTxns));

        // 检测3：出库数量异常（单日出库 > 安全库存50%）
        result.addAll(detectAbnormalOutbound(recentTxns));

        return ApiResponse.ok(result);
    }

    /** 智能预警：结合安全库存、出库趋势、建议补货量 */
    private List<Map<String, Object>> smartAlerts(List<Inventory> inventory) {
        Map<Long, BigDecimal> sums = inventory.stream()
                .collect(Collectors.groupingBy(x -> x.getItem().getId(),
                        Collectors.mapping(Inventory::getQuantity, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        // 近7天出库量统计
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        List<InventoryTransaction> weekTxns = transactions.findByTransactionAtBetween(
                weekAgo.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Map<Long, BigDecimal> weekOutbound = new HashMap<>();
        for (InventoryTransaction t : weekTxns) {
            if ("out".equals(t.getTransactionType()) || "transfer_out".equals(t.getTransactionType())) {
                weekOutbound.merge(t.getItem().getId(), t.getQuantity().abs(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Item i : items.findAll()) {
            if (!i.getStatus()) continue;
            BigDecimal current = sums.getOrDefault(i.getId(), BigDecimal.ZERO);
            if (current.compareTo(i.getSafetyStock()) >= 0) continue;

            BigDecimal weekOut = weekOutbound.getOrDefault(i.getId(), BigDecimal.ZERO);
            BigDecimal dailyAvg = weekOut.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            BigDecimal suggestedOrder = i.getSafetyStock().subtract(current).add(dailyAvg.multiply(BigDecimal.valueOf(3)))
                    .max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);

            String priority;
            if (current.compareTo(i.getSafetyStock().multiply(new BigDecimal("0.5"))) < 0
                    || (dailyAvg.compareTo(BigDecimal.ZERO) > 0
                    && dailyAvg.compareTo(i.getSafetyStock().multiply(new BigDecimal("0.2"))) > 0)) {
                priority = "HIGH";
            } else if (current.compareTo(i.getSafetyStock()) < 0) {
                priority = "MEDIUM";
            } else {
                priority = "LOW";
            }

            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("itemId", i.getId());
            alert.put("itemCode", i.getCode());
            alert.put("itemName", i.getName());
            alert.put("unit", i.getUnit());
            alert.put("safetyStock", i.getSafetyStock());
            alert.put("currentStock", current);
            alert.put("shortage", i.getSafetyStock().subtract(current));
            alert.put("priority", priority);
            alert.put("dailyAvgOut", dailyAvg);
            alert.put("suggestedOrder", suggestedOrder);
            result.add(alert);
        }
        result.sort((a, b) -> {
            String pa = (String) a.get("priority");
            String pb = (String) b.get("priority");
            return pa.equals(pb) ? 0 : pa.equals("HIGH") ? -1 : pb.equals("HIGH") ? 1 : pa.equals("MEDIUM") ? -1 : 1;
        });
        return result;
    }

    /** 检测连续3天库存下降 */
    private List<Map<String, Object>> detectContinuousDecline(List<Inventory> all, List<InventoryTransaction> recentTxns) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 按物品分组并按日期排序
        Map<Long, List<InventoryTransaction>> byItem = recentTxns.stream()
                .filter(t -> "out".equals(t.getTransactionType()))
                .collect(Collectors.groupingBy(t -> t.getItem().getId(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<InventoryTransaction>> entry : byItem.entrySet()) {
            List<InventoryTransaction> txns = entry.getValue();
            if (txns.size() < 3) continue;

            // 检查最近3天是否每天都有出库
            Set<LocalDate> dates = txns.stream()
                    .map(t -> t.getTransactionAt().toLocalDate())
                    .filter(d -> d.isAfter(LocalDate.now().minusDays(4)))
                    .collect(Collectors.toSet());
            if (dates.size() >= 3) {
                Item item = txns.get(0).getItem();
                result.add(Map.of(
                        "type", "CONTINUOUS_DECLINE",
                        "severity", "MEDIUM",
                        "itemCode", item.getCode(),
                        "itemName", item.getName(),
                        "description", "连续多天有出库记录，库存可能快速下降",
                        "days", dates.size()
                ));
            }
        }
        return result;
    }

    /** 检测出库单缺少批次号 */
    private List<Map<String, Object>> detectMissingBatch(List<InventoryTransaction> recentTxns) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (InventoryTransaction t : recentTxns) {
            if ("out".equals(t.getTransactionType()) && (t.getLocation() == null || t.getLocation().getCode() == null)) {
                String key = t.getItem().getCode() + "@" + t.getReferenceNo();
                if (seen.add(key)) {
                    result.add(Map.of(
                            "type", "MISSING_BATCH",
                            "severity", "LOW",
                            "itemCode", t.getItem().getCode(),
                            "itemName", t.getItem().getName(),
                            "referenceNo", t.getReferenceNo(),
                            "description", "出库单缺少批次号信息"
                    ));
                }
            }
        }
        return result;
    }

    /** 检测单日出库数量异常 */
    private List<Map<String, Object>> detectAbnormalOutbound(List<InventoryTransaction> recentTxns) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, BigDecimal> dailyOutbound = new HashMap<>();
        for (InventoryTransaction t : recentTxns) {
            if ("out".equals(t.getTransactionType())) {
                String key = t.getItem().getId() + "@" + t.getTransactionAt().toLocalDate();
                dailyOutbound.merge(key, t.getQuantity().abs(), BigDecimal::add);
            }
        }
        for (Map.Entry<String, BigDecimal> entry : dailyOutbound.entrySet()) {
            String[] parts = entry.getKey().split("@");
            Long itemId = Long.parseLong(parts[0]);
            BigDecimal qty = entry.getValue();
            Optional<Item> itemOpt = items.findById(itemId);
            if (itemOpt.isPresent() && itemOpt.get().getSafetyStock().compareTo(BigDecimal.ZERO) > 0
                    && qty.compareTo(itemOpt.get().getSafetyStock().multiply(new BigDecimal("0.5"))) > 0) {
                Item item = itemOpt.get();
                result.add(Map.of(
                        "type", "ABNORMAL_OUTBOUND",
                        "severity", "HIGH",
                        "itemCode", item.getCode(),
                        "itemName", item.getName(),
                        "quantity", qty,
                        "description", "单日出库量 " + qty + " 超过安全库存50%（" + item.getSafetyStock() + "）"
                ));
            }
        }
        return result;
    }

    /** 按分类统计库存数量分布 */
    private List<Map<String, Object>> categoryDistribution(List<Inventory> inventory) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Inventory inv : inventory) {
            String catName = inv.getItem().getCategory() == null ? "未分类" : inv.getItem().getCategory().getName();
            byCategory.merge(catName, inv.getQuantity(), BigDecimal::add);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            result.add(item);
        }
        return result;
    }

    /** 按分类统计库存金额分布 */
    private List<Map<String, Object>> valueByCategory(List<Inventory> inventory) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Inventory inv : inventory) {
            String catName = inv.getItem().getCategory() == null ? "未分类" : inv.getItem().getCategory().getName();
            byCategory.merge(catName, inv.getTotalAmount(), BigDecimal::add);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            result.add(Map.of("name", entry.getKey(), "value", entry.getValue()));
        }
        return result;
    }

    /** 近6个月月度利润趋势 */
    private List<Map<String, Object>> monthlyProfitData() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(5).withDayOfMonth(1);
        List<InventoryTransaction> txns = transactions.findByTransactionAtBetween(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<String, BigDecimal[]> monthly = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 6; i++) {
            String key = start.plusMonths(i).format(fmt);
            monthly.put(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (InventoryTransaction t : txns) {
            String key = t.getTransactionAt().format(fmt);
            BigDecimal[] values = monthly.get(key);
            if (values != null) {
                values[0] = values[0].add(t.getTotalCostAmount());
                values[1] = values[1].add(t.getSaleAmount());
                values[2] = values[2].add(t.getProfit());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : monthly.entrySet()) {
            result.add(Map.of("month", entry.getKey(), "cost", entry.getValue()[0], "sale", entry.getValue()[1], "profit", entry.getValue()[2]));
        }
        return result;
    }

    /** 近14天每日出入库金额 */
    private List<Map<String, Object>> dailyTrendData() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(13);
        List<InventoryTransaction> txns = transactions.findByTransactionAtBetween(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<LocalDate, Map<String, BigDecimal>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < 14; i++) {
            grouped.put(start.plusDays(i), new HashMap<>(Map.of("in", BigDecimal.ZERO, "out", BigDecimal.ZERO)));
        }
        for (InventoryTransaction t : txns) {
            LocalDate d = t.getTransactionAt().toLocalDate();
            if (grouped.containsKey(d)) {
                String type = t.getTransactionType();
                if ("in".equals(type)) {
                    grouped.get(d).merge("in", t.getTotalCostAmount(), BigDecimal::add);
                } else if ("out".equals(type)) {
                    grouped.get(d).merge("out", t.getSaleAmount(), BigDecimal::add);
                }
            }
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey().format(fmt));
            item.put("inbound", entry.getValue().get("in"));
            item.put("outbound", entry.getValue().get("out"));
            result.add(item);
        }
        return result;
    }

    /** 库存金额TOP N物品 */
    private List<Map<String, Object>> topItemsByValue(List<Inventory> inventory, int limit) {
        Map<Long, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (Inventory inv : inventory) {
            Long itemId = inv.getItem().getId();
            aggregated.computeIfAbsent(itemId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemCode", inv.getItem().getCode());
                m.put("itemName", inv.getItem().getName());
                m.put("unit", inv.getItem().getUnit());
                m.put("value", BigDecimal.ZERO);
                m.put("quantity", BigDecimal.ZERO);
                return m;
            });
            Map<String, Object> m = aggregated.get(itemId);
            m.put("value", ((BigDecimal) m.get("value")).add(inv.getTotalAmount()));
            m.put("quantity", ((BigDecimal) m.get("quantity")).add(inv.getQuantity()));
        }
        return aggregated.values().stream()
                .sorted((a, b) -> ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value")))
                .limit(limit)
                .map(m -> Map.of("itemCode", m.get("itemCode"), "itemName", m.get("itemName"),
                        "unit", m.get("unit"), "value", m.get("value"), "quantity", m.get("quantity")))
                .toList();
    }

    /** 增强的库存流水视图（含变动前库存） */
    private Map<String, Object> tx(InventoryTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("referenceNo", t.getReferenceNo());
        m.put("itemName", t.getItem().getName());
        m.put("itemCode", t.getItem().getCode());
        m.put("transactionType", t.getTransactionType());
        m.put("quantity", t.getQuantity());
        // 变动前库存 = 变动后库存 - 变动数量（入库为正，出库为负）
        BigDecimal beforeQty = t.getBalanceQuantity().subtract(t.getQuantity());
        m.put("beforeQuantity", beforeQty);
        m.put("balanceQuantity", t.getBalanceQuantity());
        m.put("saleAmount", t.getSaleAmount());
        m.put("totalCostAmount", t.getTotalCostAmount());
        m.put("profit", t.getProfit());
        m.put("transactionAt", t.getTransactionAt());
        return m;
    }
}