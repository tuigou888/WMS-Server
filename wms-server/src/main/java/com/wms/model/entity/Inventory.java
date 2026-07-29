package com.wms.model.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="inventory", uniqueConstraints=@UniqueConstraint(columnNames={"item_id","warehouse_id","location_id","batch_no"}))
public class Inventory extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="item_id") private Item item;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="warehouse_id") private Warehouse warehouse;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="location_id") private Location location;
 @Column(name="batch_no", length=60) private String batchNo;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity=BigDecimal.ZERO;
 @Column(nullable=false,precision=18,scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal avgCost=BigDecimal.ZERO;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal lastInCost=BigDecimal.ZERO;
 public Inventory(){} public Inventory(Item i,Warehouse w,Location l,String b){item=i;warehouse=w;location=l;batchNo=b;}
 public Long getId(){return id;} public Item getItem(){return item;} public Warehouse getWarehouse(){return warehouse;} public Location getLocation(){return location;} public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;}
 public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;} public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;} public BigDecimal getAvgCost(){return avgCost;} public void setAvgCost(BigDecimal v){avgCost=v;} public BigDecimal getLastInCost(){return lastInCost;} public void setLastInCost(BigDecimal v){lastInCost=v;}
}
