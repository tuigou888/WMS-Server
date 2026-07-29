package com.wms.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record ItemRequest(@NotBlank(message="不能为空") @Size(max=50) String code, @NotBlank(message="不能为空") @Size(max=200) String name, Long categoryId, String unit, String specs, String brand, String model, String barcode, @DecimalMin(value="0",message="不能小于0") BigDecimal safetyStock, @DecimalMin(value="0",message="不能小于0") BigDecimal maxStock, @DecimalMin(value="0",message="不能小于0") BigDecimal minStock, Boolean status, String remark, Long defaultWarehouseId) {}
