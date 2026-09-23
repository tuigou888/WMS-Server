package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_sessions_expires_at", columnList = "expires_at"))
public class AuthSession extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 50) private String username;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { tokenHash = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }
}
