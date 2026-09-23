package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wechat_bind_tickets", indexes = @Index(name = "idx_wechat_bind_tickets_expires_at", columnList = "expires_at"))
public class WechatBindTicket extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ticket_hash", nullable = false, unique = true, length = 64) private String ticketHash;
    @Column(nullable = false, length = 64) private String openid;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    public Long getId() { return id; }
    public String getTicketHash() { return ticketHash; }
    public void setTicketHash(String value) { ticketHash = value; }
    public String getOpenid() { return openid; }
    public void setOpenid(String value) { openid = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }
}
