package com.wms.model.entity;
import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="transfer_orders") public class TransferOrder extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String transferNo; @Column(nullable=false) private String status="DRAFT";
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Warehouse sourceWarehouse; @ManyToOne(optional=false,fetch=FetchType.LAZY) private Warehouse targetWarehouse; private String remark; private String reviewer;
 @OneToMany(mappedBy="transfer",cascade=CascadeType.ALL,orphanRemoval=true) private List<TransferLine> lines=new ArrayList<>();
 public Long getId(){return id;} public String getTransferNo(){return transferNo;} public void setTransferNo(String v){transferNo=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public Warehouse getSourceWarehouse(){return sourceWarehouse;} public void setSourceWarehouse(Warehouse v){sourceWarehouse=v;} public Warehouse getTargetWarehouse(){return targetWarehouse;} public void setTargetWarehouse(Warehouse v){targetWarehouse=v;}
 public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public String getReviewer(){return reviewer;} public void setReviewer(String v){reviewer=v;} public List<TransferLine> getLines(){return lines;}
 public void replaceLines(List<TransferLine> values){lines.clear();values.forEach(x->{x.setTransfer(this);lines.add(x);});}
}
