package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.UserAccount;
import jakarta.persistence.*;

/**
 * 商城商品收藏表：用户对 {@link MarketProduct} 的收藏关系，
 * (user, product) 唯一约束防止重复收藏。
 */
@Entity
@Table(name = "market_favorite",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id", "product_id"}))
public class MarketFavorite extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketProduct product;

    public MarketFavorite() {}
    public MarketFavorite(UserAccount user, MarketProduct product) { this.user = user; this.product = product; }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; } public void setUser(UserAccount user) { this.user = user; }
    public MarketProduct getProduct() { return product; } public void setProduct(MarketProduct product) { this.product = product; }
}
