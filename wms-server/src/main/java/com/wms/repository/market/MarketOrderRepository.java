package com.wms.repository.market;

import com.wms.model.entity.market.MarketOrder;
import com.wms.model.entity.market.MarketOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {
    boolean existsByOrderNo(String orderNo);

    /** 微信支付回调按 out_trade_no（=orderNo）查单。 */
    @Query("select distinct o from MarketOrder o left join fetch o.user left join fetch o.warehouse left join fetch o.customer left join fetch o.items i left join fetch i.item left join fetch i.item.category where o.orderNo = :orderNo")
    Optional<MarketOrder> findByOrderNo(@Param("orderNo") String orderNo);

    @Query("select distinct o from MarketOrder o left join fetch o.user left join fetch o.warehouse left join fetch o.customer left join fetch o.items i left join fetch i.item left join fetch i.item.category "
            + "where (:userId is null or o.user.id = :userId) "
            + "and (:status is null or o.orderStatus = :status) order by o.createdAt desc")
    Page<MarketOrder> search(@Param("userId") Long userId, @Param("status") MarketOrderStatus status, Pageable pageable);

    @Query("select distinct o from MarketOrder o left join fetch o.user left join fetch o.warehouse left join fetch o.customer left join fetch o.items i left join fetch i.item left join fetch i.item.category "
            + "where (:keyword is null or :keyword = '' "
            + "   or lower(o.orderNo) like lower(concat('%', :keyword, '%')) "
            + "   or lower(o.receiverName) like lower(concat('%', :keyword, '%')) "
            + "   or lower(o.receiverPhone) like lower(concat('%', :keyword, '%'))) "
            + "and (:status is null or o.orderStatus = :status) order by o.createdAt desc")
    Page<MarketOrder> searchAdmin(@Param("keyword") String keyword, @Param("status") MarketOrderStatus status, Pageable pageable);

    @Query("select distinct o from MarketOrder o left join fetch o.user left join fetch o.warehouse left join fetch o.customer left join fetch o.items i left join fetch i.item left join fetch i.item.category where o.id = :id")
    Optional<MarketOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o left join fetch o.user left join fetch o.warehouse left join fetch o.customer left join fetch o.items i left join fetch i.item where o.id = :id")
    Optional<MarketOrder> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o where o.refundNo = :refundNo")
    Optional<MarketOrder> findByRefundNoForUpdate(@Param("refundNo") String refundNo);

    @Query("select o from MarketOrder o where o.refundNo = :refundNo")
    Optional<MarketOrder> findByRefundNo(@Param("refundNo") String refundNo);

    /** 取消后才收到支付回调的线上订单，交由定时任务自动发起退款。 */
    @Query("select o.id from MarketOrder o where o.orderStatus = 'CANCELLED' and o.payStatus = 'PAID' and o.payType = 'PAY_ONLINE'")
    List<Long> findCancelledPaidOnlineOrderIds();

    @Query("select count(o) from MarketOrder o where o.user.id = :userId and o.orderStatus not in ('CANCELLED')")
    long countActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update MarketOrder o set o.shippedAt = :time where o.id = :id")
    // 保留占位（实际状态流转走 findForUpdate 后 setter），接口仅供按需
    void touchShipped(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);

    /** 销售额合计（已完成订单） */
    @Query("select coalesce(sum(o.totalAmount),0) from MarketOrder o where o.orderStatus = 'COMPLETED'")
    java.math.BigDecimal sumCompletedAmount();

    /** 今日下单数 */
    @Query("select count(o) from MarketOrder o where cast(o.createdAt as date) = current_date and o.orderStatus <> 'CANCELLED'")
    long countTodayOrders();

    /** 今日销售额 */
    @Query("select coalesce(sum(o.totalAmount),0) from MarketOrder o where cast(o.createdAt as date) = current_date and o.orderStatus <> 'CANCELLED'")
    java.math.BigDecimal sumTodayAmount();

    /** 各状态订单数 */
    @Query("select count(o) from MarketOrder o where o.orderStatus = :status")
    long countByStatus(@Param("status") MarketOrderStatus status);

    /** 商品销量 Top N（已完成订单的明细聚合） */
    @Query("select i.itemName as name, i.itemCode as code, coalesce(sum(i.quantity),0) as qty, coalesce(sum(i.subtotal),0) as amount "
            + "from MarketOrderItem i where i.order.orderStatus = 'COMPLETED' "
            + "group by i.itemName, i.itemCode order by qty desc")
    List<Object[]> topProducts(@Param("limit") Pageable pageable);
}
