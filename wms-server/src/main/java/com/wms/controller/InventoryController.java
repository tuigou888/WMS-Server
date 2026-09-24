package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.InventoryTransaction;
import com.wms.repository.InventoryRepository;
import com.wms.repository.InventoryTransactionRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.WarehouseRepository;
import com.wms.service.WarehouseAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final WarehouseAccessService warehouseAccess;

    public InventoryController(InventoryRepository i, InventoryTransactionRepository t,
                               WarehouseRepository w, LocationRepository l, WarehouseAccessService warehouseAccess) {
        inventories = i;
        transactions = t;
        warehouses = w;
        locations = l;
        this.warehouseAccess = warehouseAccess;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(pageSize, 1), 200));
        org.springframework.data.domain.Page<Inventory> result = warehouseAccess.isWarehouseScoped()
                ? inventories.findAllDetailedByWarehouseIds(warehouseAccess.currentWarehouseIds(), pageable)
                : inventories.findAllDetailed(pageable);
        return ApiResponse.ok(Map.of("records", result.getContent().stream().map(this::inventoryView).toList(),
                "total", result.getTotalElements(), "page", result.getNumber() + 1, "pageSize", result.getSize()));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<List<Map<String, Object>>> transactionList(
            @RequestParam(defaultValue = "100") int limit) {
        int size = Math.min(Math.max(limit, 0), 500);
        if (size == 0) return ApiResponse.ok(List.of());
        return ApiResponse.ok(transactions.findRecentDetailedLimited(PageRequest.of(0, size)).stream()
                .filter(t -> warehouseAccess.canAccess(t.getWarehouse().getId()))
                .map(this::transactionView)
                .toList());
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<List<Map<String, Object>>> warehouseList() {
        return ApiResponse.ok(warehouses.findByStatusTrueOrderByNameAsc().stream().filter(w -> warehouseAccess.canAccess(w.getId()))
                .map(w -> Map.<String, Object>of("id", w.getId(), "code", w.getCode(), "name", w.getName()))
                .toList());
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<List<Map<String, Object>>> byItem(@PathVariable Long itemId) {
        return ApiResponse.ok(inventories.findByItemId(itemId).stream().filter(i -> warehouseAccess.canAccess(i.getWarehouse().getId()))
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
