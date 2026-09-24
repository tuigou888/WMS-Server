package com.wms.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_warehouse_access", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_warehouse_access", columnNames = {"user_id", "warehouse_id"}))
public class UserWarehouseAccess extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "warehouse_id", nullable = false) private Warehouse warehouse;

    protected UserWarehouseAccess() {}
    public UserWarehouseAccess(UserAccount user, Warehouse warehouse) { this.user = user; this.warehouse = warehouse; }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public Warehouse getWarehouse() { return warehouse; }
}
