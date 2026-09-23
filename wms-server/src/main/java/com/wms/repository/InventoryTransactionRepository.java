package com.wms.repository;

import com.wms.model.entity.InventoryTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 无 limit 的全量查询，仅供需要完整历史的统计使用（库龄 FIFO、连续下降检测、收发存汇总）。 */
    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt desc")
    List<InventoryTransaction> findRecentDetailed();

    /** 把 limit 下推到 SQL，供"最近 N 条"展示路径使用；不要拿它做聚合，截断会静默改变结果。 */
    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt desc")
    List<InventoryTransaction> findRecentDetailedLimited(Pageable pageable);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionType = :transactionType order by t.transactionAt desc")
    List<InventoryTransaction> findByTransactionType(String transactionType);

    @Query("select t from InventoryTransaction t where t.transactionAt >= :since and t.transactionAt < :until order by t.transactionAt")
    List<InventoryTransaction> findByTransactionAtBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionAt >= :since and t.transactionAt < :until order by t.transactionAt")
    List<InventoryTransaction> findDetailedBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("select case when count(t) > 0 then true else false end from InventoryTransaction t where t.item.id = :itemId")
    boolean existsByItemId(@Param("itemId") Long itemId);

    /** 商城回滚库存：按订单号查出原出库（OUT）流水，取实际扣减成本回填，避免用当前均价导致成本失真。 */
    @Query("select t from InventoryTransaction t where t.referenceNo = :referenceNo and t.transactionType = :txType order by t.transactionAt asc, t.id asc")
    List<InventoryTransaction> findByReferenceNoAndType(@Param("referenceNo") String referenceNo, @Param("txType") String txType);

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.referenceNo = :referenceNo and t.reversalOfTransactionId is null order by t.transactionAt asc, t.id asc")
    List<InventoryTransaction> findUnreversedByReferenceNo(@Param("referenceNo") String referenceNo);
}
