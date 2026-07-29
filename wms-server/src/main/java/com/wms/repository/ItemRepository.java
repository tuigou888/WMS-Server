package com.wms.repository;
import com.wms.model.entity.Item;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ItemRepository extends JpaRepository<Item,Long> { Optional<Item> findByCode(String code); boolean existsByCode(String code); Page<Item> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name,String code, Pageable page); }
