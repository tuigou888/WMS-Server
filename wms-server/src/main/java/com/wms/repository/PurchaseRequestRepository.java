package com.wms.repository; import com.wms.model.entity.PurchaseRequest; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest,Long>{ @Query("select distinct p from PurchaseRequest p join fetch p.warehouse left join fetch p.supplier left join fetch p.lines l join fetch l.item order by p.createdAt desc") List<PurchaseRequest> findAllDetailed();  @Query("select distinct p from PurchaseRequest p join fetch p.warehouse left join fetch p.supplier left join fetch p.lines l join fetch l.item where p.id=:id") Optional<PurchaseRequest> findDetailedById(Long id);
 @Query("select case when count(l) > 0 then true else false end from PurchaseRequestLine l where l.item.id=:itemId") boolean existsLineByItemId(Long itemId);
 boolean existsBySupplierId(Long supplierId);
}
