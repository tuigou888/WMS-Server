package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.BusinessException;
import com.wms.dto.WarehouseRequest;
import com.wms.model.entity.Warehouse;
import com.wms.repository.WarehouseRepository;
import com.wms.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仓库基础资料。禁用仓库不会出现在新建业务单据的仓库选择中。 */
@RestController
@RequestMapping("/warehouses")
public class WarehouseController {
    private final WarehouseRepository warehouses;

    public WarehouseController(WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        List<Warehouse> values = includeDisabled ? warehouses.findAll() : warehouses.findByStatusTrueOrderByNameAsc();
        return ApiResponse.ok(values.stream().map(WarehouseController::view).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody WarehouseRequest request) {
        ensureAdmin();
        String code = request.code().trim();
        if (warehouses.existsByCode(code)) {
            throw new BusinessException("仓库编码已存在");
        }
        Warehouse warehouse = new Warehouse();
        merge(warehouse, request);
        return ApiResponse.ok("仓库创建成功", view(warehouses.save(warehouse)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        ensureAdmin();
        Warehouse warehouse = warehouses.findById(id).orElseThrow(() -> new BusinessException("仓库不存在"));
        String code = request.code().trim();
        if (!warehouse.getCode().equals(code) && warehouses.existsByCode(code)) {
            throw new BusinessException("仓库编码已存在");
        }
        merge(warehouse, request);
        return ApiResponse.ok("仓库更新成功", view(warehouses.save(warehouse)));
    }

    private void merge(Warehouse warehouse, WarehouseRequest request) {
        warehouse.setCode(request.code().trim());
        warehouse.setName(request.name().trim());
        warehouse.setStatus(request.status() == null || request.status());
    }

    private void ensureAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof TokenService.Principal user) || !"ADMIN".equals(user.role())) {
            throw new AccessDeniedException("仅管理员可维护仓库");
        }
    }

    public static Map<String, Object> view(Warehouse warehouse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", warehouse.getId());
        result.put("code", warehouse.getCode());
        result.put("name", warehouse.getName());
        result.put("status", Boolean.TRUE.equals(warehouse.getStatus()));
        return result;
    }
}
