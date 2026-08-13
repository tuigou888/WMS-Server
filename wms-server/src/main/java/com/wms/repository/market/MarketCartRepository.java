package com.wms.repository.market;

import com.wms.model.entity.market.MarketCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketCartRepository extends JpaRepository<MarketCart, Long> {
    Optional<MarketCart> findByUserIdAndProductId(Long userId, Long productId);
    List<MarketCart> findByUserIdOrderByIdDesc(Long userId);
    long countByUserId(Long userId);

    @Modifying
    @Query("delete from MarketCart c where c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from MarketCart c where c.user.id = :userId and c.id in :ids")
    void deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
