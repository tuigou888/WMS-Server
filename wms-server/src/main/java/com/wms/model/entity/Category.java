package com.wms.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

/**
 * 类级 @BatchSize：Item.category 是 EAGER @ManyToOne，Hibernate 6 列表查询（items 列表页/Excel 导出/各选择器）
 * 会对每行发一条 follow-on SELECT（全局 N+1）。6.2+ 的 BatchInitializeEntitySelectFetchInitializer 会按
 * 目标实体的 batch size 把这些 follow-on 合并成 IN 查询（50 行一批），把 N 次压成 N/50 次。
 * 注意：@BatchSize 不能标在 @ManyToOne 字段上（启动即 AnnotationException），只能标在目标实体类或集合上。
 */
@Entity
@BatchSize(size = 50)
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