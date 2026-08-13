package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import jakarta.persistence.*;

/**
 * 订单流转日志：每一次状态变更/操作均落一条日志，便于审计与客服查询。
 */
@Entity
@Table(name = "market_order_log")
public class MarketOrderLog extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(nullable = false, length = 30) private String action;          // CREATE / PAY / AUDIT / SHIP / COMPLETE / CANCEL / REFUND
    @Column(length = 60) private String operator;                          // 操作人用户名（系统操作为 system）
    @Column(length = 500) private String remark;

    public MarketOrderLog() {}
    public MarketOrderLog(Long orderId, String action, String operator, String remark) {
        this.orderId = orderId; this.action = action; this.operator = operator; this.remark = remark;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; } public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getAction() { return action; } public void setAction(String action) { this.action = action; }
    public String getOperator() { return operator; } public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; } public void setRemark(String remark) { this.remark = remark; }
}
