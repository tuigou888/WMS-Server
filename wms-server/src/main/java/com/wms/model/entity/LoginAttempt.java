package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_login_attempts")
public class LoginAttempt extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "rate_key", nullable = false, unique = true, length = 255) private String rateKey;
    @Column(name = "attempt_count", nullable = false) private Integer attemptCount;
    @Column(name = "window_start", nullable = false) private LocalDateTime windowStart;
    public Long getId() { return id; }
    public String getRateKey() { return rateKey; }
    public void setRateKey(String value) { rateKey = value; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { attemptCount = value; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime value) { windowStart = value; }
}
