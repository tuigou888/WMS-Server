package com.wms.repository;

import com.wms.model.entity.DocumentSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    Optional<DocumentSequence> findByPrefix(String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ds from DocumentSequence ds where ds.prefix = :prefix")
    Optional<DocumentSequence> findForUpdate(@Param("prefix") String prefix);
}