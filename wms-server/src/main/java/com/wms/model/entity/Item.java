package com.wms.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "items")
public class Item extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 200) private String name;
    @ManyToOne(fetch = FetchType.EAGER) private Category category;
    private String unit = "个"; private String specs; private String brand; private String model; private String barcode;
    @Column(precision = 18, scale = 4, nullable = false) private BigDecimal safetyStock = BigDecimal.ZERO;
    @Column(precision = 18, scale = 4, nullable = false) private BigDecimal maxStock = BigDecimal.ZERO;
    @Column(precision = 18, scale = 4, nullable = false) private BigDecimal minStock = BigDecimal.ZERO;
    @Column(nullable = false) private Boolean status = true;
    @Column(length = 1000) private String remark;
    @ManyToOne(fetch = FetchType.EAGER) private Warehouse defaultWarehouse;
    public Long getId() { return id; } public String getCode() { return code; } public void setCode(String code) { this.code = code; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; } public void setCategory(Category category) { this.category = category; }
    public Warehouse getDefaultWarehouse() { return defaultWarehouse; } public void setDefaultWarehouse(Warehouse w) { this.defaultWarehouse = w; }
    public String getUnit() { return unit; } public void setUnit(String unit) { this.unit = unit; }
    public String getSpecs() { return specs; } public void setSpecs(String specs) { this.specs = specs; }
    public String getBrand() { return brand; } public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; } public void setModel(String model) { this.model = model; }
    public String getBarcode() { return barcode; } public void setBarcode(String barcode) { this.barcode = barcode; }
    public BigDecimal getSafetyStock() { return safetyStock; } public void setSafetyStock(BigDecimal safetyStock) { this.safetyStock = safetyStock == null ? BigDecimal.ZERO : safetyStock; }
    public BigDecimal getMaxStock() { return maxStock; } public void setMaxStock(BigDecimal maxStock) { this.maxStock = maxStock == null ? BigDecimal.ZERO : maxStock; }
    public BigDecimal getMinStock() { return minStock; } public void setMinStock(BigDecimal minStock) { this.minStock = minStock == null ? BigDecimal.ZERO : minStock; }
    public Boolean getStatus() { return status; } public void setStatus(Boolean status) { this.status = status; }
    public String getRemark() { return remark; } public void setRemark(String remark) { this.remark = remark; }
}
