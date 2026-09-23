package com.wms.model.entity.market;
import com.wms.model.entity.AuditableEntity; import com.wms.model.entity.Item; import com.wms.model.entity.Warehouse; import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDateTime;
/** 商城支付前的库存预占；不改变物理库存，available=物理库存-有效 HELD 预占。 */
@Entity @Table(name="inventory_reservations",uniqueConstraints=@UniqueConstraint(columnNames={"order_id","item_id"}),indexes={@Index(name="idx_reservation_available",columnList="item_id,warehouse_id,status,expires_at"),@Index(name="idx_reservation_order",columnList="order_id")}) public class InventoryReservation extends AuditableEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="order_id",nullable=false) private MarketOrder order;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="item_id",nullable=false) private Item item;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal quantity=BigDecimal.ZERO;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private InventoryReservationStatus status=InventoryReservationStatus.HELD;
 @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt; private LocalDateTime consumedAt; private LocalDateTime releasedAt;
 public InventoryReservation(){} public InventoryReservation(MarketOrder order,Item item,Warehouse warehouse,BigDecimal quantity,LocalDateTime expiresAt){this.order=order;this.item=item;this.warehouse=warehouse;this.quantity=quantity;this.expiresAt=expiresAt;}
 public Long getId(){return id;} public MarketOrder getOrder(){return order;} public Item getItem(){return item;} public Warehouse getWarehouse(){return warehouse;} public BigDecimal getQuantity(){return quantity;} public InventoryReservationStatus getStatus(){return status;} public void setStatus(InventoryReservationStatus v){status=v;} public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;} public LocalDateTime getConsumedAt(){return consumedAt;} public void setConsumedAt(LocalDateTime v){consumedAt=v;} public LocalDateTime getReleasedAt(){return releasedAt;} public void setReleasedAt(LocalDateTime v){releasedAt=v;}
}
