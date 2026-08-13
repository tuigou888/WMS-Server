package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.Warehouse;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商城订单主表。
 * 状态机：PENDING（待付款）→ AUDITED（已审核/待发货）→ SHIPPING（已发货）→ COMPLETED（已完成）
 *        PENDING/AUDITED 可 → CANCELLED（取消并回滚库存）
 * 支付状态：UNPAID → PAID / REFUNDED
 */
@Entity
@Table(name = "market_order",
        uniqueConstraints = @UniqueConstraint(columnNames = "order_no"))
public class MarketOrder extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, unique = true, length = 32) private String orderNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private MarketCustomer customer;

    @Column(nullable = false, length = 60) private String receiverName;
    @Column(nullable = false, length = 20) private String receiverPhone;
    @Column(nullable = false, length = 200) private String receiverAddress;

    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 支付方式：PAY_ONLINE / CASH_ON_DELIVERY / CREDIT */
    @Column(nullable = false, length = 20) private String payType = "PAY_ONLINE";
    /** 支付状态：UNPAID / PAID / REFUNDED */
    @Column(nullable = false, length = 20) private String payStatus = "UNPAID";
    /** 订单状态：PENDING / AUDITED / SHIPPING / COMPLETED / CANCELLED */
    @Column(nullable = false, length = 20) private String orderStatus = "PENDING";

    private LocalDateTime paidAt;
    private LocalDateTime auditedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String reviewer;
    private String reviewRemark;

    @Column(length = 100) private String logisticsCompany;
    @Column(length = 100) private String logisticsNumber;

    @Column(length = 500) private String remark;
    @Column(length = 500) private String adminRemark;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MarketOrderItem> items = new ArrayList<>();

    public MarketOrder() {}

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; } public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public UserAccount getUser() { return user; } public void setUser(UserAccount user) { this.user = user; }
    public Warehouse getWarehouse() { return warehouse; } public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public MarketCustomer getCustomer() { return customer; } public void setCustomer(MarketCustomer customer) { this.customer = customer; }
    public String getReceiverName() { return receiverName; } public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; } public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getReceiverAddress() { return receiverAddress; } public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount; }
    public String getPayType() { return payType; } public void setPayType(String payType) { this.payType = payType; }
    public String getPayStatus() { return payStatus; } public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public String getOrderStatus() { return orderStatus; } public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getPaidAt() { return paidAt; } public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getAuditedAt() { return auditedAt; } public void setAuditedAt(LocalDateTime auditedAt) { this.auditedAt = auditedAt; }
    public LocalDateTime getShippedAt() { return shippedAt; } public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; } public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; } public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getReviewer() { return reviewer; } public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public String getReviewRemark() { return reviewRemark; } public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getLogisticsCompany() { return logisticsCompany; } public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
    public String getLogisticsNumber() { return logisticsNumber; } public void setLogisticsNumber(String logisticsNumber) { this.logisticsNumber = logisticsNumber; }
    public String getRemark() { return remark; } public void setRemark(String remark) { this.remark = remark; }
    public String getAdminRemark() { return adminRemark; } public void setAdminRemark(String adminRemark) { this.adminRemark = adminRemark; }
    public List<MarketOrderItem> getItems() { return items; }
    public void replaceItems(List<MarketOrderItem> list) { this.items.clear(); if (list != null) { list.forEach(i -> i.setOrder(this)); this.items.addAll(list); } }
}
