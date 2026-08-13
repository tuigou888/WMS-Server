package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.UserAccount;
import jakarta.persistence.*;

/**
 * 商城购物车：登录用户（{@link UserAccount}）维度保存商品快照。
 * 用 user_account_id + product_id 联合唯一约束避免重复加入。
 */
@Entity
@Table(name = "market_cart",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "product_id"}))
public class MarketCart extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketProduct product;

    @Column(nullable = false) private Integer quantity = 1;

    /** 加入时的售价快照（仅用于购物车展示，下单以订单行快照为准） */
    @Column(nullable = false, precision = 18, scale = 4) private java.math.BigDecimal snapshotPrice = java.math.BigDecimal.ZERO;

    public MarketCart() {}
    public MarketCart(UserAccount user, MarketProduct product, Integer quantity, java.math.BigDecimal snapshotPrice) {
        this.user = user; this.product = product; this.quantity = quantity; this.snapshotPrice = snapshotPrice;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; } public void setUser(UserAccount user) { this.user = user; }
    public MarketProduct getProduct() { return product; } public void setProduct(MarketProduct product) { this.product = product; }
    public Integer getQuantity() { return quantity; } public void setQuantity(Integer quantity) { this.quantity = quantity == null || quantity < 1 ? 1 : quantity; }
    public java.math.BigDecimal getSnapshotPrice() { return snapshotPrice; } public void setSnapshotPrice(java.math.BigDecimal snapshotPrice) { this.snapshotPrice = snapshotPrice == null ? java.math.BigDecimal.ZERO : snapshotPrice; }
}
