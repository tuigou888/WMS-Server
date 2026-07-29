package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List;
public record StocktakeRequest(@NotNull Long warehouseId,String remark, List<@Valid CountLineRequest> lines) {
 public record CountLineRequest(@NotBlank String itemCode,@NotBlank String locationCode,String batchNo,@NotNull @DecimalMin(value="0") BigDecimal actualQuantity) {}
}
