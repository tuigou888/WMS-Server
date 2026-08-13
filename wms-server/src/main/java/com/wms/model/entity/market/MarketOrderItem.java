package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.Item;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 商城订单行：保存下单时的商品/物品快照（itemCode、itemName、unit、salePrice、subtotal），
 * 商品后续调价/下架不影响历史订单。
 * 同时关联 Item（库存履约依据）和 MarketProduct（保留商品上架关联，便于统计）。
 */
@Entity
@Table(name = "market_order_item")
public class MarketOrderItem extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private MarketOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false) private Long productId;
    @Column(nullable = false, length = 200) private String itemName;
    @Column(nullable = false, length = 200) private String itemCode;
    @Column(length = 30) private String unit;
    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal salePrice = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal quantity = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;

    public MarketOrderItem() {}

    public Long getId() { return id; }
    public MarketOrder getOrder() { return order; } public void setOrder(MarketOrder order) { this.order = order; }
    public Item getItem() { return item; } public void setItem(Item item) { this.item = item; }
    public Long getProductId() { return productId; } public void setProductId(Long productId) { this.productId = productId; }
    public String getItemName() { return itemName; } public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemCode() { return itemCode; } public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getUnit() { return unit; } public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getSalePrice() { return salePrice; } public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice == null ? BigDecimal.ZERO : salePrice; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal quantity) { this.quantity = quantity == null ? BigDecimal.ZERO : quantity; }
    public BigDecimal getSubtotal() { return subtotal; } public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal; }
}
