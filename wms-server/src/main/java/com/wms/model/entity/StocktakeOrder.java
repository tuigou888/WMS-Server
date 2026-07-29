package com.wms.model.entity;
import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="stocktake_orders") public class StocktakeOrder extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String stocktakeNo; @Column(nullable=false) private String status="DRAFT";
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Warehouse warehouse; private String remark; private String reviewer;
 @OneToMany(mappedBy="stocktake",cascade=CascadeType.ALL,orphanRemoval=true) private List<StocktakeLine> lines=new ArrayList<>();
 public Long getId(){return id;} public String getStocktakeNo(){return stocktakeNo;} public void setStocktakeNo(String v){stocktakeNo=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public Warehouse getWarehouse(){return warehouse;} public void setWarehouse(Warehouse v){warehouse=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public String getReviewer(){return reviewer;} public void setReviewer(String v){reviewer=v;}
 public List<StocktakeLine> getLines(){return lines;} public void replaceLines(List<StocktakeLine> values){lines.clear();values.forEach(x->{x.setStocktake(this);lines.add(x);});}
}
