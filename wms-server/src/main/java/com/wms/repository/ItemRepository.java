package com.wms.repository;
import com.wms.model.entity.Item;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ItemRepository extends JpaRepository<Item,Long> {
 Optional<Item> findByCode(String code); boolean existsByCode(String code); Page<Item> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name,String code, Pageable page);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Item i where i.id=:id") Optional<Item> findForInventoryCreation(@Param("id") Long id);
}
