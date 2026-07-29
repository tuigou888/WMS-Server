package com.wms.model.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="stocktake_lines") public class StocktakeLine extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) private StocktakeOrder stocktake; @ManyToOne(optional=false,fetch=FetchType.LAZY) private Item item;
 @Column(nullable=false) private String locationCode; @Column(length=60) private String batchNo; @Column(nullable=false,precision=18,scale=4) private BigDecimal bookQuantity; @Column(precision=18,scale=4) private BigDecimal actualQuantity; @Column(precision=18,scale=4) private BigDecimal differenceQuantity;
 public Long getId(){return id;} public StocktakeOrder getStocktake(){return stocktake;} public void setStocktake(StocktakeOrder v){stocktake=v;} public Item getItem(){return item;} public void setItem(Item v){item=v;}
 public String getLocationCode(){return locationCode;} public void setLocationCode(String v){locationCode=v;} public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public BigDecimal getBookQuantity(){return bookQuantity;} public void setBookQuantity(BigDecimal v){bookQuantity=v;} public BigDecimal getActualQuantity(){return actualQuantity;} public void setActualQuantity(BigDecimal v){actualQuantity=v;} public BigDecimal getDifferenceQuantity(){return differenceQuantity;} public void setDifferenceQuantity(BigDecimal v){differenceQuantity=v;}
}
