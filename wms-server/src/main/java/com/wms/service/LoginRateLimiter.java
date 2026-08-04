package com.wms.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限速：按 key（IP|用户名）统计失败次数，窗口内超过阈值则锁定。
 * 纯内存实现，重启即清零，适用于单实例部署。
 */
@Service
public class LoginRateLimiter {

    /** 窗口内最大失败次数 */
    public static final int MAX_ATTEMPTS = 5;
    /** 窗口时长 */
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private record Attempt(int count, Instant windowStart) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null) return false;
        Attempt attempt = attempts.get(key);
        if (attempt == null) return false;
        if (attempt.windowStart().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key);
            return false;
        }
        return attempt.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String key) {
        if (key == null) return;
        attempts.compute(key, (k, prev) -> {
            Instant now = Instant.now();
            if (prev == null || prev.windowStart().plus(WINDOW).isBefore(now)) {
                return new Attempt(1, now);
            }
            return new Attempt(prev.count() + 1, prev.windowStart());
        });
    }

    public void reset(String key) {
        if (key != null) attempts.remove(key);
    }
}
