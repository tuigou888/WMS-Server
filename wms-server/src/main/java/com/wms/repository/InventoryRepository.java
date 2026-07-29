package com.wms.repository;

import com.wms.model.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location")
    List<Inventory> findAllDetailed();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.item.id = :itemId and i.warehouse.id = :warehouseId "
            + "and ((:locationId is null and i.location is null) or i.location.id = :locationId) "
            + "and ((:batchNo is null and i.batchNo is null) or i.batchNo = :batchNo)")
    Optional<Inventory> findForUpdate(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId,
                                      @Param("locationId") Long locationId, @Param("batchNo") String batchNo);

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.item.id = :itemId")
    List<Inventory> findByItemId(@Param("itemId") Long itemId);

    @Query("select i from Inventory i join fetch i.item join fetch i.warehouse left join fetch i.location where i.batchNo is not null and i.batchNo <> ''")
    List<Inventory> findWithBatch();
}