package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List;
public record TransferRequest(@NotNull Long sourceWarehouseId,@NotNull Long targetWarehouseId,String remark,@NotEmpty List<@Valid TransferLineRequest> lines) {
 public record TransferLineRequest(@NotBlank String itemCode,@NotBlank String sourceLocationCode,@NotBlank String targetLocationCode,String batchNo,@NotNull @DecimalMin(value="0.0001") BigDecimal quantity) {}
}
