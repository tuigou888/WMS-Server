package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.InventoryTransaction;
import com.wms.model.entity.Item;
import com.wms.repository.InventoryRepository;
import com.wms.repository.InventoryTransactionRepository;
import com.wms.repository.ItemRepository;
import com.wms.service.WarehouseAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Set;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final InventoryRepository inventories;
    private final InventoryTransactionRepository transactions;
    private final ItemRepository items;
    private final WarehouseAccessService warehouseAccess;

    public ReportController(InventoryRepository i, InventoryTransactionRepository t, ItemRepository items, WarehouseAccessService warehouseAccess) {
        inventories = i;
        transactions = t;
        this.items = items;
        this.warehouseAccess = warehouseAccess;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('report:view')")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> dashboard() {
        List<ItemStock> stock = currentStockByItem();
        List<Map<String, Object>> alerts = smartAlerts(stock);
        BigDecimal qty = stock.stream().map(x -> x.quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = stock.stream().map(x -> x.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        List<InventoryTransaction> todayTxns = visibleTransactions(transactions.findDetailedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        BigDecimal inbound = todayTxns.stream()
                .filter(t -> t.getQuantity().signum()>0)
                .map(InventoryTransaction::getTotalCostAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outbound = todayTxns.stream()
                .filter(t -> t.getQuantity().signum()<0)
                .map(InventoryTransaction::getTotalCostAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sales = todayTxns.stream().filter(t -> "out".equals(t.getTransactionType())).map(InventoryTransaction::getSaleAmount).reduce(BigDecimal.ZERO,BigDecimal::add);

        List<Map<String, Object>> categoryDist = categoryDistribution(stock);
        List<Map<String, Object>> valueByCategory = valueByCategory(stock);
        List<Map<String, Object>> dailyTrend = dailyTrendData();
        List<Map<String, Object>> monthlyProfit = monthlyProfitData();
        List<Map<String, Object>> topItemsByValue = topItemsByValue(stock, 8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockItemCount", stock.stream().filter(x -> x.quantity.signum() > 0).count());
        result.put("totalQuantity", qty);
        result.put("totalAmount", amount);
        result.put("todayInboundAmount", inbound);
        result.put("todayOutboundAmount", outbound);
        result.put("todaySalesAmount", sales);
        result.put("alertCount", (long) alerts.size());
        result.put("alerts", alerts);
        result.put("recentTransactions", transactions.findRecentDetailedLimited(PageRequest.of(0, 8)).stream().map(this::tx).toList());
        result.put("categoryDistribution", categoryDist);
        result.put("valueByCategory", valueByCategory);
        result.put("dailyTrend", dailyTrend);
        result.put("monthlyProfit", monthlyProfit);
        result.put("topItemsByValue", topItemsByValue);
        return ApiResponse.ok(result);
    }

    @GetMapping("/stock-alert")
    @PreAuthorize("hasAuthority('report:view')")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> stockAlert() {
        return ApiResponse.ok(smartAlerts(currentStockByItem()));
    }

    @GetMapping("/profit")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResponse<Map<String, Object>> profit(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        boolean scoped=warehouseAccess.isWarehouseScoped();List<Long> ids=scoped?warehouseAccess.currentWarehouseIds():List.of(-1L);org.springframework.data.domain.Pageable pageable=PageRequest.of(Math.max(0,page-1),Math.min(100,Math.max(1,pageSize)));
        org.springframework.data.domain.Page<InventoryTransaction> rows=scoped?transactions.pageDetailedByTransactionTypeAndWarehouses("out",ids,pageable):transactions.pageDetailedByTransactionType("out",pageable);Object[] totals=transactions.salesTotals(scoped,ids);
        return ApiResponse.ok(Map.of("records",rows.getContent().stream().map(this::tx).toList(),"total",rows.getTotalElements(),"page",rows.getNumber()+1,"pageSize",rows.getSize(),"salesCount",totals[0],"totalSale",totals[1],"totalProfit",totals[2]));
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResponse<List<Map<String, Object>>> anomalies() {
        LocalDateTime since = LocalDate.now().minusDays(30).atStartOfDay();
        List<InventoryTransaction> recentTxns = visibleTransactions(transactions.findDetailedBetween(since, LocalDate.now().plusDays(1).atStartOfDay()));
        List<Map<String, Object>> result = new ArrayList<>();

        // 检测1：连续3天库存下降
        result.addAll(detectContinuousDecline(recentTxns));

        // 检测2：出库流水缺少库位（事务实体无批次号字段，库位缺失同样意味着库存无法追溯）
        result.addAll(detectMissingLocation(recentTxns));

        // 检测3：出库数量异常（单日出库 > 安全库存50%）
        result.addAll(detectAbnormalOutbound(recentTxns));

        return ApiResponse.ok(result);
    }

    /** 智能预警：结合安全库存、出库趋势、建议补货量 */
    private List<Map<String, Object>> smartAlerts(List<ItemStock> inventory) {
        // 近7天出库量统计
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        List<InventoryTransaction> weekTxns = visibleTransactions(transactions.findDetailedBetween(
                weekAgo.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        Map<Long, BigDecimal> weekOutbound = new HashMap<>();
        for (InventoryTransaction t : weekTxns) {
            if ("out".equals(t.getTransactionType()) || "transfer_out".equals(t.getTransactionType())) {
                weekOutbound.merge(t.getItem().getId(), t.getQuantity().abs(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ItemStock i : inventory) {
            if (!i.enabled || (warehouseAccess.isWarehouseScoped() && !i.hasInventory)) continue;
            BigDecimal current = i.quantity;
            if (current.compareTo(i.safetyStock) >= 0) continue;

            BigDecimal weekOut = weekOutbound.getOrDefault(i.id, BigDecimal.ZERO);
            BigDecimal dailyAvg = weekOut.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            BigDecimal suggestedOrder = i.safetyStock.subtract(current).add(dailyAvg.multiply(BigDecimal.valueOf(3)))
                    .max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);

            String priority;
            if (current.compareTo(i.safetyStock.multiply(new BigDecimal("0.5"))) < 0
                    || (dailyAvg.compareTo(BigDecimal.ZERO) > 0
                    && dailyAvg.compareTo(i.safetyStock.multiply(new BigDecimal("0.2"))) > 0)) {
                priority = "HIGH";
            } else if (current.compareTo(i.safetyStock) < 0) {
                priority = "MEDIUM";
            } else {
                priority = "LOW";
            }

            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("itemId", i.id);
            alert.put("itemCode", i.code);
            alert.put("itemName", i.name);
            alert.put("unit", i.unit);
            alert.put("safetyStock", i.safetyStock);
            alert.put("currentStock", current);
            alert.put("shortage", i.safetyStock.subtract(current));
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
    private List<Map<String, Object>> detectContinuousDecline(List<InventoryTransaction> recentTxns) {
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

    /** 检测出库流水缺少库位信息（无批次号字段，库位缺失使流水无法追溯） */
    private List<Map<String, Object>> detectMissingLocation(List<InventoryTransaction> recentTxns) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (InventoryTransaction t : recentTxns) {
            if ("out".equals(t.getTransactionType()) && (t.getLocation() == null || t.getLocation().getCode() == null)) {
                String key = t.getItem().getCode() + "@" + t.getReferenceNo();
                if (seen.add(key)) {
                    result.add(Map.of(
                            "type", "MISSING_LOCATION",
                            "severity", "LOW",
                            "itemCode", t.getItem().getCode(),
                            "itemName", t.getItem().getName(),
                            "referenceNo", t.getReferenceNo(),
                            "description", "出库流水缺少库位信息"
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
    private List<Map<String, Object>> categoryDistribution(List<ItemStock> inventory) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ItemStock item : inventory) {
            if (!item.hasInventory) continue;
            byCategory.merge(item.categoryName, item.quantity, BigDecimal::add);
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
    private List<Map<String, Object>> valueByCategory(List<ItemStock> inventory) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ItemStock item : inventory) {
            if (!item.hasInventory) continue;
            byCategory.merge(item.categoryName, item.amount, BigDecimal::add);
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
        List<InventoryTransaction> txns = visibleTransactions(transactions.findDetailedBetween(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay()));

        Map<String, BigDecimal[]> monthly = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 6; i++) {
            String key = start.plusMonths(i).format(fmt);
            monthly.put(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (InventoryTransaction t : txns) {
            // 利润只应来自销售类出库流水：采购入库(amount>0)/调拨/报损报溢均无利润，混入会污染趋势
            if (!"out".equals(t.getTransactionType())) continue;
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
        List<InventoryTransaction> txns = visibleTransactions(transactions.findDetailedBetween(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay()));

        Map<LocalDate, Map<String, BigDecimal>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < 14; i++) {
            grouped.put(start.plusDays(i), new HashMap<>(Map.of("in", BigDecimal.ZERO, "out", BigDecimal.ZERO)));
        }
        for (InventoryTransaction t : txns) {
            LocalDate d = t.getTransactionAt().toLocalDate();
            if (grouped.containsKey(d)) {
                String type = t.getTransactionType();
                if (t.getQuantity().signum()>0) {
                    grouped.get(d).merge("in", t.getTotalCostAmount(), BigDecimal::add);
                } else if (t.getQuantity().signum()<0) {
                    grouped.get(d).merge("out", t.getTotalCostAmount(), BigDecimal::add);
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
    private List<Map<String, Object>> topItemsByValue(List<ItemStock> inventory, int limit) {
        return inventory.stream().filter(x -> x.hasInventory)
                .sorted((a, b) -> b.amount.compareTo(a.amount))
                .limit(limit)
                .map(m -> Map.<String,Object>of("itemCode", m.code, "itemName", m.name,
                        "unit", m.unit, "value", m.amount, "quantity", m.quantity))
                .toList();
    }

    /** 库龄分析（G7）：按物品/仓库统计库存分布在 0-30/30-60/60-90/>90 天的批次，识别呆滞料。 */
    @GetMapping("/inventory-age")
    @PreAuthorize("hasAuthority('report:view')")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> inventoryAge() {
        LocalDate today = LocalDate.now();

        Map<String,List<AgeLayer>> remainingLayers;
        try (java.util.stream.Stream<InventoryTransaction> txns = transactions.streamDetailedOrdered()) {
            remainingLayers = remainingFifoLayers(txns.filter(t -> warehouseAccess.canAccess(t.getWarehouse().getId())));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        java.util.stream.Stream<Inventory> inventoryStream=warehouseAccess.isWarehouseScoped()?inventories.streamAllDetailedByWarehouseIds(warehouseAccess.currentWarehouseIds()):inventories.streamAllDetailed();
        try(inventoryStream){inventoryStream.forEach(inv->{if(inv.getQuantity()==null||inv.getQuantity().signum()<=0)return;List<AgeLayer> layers=remainingLayers.getOrDefault(stockKey(inv.getItem().getId(),inv.getWarehouse().getId(),inv.getLocation()==null?null:inv.getLocation().getId(),inv.getBatchNo()),List.of());long ageDays=layerAgeDays(inv,layers,today);String bucket=ageDays<30?"0-30":ageDays<60?"30-60":ageDays<90?"60-90":">90";Map<String,Object> m=new LinkedHashMap<>();m.put("itemCode",inv.getItem().getCode());m.put("itemName",inv.getItem().getName());m.put("unit",inv.getItem().getUnit());m.put("warehouseName",inv.getWarehouse().getName());m.put("locationCode",inv.getLocation()==null?null:inv.getLocation().getCode());m.put("batchNo",inv.getBatchNo());m.put("quantity",inv.getQuantity());m.put("amount",inv.getTotalAmount());m.put("earliestInDate",earliestLayerDate(inv,layers,today));m.put("ageDays",ageDays);m.put("bucket",bucket);result.add(m);});}
        result.sort((a, b) -> Long.compare((long) b.get("ageDays"), (long) a.get("ageDays")));
        return ApiResponse.ok(result);
    }

    /** 以带符号流水构建每个库存键真实剩余的 FIFO 入库层，出库会逐层消费而非只看当前库存。 */
    private Map<String,List<AgeLayer>> remainingFifoLayers(java.util.stream.Stream<InventoryTransaction> txns){Map<String,Deque<AgeLayer>> layers=new HashMap<>();txns.forEach(t->{String key=stockKey(t.getItem().getId(),t.getWarehouse().getId(),t.getLocation()==null?null:t.getLocation().getId(),t.getBatchNo());Deque<AgeLayer> queue=layers.computeIfAbsent(key,x->new ArrayDeque<>());if(t.getQuantity().signum()>0){queue.addLast(new AgeLayer(t.getTransactionAt().toLocalDate(),t.getQuantity()));return;}BigDecimal consume=t.getQuantity().abs();while(consume.signum()>0&&!queue.isEmpty()){AgeLayer first=queue.getFirst();BigDecimal used=first.quantity.min(consume);first.quantity=first.quantity.subtract(used);consume=consume.subtract(used);if(first.quantity.signum()==0)queue.removeFirst();}});Map<String,List<AgeLayer>> result=new HashMap<>();layers.forEach((k,v)->result.put(k,new ArrayList<>(v)));return result;}
    private long layerAgeDays(Inventory inv,List<AgeLayer> layers,LocalDate today){BigDecimal remaining=inv.getQuantity(),weighted=BigDecimal.ZERO;for(AgeLayer layer:layers){if(remaining.signum()<=0)break;BigDecimal q=layer.quantity.min(remaining);weighted=weighted.add(BigDecimal.valueOf(ChronoUnit.DAYS.between(layer.date,today)).multiply(q));remaining=remaining.subtract(q);}if(remaining.signum()>0){LocalDate fallback=inv.getUpdatedAt()==null?today:inv.getUpdatedAt().toLocalDate();weighted=weighted.add(BigDecimal.valueOf(ChronoUnit.DAYS.between(fallback,today)).multiply(remaining));}return weighted.divide(inv.getQuantity(),0,RoundingMode.HALF_UP).longValue();}
    private LocalDate earliestLayerDate(Inventory inv,List<AgeLayer> layers,LocalDate today){return layers.isEmpty()?(inv.getUpdatedAt()==null?today:inv.getUpdatedAt().toLocalDate()):layers.getFirst().date;}
    private static final class AgeLayer { private final LocalDate date; private BigDecimal quantity; private AgeLayer(LocalDate date,BigDecimal quantity){this.date=date;this.quantity=quantity;} }

    /**
     * 按 FIFO 估算当前库存的加权平均库龄：
     * 从最早入库层开始逐层消耗，剩下的库存一定来自较新的层；加权天数 = Σ(层内剩余量 × 该层天数) / 总库存。
     * 无法由入库层完全解释的多余量（如历史数据缺失）按 updatedAt 兜底计龄。
     */
    private long fifoAgeDays(Inventory inv, Map<String, List<InventoryTransaction>> inboundByStock, LocalDate today) {
        BigDecimal remaining = inv.getQuantity();
        BigDecimal weightedDays = BigDecimal.ZERO;
        LocalDate fallback = inv.getUpdatedAt() == null ? today : inv.getUpdatedAt().toLocalDate();
        for (InventoryTransaction t : inboundByStock.getOrDefault(stockKey(inv.getItem().getId(), inv.getWarehouse().getId(),
                inv.getLocation() == null ? null : inv.getLocation().getId()), List.of())) {
            if (remaining.signum() <= 0) break;
            BigDecimal q = t.getQuantity().min(remaining);
            long days = ChronoUnit.DAYS.between(t.getTransactionAt().toLocalDate(), today);
            weightedDays = weightedDays.add(BigDecimal.valueOf(days).multiply(q));
            remaining = remaining.subtract(q);
        }
        if (remaining.signum() > 0) {
            long days = ChronoUnit.DAYS.between(fallback, today);
            weightedDays = weightedDays.add(BigDecimal.valueOf(days).multiply(remaining));
        }
        return weightedDays.divide(inv.getQuantity(), 0, RoundingMode.HALF_UP).longValue();
    }

    /** 当前库存中最早入库层的日期（FIFO 剩余首层），无入库记录时用 updatedAt 兜底 */
    private LocalDate earliestLayerDate(Inventory inv, Map<String, List<InventoryTransaction>> inboundByStock, LocalDate today) {
        LocalDate fallback = inv.getUpdatedAt() == null ? today : inv.getUpdatedAt().toLocalDate();
        BigDecimal remaining = inv.getQuantity();
        for (InventoryTransaction t : inboundByStock.getOrDefault(stockKey(inv.getItem().getId(), inv.getWarehouse().getId(),
                inv.getLocation() == null ? null : inv.getLocation().getId()), List.of())) {
            if (remaining.signum() <= 0) break;
            remaining = remaining.subtract(t.getQuantity());
            if (remaining.signum() > 0) continue; // 该层已被完全消耗
            return t.getTransactionAt().toLocalDate(); // 库存落在这一层
        }
        return fallback;
    }

    /** 收发存汇总（G8）：按物品聚合期初/入库/出库/期末（数量+金额）；period 例如 2026-07。 */
    @GetMapping("/in-out-summary")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResponse<List<Map<String, Object>>> inOutSummary(@RequestParam(required = false) String period) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = period == null || period.isBlank()
                ? today.withDayOfMonth(1) : LocalDate.parse(period + "-01");
        LocalDate monthEnd = monthStart.plusMonths(1);
        boolean scoped=warehouseAccess.isWarehouseScoped();
        List<Long> warehouseIds=scoped?warehouseAccess.currentWarehouseIds():List.of(-1L);
        List<Object[]> aggregates=transactions.aggregateInOutSummary(monthStart.atStartOfDay(),monthEnd.atStartOfDay(),scoped,warehouseIds);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object[] row:aggregates){BigDecimal openingQty=decimal(row[4]),openingAmount=decimal(row[5]),inQty=decimal(row[6]),inAmount=decimal(row[7]),outQty=decimal(row[8]),outAmount=decimal(row[9]);Map<String,Object> view=new LinkedHashMap<>();view.put("itemCode",row[1]);view.put("itemName",row[2]);view.put("unit",row[3]);view.put("openingQuantity",openingQty);view.put("inQuantity",inQty);view.put("inAmount",inAmount);view.put("outQuantity",outQty);view.put("outAmount",outAmount);view.put("endingQuantity",openingQty.add(inQty).subtract(outQty));view.put("endingAmount",openingAmount.add(inAmount).subtract(outAmount));result.add(view);}
        return ApiResponse.ok(result);
    }

    private String stockKey(Long itemId, Long warehouseId, Long locationId) {
        return itemId + "@" + warehouseId + "@" + (locationId == null ? "-" : locationId);
    }
    private List<ItemStock> currentStockByItem(){Map<Long,ItemStock> grouped=new LinkedHashMap<>();for(Item item:items.findAll())grouped.put(item.getId(),new ItemStock(item));boolean scoped=warehouseAccess.isWarehouseScoped();java.util.stream.Stream<Inventory> stream=scoped?inventories.streamAllDetailedByWarehouseIds(warehouseAccess.currentWarehouseIds()):inventories.streamAllDetailed();try(stream){stream.forEach(inv->{ItemStock value=grouped.get(inv.getItem().getId());if(value!=null)value.add(inv);});}return new ArrayList<>(grouped.values());}
    private List<Inventory> visibleInventory(List<Inventory> rows) { return rows.stream().filter(i -> warehouseAccess.canAccess(i.getWarehouse().getId())).toList(); }
    private List<InventoryTransaction> visibleTransactions(List<InventoryTransaction> rows) { return rows.stream().filter(t -> warehouseAccess.canAccess(t.getWarehouse().getId())).toList(); }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal amount ? amount : value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private static final class ItemStock{private final Long id;private final String code,name,unit,categoryName;private final BigDecimal safetyStock;private final boolean enabled;private BigDecimal quantity=BigDecimal.ZERO,amount=BigDecimal.ZERO;private boolean hasInventory;private ItemStock(Item i){id=i.getId();code=i.getCode();name=i.getName();unit=i.getUnit();categoryName=i.getCategory()==null?"未分类":i.getCategory().getName();safetyStock=i.getSafetyStock();enabled=Boolean.TRUE.equals(i.getStatus());}private void add(Inventory i){quantity=quantity.add(i.getQuantity());amount=amount.add(i.getTotalAmount());hasInventory=true;}}
    private String stockKey(Long itemId,Long warehouseId,Long locationId,String batchNo){return stockKey(itemId,warehouseId,locationId)+"@"+(batchNo==null?"-":batchNo);}

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
