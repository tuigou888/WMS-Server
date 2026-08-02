package com.wms.model.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="adjustment_lines") public class AdjustmentLine extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private AdjustmentOrder adjustment;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Item item;
 @Column(nullable=false) private String locationCode;
 @Column(length=60) private String batchNo;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity;
 public Long getId(){return id;} public AdjustmentOrder getAdjustment(){return adjustment;} public void setAdjustment(AdjustmentOrder v){adjustment=v;}
 public Item getItem(){return item;} public void setItem(Item v){item=v;}
 public String getLocationCode(){return locationCode;} public void setLocationCode(String v){locationCode=v;}
 public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;}
 public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;}
}