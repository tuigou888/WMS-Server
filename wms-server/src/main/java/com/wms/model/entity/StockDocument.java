package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name="stock_documents")
public class StockDocument extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=60) private String documentNo;
 @Column(nullable=false,length=10) private String type; // IN, OUT
 @Column(nullable=false,length=20) private String status="DRAFT"; // DRAFT, APPROVED, REJECTED, COMPLETED, CANCELLED
 @ManyToOne(fetch=FetchType.LAZY) private BusinessPartner partner;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Warehouse warehouse;
 private LocalDate businessDate=LocalDate.now(); private String remark; private String reviewer; private String reviewRemark;
 @OneToMany(mappedBy="document",cascade=CascadeType.ALL,orphanRemoval=true) private List<StockDocumentLine> lines=new ArrayList<>();
 public Long getId(){return id;} public String getDocumentNo(){return documentNo;} public void setDocumentNo(String v){documentNo=v;} public String getType(){return type;} public void setType(String v){type=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;} public BusinessPartner getPartner(){return partner;} public void setPartner(BusinessPartner v){partner=v;}
 public Warehouse getWarehouse(){return warehouse;} public void setWarehouse(Warehouse v){warehouse=v;} public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
 public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public String getReviewer(){return reviewer;} public void setReviewer(String v){reviewer=v;} public String getReviewRemark(){return reviewRemark;} public void setReviewRemark(String v){reviewRemark=v;}
 public List<StockDocumentLine> getLines(){return lines;} public void replaceLines(List<StockDocumentLine> values){lines.clear(); values.forEach(x->{x.setDocument(this);lines.add(x);});}
}
