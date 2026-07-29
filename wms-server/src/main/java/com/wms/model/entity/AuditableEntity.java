package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AuditableEntity {
    @Column(nullable = false, updatable = false) protected LocalDateTime createdAt;
    @Column(nullable = false) protected LocalDateTime updatedAt;
    @PrePersist void createAudit() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void updateAudit() { updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
