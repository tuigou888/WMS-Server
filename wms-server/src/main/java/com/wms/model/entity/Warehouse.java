package com.wms.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

/** 类级 @BatchSize：同 Category——Item.defaultWarehouse 等 EAGER 引用的 follow-on SELECT 按 50 行一批合并成 IN 查询。 */
@Entity
@BatchSize(size = 50)
@Table(name = "warehouses")
public class Warehouse extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private Boolean status = true;

    public Warehouse() {
    }

    public Warehouse(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }
}
