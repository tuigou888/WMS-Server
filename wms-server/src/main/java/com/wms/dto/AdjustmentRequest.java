package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List;
public record AdjustmentRequest(@NotBlank @Pattern(regexp="LOSS|GAIN",message="动作必须为 LOSS 或 GAIN") String action, @NotNull Long warehouseId, String reason, String remark,
                                @NotEmpty(message="至少需要一条明细") List<@Valid AdjustmentLineRequest> lines) {
 public record AdjustmentLineRequest(@NotBlank String itemCode,@NotBlank String locationCode,String batchNo,@NotNull @DecimalMin(value="0.0001") BigDecimal quantity) {}
}