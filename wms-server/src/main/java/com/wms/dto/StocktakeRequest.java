package com.wms.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.List;
public record StocktakeRequest(@NotNull Long warehouseId,String remark, List<@Valid CountLineRequest> lines,
                               /** 仅盘点指定的物品（G11），为空则全仓盘点。 */
                               List<String> itemCodes,
                               /** 仅盘点指定的库位（G11），为空则全部库位。 */
                               List<String> locationCodes) {
 public StocktakeRequest(Long warehouseId,String remark,List<CountLineRequest> lines){this(warehouseId,remark,lines,null,null);}
 /** locationCode 允许空：盘点单创建时无库位行存的是空串，@NotBlank 会导致空库位行无法录入实盘（400）。 */
 public record CountLineRequest(@NotBlank String itemCode,String locationCode,String batchNo,@NotNull @DecimalMin(value="0") BigDecimal actualQuantity) {}
}
