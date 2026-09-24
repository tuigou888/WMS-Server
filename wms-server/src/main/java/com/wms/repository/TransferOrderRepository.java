package com.wms.repository;

import com.wms.model.entity.TransferOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
    @Query("select distinct t from TransferOrder t join fetch t.sourceWarehouse join fetch t.targetWarehouse left join fetch t.lines l left join fetch l.item order by t.createdAt desc")
    List<TransferOrder> findAllDetailed();

    @Query("select distinct t from TransferOrder t join fetch t.sourceWarehouse join fetch t.targetWarehouse left join fetch t.lines l left join fetch l.item where t.id=:id")
    Optional<TransferOrder> findDetailedById(@Param("id") Long id);

    @Query("select t.id from TransferOrder t where (:scoped=false or (t.sourceWarehouse.id in :warehouseIds and t.targetWarehouse.id in :warehouseIds)) order by t.createdAt desc, t.id desc")
    Page<Long> pageIds(@Param("scoped") boolean scoped, @Param("warehouseIds") List<Long> warehouseIds, Pageable pageable);

    @Query("select distinct t from TransferOrder t join fetch t.sourceWarehouse join fetch t.targetWarehouse left join fetch t.lines l left join fetch l.item where t.id in :ids")
    List<TransferOrder> findDetailedByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TransferOrder t where t.id=:id") Optional<TransferOrder> findForUpdateById(@Param("id") Long id);
}
