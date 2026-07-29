package com.wms.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer sortOrder = 0;

    private Boolean status = true;

    public Category() {}

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getStatus() { return status; }

    public void setStatus(Boolean status) { this.status = status; }
}