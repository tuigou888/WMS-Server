package com.wms.repository;

import com.wms.model.entity.IdempotentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, Long> {
    /** 超保留期记录的批量清理：响应重放窗口之外的数据没有保留价值。 */
    @Modifying
    @Query("delete from IdempotentRequest r where r.createdAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);

    @Modifying
    @Query(value = "insert into idempotent_requests (username, scope, request_key, request_hash, created_at, updated_at) "
            + "values (:username, :scope, :requestKey, :requestHash, now(), now()) "
            + "on duplicate key update request_key = request_key", nativeQuery = true)
    void insertIfAbsent(@Param("username") String username, @Param("scope") String scope,
                        @Param("requestKey") String requestKey, @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotentRequest r where r.username=:username and r.scope=:scope and r.requestKey=:requestKey")
    Optional<IdempotentRequest> findForUpdate(@Param("username") String username, @Param("scope") String scope,
                                              @Param("requestKey") String requestKey);
}
