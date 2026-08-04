package com.wms.repository;

import com.wms.model.entity.DocumentSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    Optional<DocumentSequence> findByPrefix(String prefix);

    /** 原子插入序号行（并发首次取号时只有一个成功），需与唯一约束配合，兼容 MySQL / H2(MODE=MySQL)。 */
    @Modifying
    @Query(value = "insert into document_sequences (prefix, counter, created_at, updated_at) "
            + "values (:prefix, 0, now(), now()) on duplicate key update prefix = prefix", nativeQuery = true)
    void insertIfAbsent(@Param("prefix") String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ds from DocumentSequence ds where ds.prefix = :prefix")
    Optional<DocumentSequence> findForUpdate(@Param("prefix") String prefix);
}