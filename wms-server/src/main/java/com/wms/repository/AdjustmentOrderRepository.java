package com.wms.repository;
import com.wms.model.entity.AdjustmentOrder; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface AdjustmentOrderRepository extends JpaRepository<AdjustmentOrder,Long> {
 @Query("select distinct a from AdjustmentOrder a join fetch a.warehouse left join fetch a.lines l left join fetch l.item order by a.createdAt desc") List<AdjustmentOrder> findAllDetailed();
 @Query("select distinct a from AdjustmentOrder a join fetch a.warehouse left join fetch a.lines l left join fetch l.item where a.id=:id") Optional<AdjustmentOrder> findDetailedById(@Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdjustmentOrder a where a.id=:id") Optional<AdjustmentOrder> findForUpdateById(@Param("id") Long id);
}