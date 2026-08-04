package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_sequences")
public class DocumentSequence extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String prefix;

    @Column(nullable = false)
    private long counter;

    public DocumentSequence() {}

    public DocumentSequence(String prefix, long counter) {
        this.prefix = prefix;
        this.counter = counter;
    }

    public Long getId() { return id; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public long getCounter() { return counter; }
    public void setCounter(long counter) { this.counter = counter; }
}