package com.wms.repository.market;

import com.wms.model.entity.market.MarketFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MarketFavoriteRepository extends JpaRepository<MarketFavorite, Long> {
    @Query("select f from MarketFavorite f join fetch f.product p join fetch p.item item left join fetch item.category left join fetch p.category where f.user.id = :userId and p.id = :productId")
    Optional<MarketFavorite> findByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    @Query(value = "select f from MarketFavorite f join fetch f.product p join fetch p.item item left join fetch item.category left join fetch p.category where f.user.id = :userId order by f.id desc",
            countQuery = "select count(f) from MarketFavorite f where f.user.id = :userId")
    Page<MarketFavorite> findByUserIdOrderByIdDesc(@Param("userId") Long userId, Pageable pageable);
    long countByUserId(Long userId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("delete from MarketFavorite f where f.user.id = :userId and f.product.id = :productId")
    void deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
}
