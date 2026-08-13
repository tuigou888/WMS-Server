package com.wms.dto.market;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商城模块 DTO 容器（嵌套 record 形式，集中定义方便引用）。
 * 引用方式：{@code MarketDtos.MarketProductRequest} 或
 * {@code import com.wms.dto.market.MarketDtos.MarketProductRequest;}。
 */
public final class MarketDtos {
    private MarketDtos() {}

    /** 创建/更新商城商品（关联 WMS 物品 + 零售属性）。 */
    public record MarketProductRequest(
            @NotNull(message = "必须关联物品") Long itemId,
            @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
            @Size(max = 500) String subTitle,
            @Size(max = 500) String mainImage,
            @Size(max = 2000) String gallery,
            @DecimalMin(value = "0", message = "售价不能小于0") BigDecimal salePrice,
            BigDecimal marketPrice,
            Long categoryId,
            Integer sortNo
    ) {}

    /** 上/下架操作。 */
    public record MarketShelfRequest(@NotBlank(message = "状态不能为空") String status) {}

    /** 加入购物车。 */
    public record MarketCartAddRequest(@NotNull(message = "商品不能为空") Long productId, Integer quantity) {}

    /** 修改购物车数量。 */
    public record MarketCartUpdateRequest(@NotNull(message = "数量不能为空") Integer quantity) {}

    /** 收货人档案。 */
    public record MarketCustomerRequest(
            @NotBlank(message = "姓名不能为空") @Size(max = 60) String name,
            @NotBlank(message = "电话不能为空") @Size(max = 20) String phone,
            @NotBlank(message = "地址不能为空") @Size(max = 200) String address,
            Boolean defaultFlag,
            @Size(max = 200) String remark
    ) {}

    /** 提交订单：选择收货人 + 支付方式，商品取自购物车（快照）。 */
    public record MarketOrderCreateRequest(
            @NotNull(message = "收货人不能为空") Long customerId,
            @NotNull(message = "发货仓库不能为空") Long warehouseId,
            @NotBlank(message = "支付方式不能为空") String payType,
            String remark
    ) {}

    /** 管理员审核订单。 */
    public record MarketOrderAuditRequest(boolean approve, String remark) {}

    /** 管理员发货。 */
    public record MarketOrderShipRequest(String logisticsCompany, String logisticsNumber) {}
}
