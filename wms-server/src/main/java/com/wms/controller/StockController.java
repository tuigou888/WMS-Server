package com.wms.controller;
import com.wms.common.ApiResponse;
import com.wms.dto.StockInRequest;
import com.wms.dto.StockOutRequest;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final InventoryService service;

    public StockController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/in/scan")
    @PreAuthorize("hasAuthority('inventory:write') and hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> stockIn(@Valid @RequestBody StockInRequest r) {
        return ApiResponse.ok("入库成功", service.stockIn(r));
    }

    @PostMapping("/out/scan")
    @PreAuthorize("hasAuthority('inventory:write') and hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> stockOut(@Valid @RequestBody StockOutRequest r) {
        return ApiResponse.ok("出库成功", service.stockOut(r));
    }
}
