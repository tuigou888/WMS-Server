package com.wms.repository;

import com.wms.model.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location order by t.transactionAt desc")
    List<InventoryTransaction> findRecentDetailed();

    @Query("select t from InventoryTransaction t join fetch t.item join fetch t.warehouse left join fetch t.location where t.transactionType = :transactionType order by t.transactionAt desc")
    List<InventoryTransaction> findByTransactionType(String transactionType);

    @Query("select t from InventoryTransaction t where t.transactionAt >= :since and t.transactionAt < :until order by t.transactionAt")
    List<InventoryTransaction> findByTransactionAtBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);
}
