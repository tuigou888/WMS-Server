package com.wms.model.entity;
import jakarta.persistence.*;
@Entity @Table(name="locations", uniqueConstraints=@UniqueConstraint(columnNames={"warehouse_id","code"}))
public class Location extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="warehouse_id") private Warehouse warehouse;
 @Column(nullable=false) private String code; private Boolean status=true;
 public Location() {} public Location(Warehouse warehouse,String code){this.warehouse=warehouse;this.code=code;}
 public Long getId(){return id;} public Warehouse getWarehouse(){return warehouse;} public String getCode(){return code;} public Boolean getStatus(){return status;}
}
