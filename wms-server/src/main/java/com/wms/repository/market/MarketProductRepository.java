package com.wms.repository.market;

import com.wms.model.entity.market.MarketProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketProductRepository extends JpaRepository<MarketProduct, Long> {
    Optional<MarketProduct> findByItemId(Long itemId);
    boolean existsByItemId(Long itemId);
    boolean existsByTitle(String title);

    @Query("select p from MarketProduct p join fetch p.item where p.status = 'SHELF_ON'")
    List<MarketProduct> findAllShelfOn();

    @Query("select p from MarketProduct p join fetch p.item where p.status = 'SHELF_ON' order by p.sortNo asc, p.salesCount desc")
    Page<MarketProduct> findShelfOn(Pageable pageable);

    @Query("select p from MarketProduct p join fetch p.item where p.status = 'SHELF_ON' "
            + "and (:categoryId is null or p.category.id = :categoryId) "
            + "and (:keyword is null or :keyword = '' or lower(p.title) like lower(concat('%', :keyword, '%')) "
            + "   or lower(p.item.name) like lower(concat('%', :keyword, '%')) "
            + "   or lower(p.item.code) like lower(concat('%', :keyword, '%'))) order by p.sortNo asc, p.salesCount desc")
    Page<MarketProduct> searchShelfOn(@Param("categoryId") Long categoryId, @Param("keyword") String keyword, Pageable pageable);

    @Query("select p from MarketProduct p left join fetch p.item left join fetch p.category "
            + "where (:keyword is null or :keyword = '' or lower(p.title) like lower(concat('%', :keyword, '%')) "
            + "   or lower(p.item.name) like lower(concat('%', :keyword, '%')) "
            + "   or lower(p.item.code) like lower(concat('%', :keyword, '%')))")
    Page<MarketProduct> searchAll(@Param("keyword") String keyword, Pageable pageable);

    @Query("select p from MarketProduct p join fetch p.item where p.id = :id")
    Optional<MarketProduct> findDetailedById(@Param("id") Long id);

    @Query("select p from MarketProduct p join fetch p.item where p.item.id = :itemId")
    Optional<MarketProduct> findByItemIdDetailed(@Param("itemId") Long itemId);

    @Query("select count(p) from MarketProduct p where p.status='SHELF_ON'")
    long countShelfOn();

    @Query("select sum(p.salesCount) from MarketProduct p where p.status='SHELF_ON'")
    Long sumSalesCount();

    @Modifying
    @Query("update MarketProduct p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void incrementView(@Param("id") Long id);
}
