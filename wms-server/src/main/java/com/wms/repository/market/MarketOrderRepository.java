package com.wms.repository.market;

import com.wms.model.entity.market.MarketOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {
    boolean existsByOrderNo(String orderNo);

    @Query("select o from MarketOrder o left join fetch o.items i left join fetch i.item "
            + "where (:userId is null or o.user.id = :userId) "
            + "and (:status is null or :status = '' or o.orderStatus = :status) order by o.createdAt desc")
    Page<MarketOrder> search(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    @Query("select o from MarketOrder o left join fetch o.items i left join fetch i.item "
            + "where (:keyword is null or :keyword = '' "
            + "   or lower(o.orderNo) like lower(concat('%', :keyword, '%')) "
            + "   or lower(o.receiverName) like lower(concat('%', :keyword, '%')) "
            + "   or lower(o.receiverPhone) like lower(concat('%', :keyword, '%'))) "
            + "and (:status is null or :status = '' or o.orderStatus = :status) order by o.createdAt desc")
    Page<MarketOrder> searchAdmin(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);

    @Query("select o from MarketOrder o left join fetch o.items i left join fetch i.item where o.id = :id")
    Optional<MarketOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o where o.id = :id")
    Optional<MarketOrder> findForUpdateById(@Param("id") Long id);

    @Query("select count(o) from MarketOrder o where o.user.id = :userId and o.orderStatus not in ('CANCELLED')")
    long countActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update MarketOrder o set o.shippedAt = :time where o.id = :id")
    // 保留占位（实际状态流转走 findForUpdate 后 setter），接口仅供按需
    void touchShipped(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);
}
