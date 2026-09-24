package com.wms.repository;
import com.wms.model.entity.AdjustmentOrder; import jakarta.persistence.LockModeType; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface AdjustmentOrderRepository extends JpaRepository<AdjustmentOrder,Long> {
 @Query("select distinct a from AdjustmentOrder a join fetch a.warehouse left join fetch a.lines l left join fetch l.item order by a.createdAt desc") List<AdjustmentOrder> findAllDetailed();
 @Query("select distinct a from AdjustmentOrder a join fetch a.warehouse left join fetch a.lines l left join fetch l.item where a.id=:id") Optional<AdjustmentOrder> findDetailedById(@Param("id") Long id);
 @Query("select a.id from AdjustmentOrder a where (:scoped=false or a.warehouse.id in :warehouseIds) order by a.createdAt desc, a.id desc") Page<Long> pageIds(@Param("scoped") boolean scoped,@Param("warehouseIds") List<Long> warehouseIds,Pageable pageable);
 @Query("select distinct a from AdjustmentOrder a join fetch a.warehouse left join fetch a.lines l left join fetch l.item where a.id in :ids") List<AdjustmentOrder> findDetailedByIdIn(@Param("ids") Collection<Long> ids);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdjustmentOrder a where a.id=:id") Optional<AdjustmentOrder> findForUpdateById(@Param("id") Long id);
}
