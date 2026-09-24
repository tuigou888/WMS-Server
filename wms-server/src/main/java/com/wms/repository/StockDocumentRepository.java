package com.wms.repository;
import com.wms.model.entity.StockDocument; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
public interface StockDocumentRepository extends JpaRepository<StockDocument,Long> {
 @Query("select distinct d from StockDocument d join fetch d.warehouse left join fetch d.partner left join fetch d.lines l left join fetch l.item order by d.createdAt desc") List<StockDocument> findAllDetailed();
 @Query("select distinct d from StockDocument d join fetch d.warehouse left join fetch d.partner left join fetch d.lines l left join fetch l.item where d.id=:id") Optional<StockDocument> findDetailedById(@Param("id") Long id);
 @Query("select d.id from StockDocument d where (:scoped=false or d.warehouse.id in :warehouseIds) order by d.createdAt desc, d.id desc") Page<Long> pageIds(@Param("scoped") boolean scoped,@Param("warehouseIds") List<Long> warehouseIds,Pageable pageable);
 @Query("select distinct d from StockDocument d join fetch d.warehouse left join fetch d.partner left join fetch d.lines l left join fetch l.item where d.id in :ids") List<StockDocument> findDetailedByIdIn(@Param("ids") Collection<Long> ids);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from StockDocument d where d.id=:id") Optional<StockDocument> findForUpdateById(@Param("id") Long id);
 @Query("select case when count(l) > 0 then true else false end from StockDocumentLine l where l.item.id=:itemId") boolean existsLineByItemId(@Param("itemId") Long itemId);
 boolean existsByPartnerId(Long partnerId);
 boolean existsByDocumentNo(String documentNo);
 boolean existsByReversalOfDocumentId(Long reversalOfDocumentId);
}
