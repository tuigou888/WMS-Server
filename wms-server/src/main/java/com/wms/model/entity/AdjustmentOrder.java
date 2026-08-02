package com.wms.model.entity;

import jakarta.persistence.*;
import java.util.*;

/**
 * 报损 / 报溢单（AdjustmentOrder）。
 * 用于货物损坏、过期、丢失、盘盈等非采购/销售性质的库存增减。
 */
@Entity
@Table(name = "adjustment_orders")
public class AdjustmentOrder extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=60) private String adjustmentNo;
    @Column(nullable=false, length=20) private String status = "DRAFT"; // DRAFT, APPROVED, REJECTED, COMPLETED
    @Column(nullable=false, length=20) private String action;            // LOSS(报损) / GAIN(报溢)
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Warehouse warehouse;
    private String reason;                                               // 报损/报溢原因
    private String remark;
    private String reviewer;

    @OneToMany(mappedBy="adjustment", cascade=CascadeType.ALL, orphanRemoval=true) private List<AdjustmentLine> lines = new ArrayList<>();

    public Long getId(){return id;}
    public String getAdjustmentNo(){return adjustmentNo;} public void setAdjustmentNo(String v){adjustmentNo=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;}
    public Warehouse getWarehouse(){return warehouse;} public void setWarehouse(Warehouse v){warehouse=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
    public String getReviewer(){return reviewer;} public void setReviewer(String v){reviewer=v;}
    public List<AdjustmentLine> getLines(){return lines;}
    public void replaceLines(List<AdjustmentLine> values){lines.clear(); values.forEach(x->{x.setAdjustment(this); lines.add(x);});}
}