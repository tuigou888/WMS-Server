package com.wms.model.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="stock_document_lines") public class StockDocumentLine extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false,fetch=FetchType.LAZY) private StockDocument document; @ManyToOne(optional=false,fetch=FetchType.LAZY) private Item item;
 @Column(nullable=false) private String locationCode; @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity; @Column(nullable=false,precision=18,scale=4) private BigDecimal unitPrice;
 private String batchNo; private String remark;
 public Long getId(){return id;} public StockDocument getDocument(){return document;} public void setDocument(StockDocument v){document=v;} public Item getItem(){return item;} public void setItem(Item v){item=v;}
 public String getLocationCode(){return locationCode;} public void setLocationCode(String v){locationCode=v;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;} public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
 public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
