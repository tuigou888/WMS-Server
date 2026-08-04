package com.wms.repository;
import com.wms.model.entity.StockDocument; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface StockDocumentRepository extends JpaRepository<StockDocument,Long> {
 @Query("select distinct d from StockDocument d join fetch d.warehouse left join fetch d.partner left join fetch d.lines l left join fetch l.item order by d.createdAt desc") List<StockDocument> findAllDetailed();
 @Query("select distinct d from StockDocument d join fetch d.warehouse left join fetch d.partner left join fetch d.lines l left join fetch l.item where d.id=:id") Optional<StockDocument> findDetailedById(@Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from StockDocument d where d.id=:id") Optional<StockDocument> findForUpdateById(@Param("id") Long id);
 @Query("select case when count(l) > 0 then true else false end from StockDocumentLine l where l.item.id=:itemId") boolean existsLineByItemId(@Param("itemId") Long itemId);
 boolean existsByPartnerId(Long partnerId);
 boolean existsByDocumentNo(String documentNo);
}
