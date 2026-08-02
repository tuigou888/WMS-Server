package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List;
public record StocktakeRequest(@NotNull Long warehouseId,String remark, List<@Valid CountLineRequest> lines,
                               /** 仅盘点指定的物品（G11），为空则全仓盘点。 */
                               List<String> itemCodes,
                               /** 仅盘点指定的库位（G11），为空则全部库位。 */
                               List<String> locationCodes) {
 public StocktakeRequest(Long warehouseId,String remark,List<CountLineRequest> lines){this(warehouseId,remark,lines,null,null);}
 public record CountLineRequest(@NotBlank String itemCode,@NotBlank String locationCode,String batchNo,@NotNull @DecimalMin(value="0") BigDecimal actualQuantity) {}
}
