package com.wms.repository;

import com.wms.model.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHash(String tokenHash);
    long deleteByExpiresAtBefore(LocalDateTime time);

    /** R4-11：分批清理取批次 id（expiresAt 存在于实体，非基类字段）。 */
    @Query("select s.id from AuthSession s where s.expiresAt < :now")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("delete from AuthSession s where s.id in :ids")
    int deleteByIdIn(@Param("ids") java.util.Collection<Long> ids);
    long deleteByUsername(String username);
    long deleteByTokenHash(String tokenHash);
}
