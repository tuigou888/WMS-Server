package com.wms.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 调度锁（上线整改 A7）：让 @Scheduled 任务跨实例互斥，锁状态存 shedlock 表（V10 迁移建表）。
 * usingDbTime() 以数据库时间判定锁过期（H2 走 CURRENT_TIMESTAMP(3)、MySQL 走 UTC_TIMESTAMP(3)，按连接自动探测），
 * 规避实例间时钟漂移导致的提前抢锁；锁由 @SchedulerLock 注解声明，方法正常/异常返回时释放。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT1H")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
