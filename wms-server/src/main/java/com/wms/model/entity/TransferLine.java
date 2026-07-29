package com.wms.model.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="transfer_lines") public class TransferLine extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) private TransferOrder transfer; @ManyToOne(optional=false,fetch=FetchType.LAZY) private Item item;
 @Column(nullable=false) private String sourceLocationCode; @Column(nullable=false) private String targetLocationCode; @Column(length=60) private String batchNo; @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity;
 public Long getId(){return id;} public TransferOrder getTransfer(){return transfer;} public void setTransfer(TransferOrder v){transfer=v;} public Item getItem(){return item;} public void setItem(Item v){item=v;}
 public String getSourceLocationCode(){return sourceLocationCode;} public void setSourceLocationCode(String v){sourceLocationCode=v;} public String getTargetLocationCode(){return targetLocationCode;} public void setTargetLocationCode(String v){targetLocationCode=v;} public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;}
}
