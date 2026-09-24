package com.wms.repository;

import com.wms.model.entity.InventoryTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 无 limit 的全量查询，仅供需要完整历史的统计使用（库龄 FIFO、连续下降检测、收发存汇总）。 */
    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt desc")
    List<InventoryTransaction> findRecentDetailed();

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt asc, t.id asc")
    Stream<InventoryTransaction> streamDetailedOrdered();

    /** 把 limit 下推到 SQL，供"最近 N 条"展示路径使用；不要拿它做聚合，截断会静默改变结果。 */
    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt desc")
    List<InventoryTransaction> findRecentDetailedLimited(Pageable pageable);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionType = :transactionType order by t.transactionAt desc")
    List<InventoryTransaction> findByTransactionType(String transactionType);

    @Query(value = "select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionType=:transactionType order by t.transactionAt desc, t.id desc",
            countQuery = "select count(t) from InventoryTransaction t where t.transactionType=:transactionType")
    org.springframework.data.domain.Page<InventoryTransaction> pageDetailedByTransactionType(@Param("transactionType") String transactionType, Pageable pageable);

    @Query(value = "select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionType=:transactionType and t.warehouse.id in :warehouseIds order by t.transactionAt desc, t.id desc",
            countQuery = "select count(t) from InventoryTransaction t where t.transactionType=:transactionType and t.warehouse.id in :warehouseIds")
    org.springframework.data.domain.Page<InventoryTransaction> pageDetailedByTransactionTypeAndWarehouses(@Param("transactionType") String transactionType,
            @Param("warehouseIds") List<Long> warehouseIds, Pageable pageable);

    @Query("select count(t), coalesce(sum(t.saleAmount),0), coalesce(sum(t.profit),0) from InventoryTransaction t where t.transactionType='out' and (:scoped=false or t.warehouse.id in :warehouseIds)")
    Object[] salesTotals(@Param("scoped") boolean scoped, @Param("warehouseIds") List<Long> warehouseIds);

    @Query("select t from InventoryTransaction t where t.transactionAt >= :since and t.transactionAt < :until order by t.transactionAt")
    List<InventoryTransaction> findByTransactionAtBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionAt >= :since and t.transactionAt < :until order by t.transactionAt")
    List<InventoryTransaction> findDetailedBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("select case when count(t) > 0 then true else false end from InventoryTransaction t where t.item.id = :itemId")
    boolean existsByItemId(@Param("itemId") Long itemId);

    /** 商城回滚库存：按订单号查出原出库（OUT）流水，取实际扣减成本回填，避免用当前均价导致成本失真。 */
    @Query("select t from InventoryTransaction t where t.referenceNo = :referenceNo and t.transactionType = :txType order by t.transactionAt asc, t.id asc")
    List<InventoryTransaction> findByReferenceNoAndType(@Param("referenceNo") String referenceNo, @Param("txType") String txType);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.referenceNo = :referenceNo and t.reversalOfTransactionId is null and not exists (select r.id from InventoryTransaction r where r.reversalOfTransactionId = t.id) order by t.transactionAt asc, t.id asc")
    List<InventoryTransaction> findUnreversedByReferenceNo(@Param("referenceNo") String referenceNo);

    @Query(value = "select i.id, i.code, i.name, i.unit, "
            + "sum(case when t.transaction_at < :monthStart then t.quantity else 0 end), "
            + "sum(case when t.transaction_at < :monthStart then case when t.quantity >= 0 then t.total_cost_amount else -t.total_cost_amount end else 0 end), "
            + "sum(case when t.transaction_at >= :monthStart and t.quantity >= 0 then t.quantity else 0 end), "
            + "sum(case when t.transaction_at >= :monthStart and t.quantity >= 0 then t.total_cost_amount else 0 end), "
            + "sum(case when t.transaction_at >= :monthStart and t.quantity < 0 then -t.quantity else 0 end), "
            + "sum(case when t.transaction_at >= :monthStart and t.quantity < 0 then t.total_cost_amount else 0 end) "
            + "from inventory_transactions t join items i on i.id=t.item_id where t.transaction_at < :monthEnd "
            + "and (:scoped=false or t.warehouse_id in (:warehouseIds)) group by i.id, i.code, i.name, i.unit order by i.code",
            nativeQuery = true)
    List<Object[]> aggregateInOutSummary(@Param("monthStart") LocalDateTime monthStart,
                                         @Param("monthEnd") LocalDateTime monthEnd,
                                         @Param("scoped") boolean scoped,
                                         @Param("warehouseIds") List<Long> warehouseIds);
}
