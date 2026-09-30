package com.wms.repository;

import com.wms.model.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByRateKey(String rateKey);
    long deleteByRateKey(String rateKey);

    /** 清理超过保留期的限速计数：窗口本身只有 10 分钟，保留 24h 仅供排查登录异常。 */
    @Modifying
    @Query("delete from LoginAttempt a where a.windowStart < :before")
    int deleteByWindowStartBefore(@Param("before") LocalDateTime before);

    /** R4-11：分批清理取批次 id。 */
    @Query("select a.id from LoginAttempt a where a.windowStart < :before")
    List<Long> findExpiredIds(@Param("before") LocalDateTime before, org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("delete from LoginAttempt a where a.id in :ids")
    int deleteByIdIn(@Param("ids") java.util.Collection<Long> ids);
}
