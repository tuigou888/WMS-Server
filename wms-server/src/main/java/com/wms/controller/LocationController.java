package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.BusinessException;
import com.wms.model.entity.Location;
import com.wms.repository.LocationRepository;
import com.wms.repository.WarehouseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库位管理。库位在入库时自动创建，也可通过此接口查询。
 */
@RestController
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locations;
    private final WarehouseRepository warehouses;

    public LocationController(LocationRepository locations, WarehouseRepository warehouses) {
        this.locations = locations;
        this.warehouses = warehouses;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long warehouseId) {
        List<Location> values;
        if (warehouseId != null) {
            if (!warehouses.existsById(warehouseId)) {
                throw new BusinessException("仓库不存在");
            }
            values = locations.findByWarehouseId(warehouseId);
        } else {
            values = locations.findAllWithWarehouse();
        }
        return ApiResponse.ok(values.stream().map(LocationController::view).toList());
    }

    public static Map<String, Object> view(Location location) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", location.getId());
        result.put("warehouseId", location.getWarehouse().getId());
        result.put("warehouseName", location.getWarehouse().getName());
        result.put("code", location.getCode());
        result.put("status", location.getStatus());
        return result;
    }
}