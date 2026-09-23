package com.wms.service;

import com.wms.model.entity.LoginAttempt;
import com.wms.repository.LoginAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/** 登录失败限速持久化到数据库，重启和多实例共享同一窗口。 */
@Service
public class LoginRateLimiter {
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);
    private final LoginAttemptRepository attempts;

    public LoginRateLimiter(LoginAttemptRepository attempts) { this.attempts = attempts; }

    @Transactional
    public boolean isBlocked(String key) {
        if (key == null) return false;
        LoginAttempt attempt = attempts.findByRateKey(key).orElse(null);
        if (attempt == null) return false;
        if (attempt.getWindowStart().plus(WINDOW).isBefore(LocalDateTime.now())) {
            attempts.delete(attempt);
            return false;
        }
        return attempt.getAttemptCount() >= MAX_ATTEMPTS;
    }

    @Transactional
    public void recordFailure(String key) {
        if (key == null) return;
        LocalDateTime now = LocalDateTime.now();
        LoginAttempt attempt = attempts.findByRateKey(key).orElse(null);
        if (attempt == null) {
            attempt = new LoginAttempt();
            attempt.setRateKey(key);
            attempt.setAttemptCount(1);
            attempt.setWindowStart(now);
        } else if (attempt.getWindowStart().plus(WINDOW).isBefore(now)) {
            attempt.setAttemptCount(1);
            attempt.setWindowStart(now);
        } else {
            attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        }
        attempts.save(attempt);
    }

    @Transactional
    public void reset(String key) { if (key != null) attempts.deleteByRateKey(key); }
}
