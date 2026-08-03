package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.InventoryTransaction;
import com.wms.repository.InventoryRepository;
import com.wms.repository.InventoryTransactionRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.WarehouseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryRepository inventories;
    private final InventoryTransactionRepository transactions;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;

    public InventoryController(InventoryRepository i, InventoryTransactionRepository t,
                               WarehouseRepository w, LocationRepository l) {
        inventories = i;
        transactions = t;
        warehouses = w;
        locations = l;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int pageSize) {
        List<Inventory> all = inventories.findAllDetailed();
        int max = Math.min(Math.max(pageSize, 1), 1000);
        int start = page > 0 ? Math.min((page - 1) * max, all.size()) : 0;
        int end = Math.min(start + max, all.size());
        return ApiResponse.ok(all.subList(start, end).stream().map(this::inventoryView).toList());
    }

    @GetMapping("/transactions")
    public ApiResponse<List<Map<String, Object>>> transactionList(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(transactions.findRecentDetailed().stream()
                .limit(Math.min(Math.max(limit, 0), 500))
                .map(this::transactionView)
                .toList());
    }

    @GetMapping("/warehouses")
    public ApiResponse<List<Map<String, Object>>> warehouseList() {
        return ApiResponse.ok(warehouses.findByStatusTrueOrderByNameAsc().stream()
                .map(w -> Map.<String, Object>of("id", w.getId(), "code", w.getCode(), "name", w.getName()))
                .toList());
    }

    @GetMapping("/{itemId}")
    public ApiResponse<List<Map<String, Object>>> byItem(@PathVariable Long itemId) {
        return ApiResponse.ok(inventories.findByItemId(itemId).stream()
                .map(this::inventoryView)
                .toList());
    }

    private Map<String, Object> inventoryView(Inventory i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("itemId", i.getItem().getId());
        m.put("itemCode", i.getItem().getCode());
        m.put("itemName", i.getItem().getName());
        m.put("unit", i.getItem().getUnit());
        m.put("warehouseId", i.getWarehouse().getId());
        m.put("warehouseName", i.getWarehouse().getName());
        m.put("locationCode", i.getLocation() == null ? null : i.getLocation().getCode());
        m.put("batchNo", i.getBatchNo());
        m.put("quantity", i.getQuantity());
        m.put("totalAmount", i.getTotalAmount());
        m.put("avgCost", i.getAvgCost());
        m.put("lastInCost", i.getLastInCost());
        m.put("updatedAt", i.getUpdatedAt());
        return m;
    }

    private Map<String, Object> transactionView(InventoryTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("referenceNo", t.getReferenceNo());
        m.put("transactionType", t.getTransactionType());
        m.put("itemCode", t.getItem().getCode());
        m.put("itemName", t.getItem().getName());
        m.put("warehouseName", t.getWarehouse().getName());
        m.put("locationCode", t.getLocation() == null ? null : t.getLocation().getCode());
        m.put("quantity", t.getQuantity());
        m.put("unitCost", t.getUnitCost());
        m.put("totalCostAmount", t.getTotalCostAmount());
        m.put("salePrice", t.getSalePrice());
        m.put("saleAmount", t.getSaleAmount());
        m.put("profit", t.getProfit());
        m.put("balanceQuantity", t.getBalanceQuantity());
        m.put("balanceAmount", t.getBalanceAmount());
        m.put("avgCostAfter", t.getAvgCostAfter());
        m.put("remark", t.getRemark());
        m.put("transactionAt", t.getTransactionAt());
        return m;
    }
}
