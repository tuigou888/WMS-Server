package com.wms.repository;

import com.wms.model.entity.StocktakeOrder;
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

public interface StocktakeOrderRepository extends JpaRepository<StocktakeOrder, Long> {
    @Query("select distinct s from StocktakeOrder s join fetch s.warehouse left join fetch s.lines l left join fetch l.item order by s.createdAt desc")
    List<StocktakeOrder> findAllDetailed();

    @Query("select distinct s from StocktakeOrder s join fetch s.warehouse left join fetch s.lines l left join fetch l.item where s.id=:id")
    Optional<StocktakeOrder> findDetailedById(@Param("id") Long id);

    @Query("select s.id from StocktakeOrder s where (:scoped=false or s.warehouse.id in :warehouseIds) order by s.createdAt desc, s.id desc")
    Page<Long> pageIds(@Param("scoped") boolean scoped, @Param("warehouseIds") List<Long> warehouseIds, Pageable pageable);

    @Query("select distinct s from StocktakeOrder s join fetch s.warehouse left join fetch s.lines l left join fetch l.item where s.id in :ids")
    List<StocktakeOrder> findDetailedByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StocktakeOrder s where s.id=:id") Optional<StocktakeOrder> findForUpdateById(@Param("id") Long id);
}
