package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.Category;
import com.wms.model.entity.Item;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 商城商品上架表：与 WMS 主数据 {@link Item} 一对一关联（同款物品可上架一次），
 * 价格、展示状态等零售属性放在本表，避免污染 WMS 物品主档。
 */
@Entity
@Table(name = "market_product",
        uniqueConstraints = @UniqueConstraint(columnNames = "item_id"))
public class MarketProduct extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, length = 200) private String title;
    @Column(length = 500) private String subTitle;
    @Column(length = 500) private String mainImage;
    @Column(length = 2000) private String gallery; // 逗号分隔的图片 URL，预留 JSON 切分

    /** 上架状态：SHELF_ON=上架可售，SHELF_OFF=已下架，SOLD_OUT=售罄（库存<=0） */
    @Column(nullable = false, length = 20) private String status = "SHELF_OFF";

    @Column(nullable = false, precision = 18, scale = 4) private BigDecimal salePrice = BigDecimal.ZERO;
    @Column(precision = 18, scale = 4) private BigDecimal marketPrice;
    @Column(nullable = false) private Integer sortNo = 0;
    @Column(nullable = false) private Long salesCount = 0L;
    @Column(nullable = false) private Long viewCount = 0L;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    public MarketProduct() {}
    public MarketProduct(Item item, String title, BigDecimal salePrice) { this.item = item; this.title = title; this.salePrice = salePrice; }
    public MarketProduct(Item item, String title, BigDecimal salePrice, BigDecimal marketPrice, Category category) {
        this.item = item; this.title = title; this.salePrice = salePrice;
        this.marketPrice = marketPrice; this.category = category;
    }

    public Long getId() { return id; }
    public Item getItem() { return item; } public void setItem(Item item) { this.item = item; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getSubTitle() { return subTitle; } public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
    public String getMainImage() { return mainImage; } public void setMainImage(String mainImage) { this.mainImage = mainImage; }
    public String getGallery() { return gallery; } public void setGallery(String gallery) { this.gallery = gallery; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public BigDecimal getSalePrice() { return salePrice; } public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice == null ? BigDecimal.ZERO : salePrice; }
    public BigDecimal getMarketPrice() { return marketPrice; } public void setMarketPrice(BigDecimal marketPrice) { this.marketPrice = marketPrice; }
    public Integer getSortNo() { return sortNo; } public void setSortNo(Integer sortNo) { this.sortNo = sortNo == null ? 0 : sortNo; }
    public Long getSalesCount() { return salesCount; } public void setSalesCount(Long salesCount) { this.salesCount = salesCount == null ? 0L : salesCount; }
    public Long getViewCount() { return viewCount; } public void setViewCount(Long viewCount) { this.viewCount = viewCount == null ? 0L : viewCount; }
    public Category getCategory() { return category; } public void setCategory(Category category) { this.category = category; }
}
