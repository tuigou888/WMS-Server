package com.wms.repository;

import com.wms.model.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHash(String tokenHash);
    long deleteByExpiresAtBefore(LocalDateTime time);
    long deleteByUsername(String username);
    long deleteByTokenHash(String tokenHash);
}
