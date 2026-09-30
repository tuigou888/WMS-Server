package com.wms.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/**
 * 测试 Profile 的 ShedLock 替身：测试库不建 shedlock 表（Flyway 关闭），而用例需要直调
 * 带 @SchedulerLock 的 @Scheduled 方法体。此 Provider 永远放行并立即释放，仅保留 AOP 语义（不互斥）。
 */
@Configuration
@Profile("test")
public class TestLockProviderConfig {

    @Bean
    public LockProvider lockProvider() {
        return (LockConfiguration configuration) -> Optional.of(() -> { /* 立即释放语义 */ });
    }
}
