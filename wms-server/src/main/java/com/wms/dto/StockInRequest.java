package com.wms.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record StockInRequest(@NotBlank(message="物品编码不能为空") String itemCode, @NotNull(message="入库数量不能为空") @DecimalMin(value="0.0001",message="入库数量必须大于0") BigDecimal quantity, @NotNull(message="入库单价不能为空") @DecimalMin(value="0",message="入库单价不能小于0") BigDecimal unitCost, @NotNull(message="仓库不能为空") Long warehouseId, @NotBlank(message="库位不能为空") String locationCode, String batchNo, String remark) {}
