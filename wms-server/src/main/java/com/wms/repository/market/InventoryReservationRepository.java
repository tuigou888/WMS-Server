package com.wms.repository.market;
import com.wms.model.entity.market.InventoryReservation; import com.wms.model.entity.market.InventoryReservationStatus; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.List;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation,Long> {
 @Query("select coalesce(sum(r.quantity),0) from InventoryReservation r where r.item.id=:itemId and r.warehouse.id=:warehouseId and r.status='HELD' and r.expiresAt>:now") BigDecimal sumHeldQuantity(@Param("itemId") Long itemId,@Param("warehouseId") Long warehouseId,@Param("now") LocalDateTime now);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from InventoryReservation r where r.item.id=:itemId and r.warehouse.id=:warehouseId and r.status='HELD' and r.expiresAt>:now order by r.id") List<InventoryReservation> findHeldForItemWarehouseForUpdate(@Param("itemId") Long itemId,@Param("warehouseId") Long warehouseId,@Param("now") LocalDateTime now);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from InventoryReservation r join fetch r.item where r.order.id=:orderId order by r.id") List<InventoryReservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);
 @Query("select r from InventoryReservation r where r.status='HELD' and r.expiresAt<=:now order by r.id") List<InventoryReservation> findExpiredHeld(@Param("now") LocalDateTime now);
 List<InventoryReservation> findByOrderIdOrderByIdAsc(Long orderId);
}
