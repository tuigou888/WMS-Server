package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
public record DocumentRequest(@NotBlank @Pattern(regexp="IN|OUT|RETURN_IN|RETURN_OUT",message="类型必须为 IN / OUT / RETURN_IN / RETURN_OUT") String type, Long partnerId, @NotNull(message="仓库不能为空") Long warehouseId, LocalDate businessDate, String remark, @NotEmpty(message="至少需要一条明细") List<@Valid DocumentLineRequest> lines) {
 public record DocumentLineRequest(@NotBlank(message="物品编码不能为空") String itemCode,@NotBlank(message="库位不能为空") String locationCode,@NotNull @DecimalMin(value="0.0001",message="数量必须大于0") BigDecimal quantity,@NotNull @DecimalMin(value="0",message="单价不能小于0") BigDecimal unitPrice,String batchNo,String remark) {}
}
