package com.wms.repository.market;

import com.wms.model.entity.market.MarketCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketCartRepository extends JpaRepository<MarketCart, Long> {
    @Query("select c from MarketCart c join fetch c.product p join fetch p.item item left join fetch item.category left join fetch p.category where c.user.id = :userId and p.id = :productId")
    Optional<MarketCart> findByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    @Query("select c from MarketCart c join fetch c.product p join fetch p.item item left join fetch item.category left join fetch p.category where c.user.id = :userId order by c.id desc")
    List<MarketCart> findByUserIdOrderByIdDesc(@Param("userId") Long userId);
    @Query("select c from MarketCart c join fetch c.product p join fetch p.item item left join fetch item.category left join fetch p.category where c.id = :id")
    Optional<MarketCart> findDetailedById(@Param("id") Long id);
    long countByUserId(Long userId);

    @Modifying
    @Query("delete from MarketCart c where c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from MarketCart c where c.user.id = :userId and c.id in :ids")
    void deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
