package com.wms.repository;

import com.wms.model.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location")
    List<Inventory> findAllDetailed();

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.warehouse.id=:warehouseId")
    List<Inventory> findAllDetailedByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("select i from Inventory i join fetch i.item item left join fetch item.category left join fetch item.defaultWarehouse join fetch i.warehouse left join fetch i.location order by i.id")
    Stream<Inventory> streamAllDetailed();

    @Query("select i from Inventory i join fetch i.item item left join fetch item.category left join fetch item.defaultWarehouse join fetch i.warehouse left join fetch i.location where i.warehouse.id in :warehouseIds order by i.id")
    Stream<Inventory> streamAllDetailedByWarehouseIds(@Param("warehouseIds") List<Long> warehouseIds);

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location order by i.updatedAt desc, i.id desc")
    Page<Inventory> findAllDetailed(Pageable pageable);

    @Query(value = "select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.warehouse.id in :warehouseIds order by i.updatedAt desc, i.id desc",
            countQuery = "select count(i) from Inventory i where i.warehouse.id in :warehouseIds")
    Page<Inventory> findAllDetailedByWarehouseIds(@Param("warehouseIds") List<Long> warehouseIds, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.item.id = :itemId and i.warehouse.id = :warehouseId "
            + "and ((:locationId is null and i.location is null) or i.location.id = :locationId) "
            + "and ((:batchNo is null and i.batchNo is null) or i.batchNo = :batchNo)")
    Optional<Inventory> findForUpdate(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId,
                                      @Param("locationId") Long locationId, @Param("batchNo") String batchNo);

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.item.id = :itemId")
    List<Inventory> findByItemId(@Param("itemId") Long itemId);

    /** 商城下单履约：按 item+warehouse 找出可扣库存（FIFO 按 updatedAt 升序）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.item.id = :itemId and i.warehouse.id = :warehouseId "
            + "and i.quantity > 0 order by i.updatedAt asc")
    List<Inventory> findFifoForOut(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    /** 商城下单预校验：某 item 在指定仓库的总可用库存。 */
    @Query("select coalesce(sum(i.quantity),0) from Inventory i where i.item.id = :itemId and i.warehouse.id = :warehouseId")
    java.math.BigDecimal availableQty(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    /** 商城取消回滚：取 item+warehouse 下第一条库存记录用于回填（限 quantity>0，加悲观锁防与并发出库竞态）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.item.id = :itemId and i.warehouse.id = :warehouseId "
            + "and i.quantity > 0 order by i.updatedAt asc")
    List<Inventory> findByItemAndWarehouse(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.batchNo is not null and i.batchNo <> ''")
    List<Inventory> findWithBatch();
}
