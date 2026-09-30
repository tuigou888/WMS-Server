package com.wms.service;

import com.wms.repository.IdempotentRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 幂等请求记录清理：{@code IdempotencyAspect} 只插入不删除，idempotent_requests 会随请求无限增长。
 *
 * <p>每日删除超过保留期（默认 48 小时）的记录。retention-hours 必须大于客户端最长重试窗口，
 * 否则在重放窗口内的响应会丢失。删除操作天然幂等，多实例同时跑只会重复删同一批行，无副作用
 * （与审计归档不同，无需单实例限制）。
 */
@Service
public class IdempotencyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupService.class);

    @Value("${idempotency.cleanup.enabled:true}") private boolean enabled;
    @Value("${idempotency.cleanup.retention-hours:48}") private int retentionHours;

    private final IdempotentRequestRepository requests;

    public IdempotencyCleanupService(IdempotentRequestRepository requests) {
        this.requests = requests;
    }

    /** 定时入口。手动执行（含测试）请直接调 {@link #runOnce()}，enabled 只控制是否自动跑。 */
    @Scheduled(cron = "${idempotency.cleanup.cron:0 15 4 * * *}")
    public void scheduledRun() {
        if (!enabled) return;
        try {
            int deleted = runOnce();
            if (deleted > 0) log.info("幂等请求记录清理 {} 行（保留期 {} 小时）", deleted, retentionHours);
        } catch (Exception e) {
            log.error("幂等请求记录清理失败，留待下一轮重试", e);
        }
    }

    /** R4-11：分批清理——每批独立短事务，避免单条全量 DELETE 长事务持间隙锁。 */
    public int runOnce() {
        int total = 0;
        while (true) {
            java.util.List<Long> ids = requests.findExpiredIds(LocalDateTime.now().minusHours(retentionHours),
                    org.springframework.data.domain.PageRequest.of(0, 1000));
            if (ids.isEmpty()) break;
            total += requests.deleteByIdIn(ids);
            if (ids.size() < 1000) break;
        }
        return total;
    }
}
