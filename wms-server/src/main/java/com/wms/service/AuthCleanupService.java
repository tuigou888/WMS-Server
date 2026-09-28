package com.wms.service;

import com.wms.repository.AuthSessionRepository;
import com.wms.repository.LoginAttemptRepository;
import com.wms.repository.WechatBindTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录相关临时表清理（与幂等记录清理同模式）：
 * <ul>
 *   <li>auth_login_attempts —— 限速计数只在「窗口过期被查到」或「登录成功」时惰性删除，
 *       既没成功也没再试的 key 会永远留下；窗口本身 10 分钟，超保留期（默认 24h，供排查）的行每日清理。</li>
 *   <li>wechat_bind_tickets —— 票据 TTL 5 分钟，peek/consume 只在碰到时惰性删除，
 *       从未被访问的过期票据需要显式清理。</li>
 *   <li>auth_sessions —— 过期会话原先由 TokenService.issue 每次登录顺带全表 DELETE（写放大），
 *       现归并到这里每日批量清理；resolve 碰到过期会话时仍即时删除。</li>
 * </ul>
 * 两表均为幂等删除，多实例同时跑无副作用。
 */
@Service
public class AuthCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupService.class);

    @Value("${auth.cleanup.enabled:true}") private boolean enabled;
    @Value("${auth.cleanup.login-attempt-retention-hours:24}") private int loginAttemptRetentionHours;

    private final LoginAttemptRepository attempts;
    private final WechatBindTicketRepository tickets;
    private final AuthSessionRepository sessions;

    public AuthCleanupService(LoginAttemptRepository attempts, WechatBindTicketRepository tickets, AuthSessionRepository sessions) {
        this.attempts = attempts;
        this.tickets = tickets;
        this.sessions = sessions;
    }

    /** 定时入口。手动执行（含测试）请直接调 {@link #runOnce()}，enabled 只控制是否自动跑。 */
    @Scheduled(cron = "${auth.cleanup.cron:0 30 4 * * *}")
    public void scheduledRun() {
        if (!enabled) return;
        try {
            Result r = runOnce();
            if (r.attemptsDeleted() > 0 || r.ticketsDeleted() > 0 || r.sessionsDeleted() > 0)
                log.info("登录临时表清理: 限速计数 {} 行（保留期 {} 小时），过期绑定票据 {} 行，过期会话 {} 行",
                        r.attemptsDeleted(), loginAttemptRetentionHours, r.ticketsDeleted(), r.sessionsDeleted());
        } catch (Exception e) {
            log.error("登录临时表清理失败，留待下一轮重试", e);
        }
    }

    /** 清理超保留期的限速计数、已过期绑定票据与已过期登录会话。 */
    @Transactional
    public Result runOnce() {
        int attemptsDeleted = attempts.deleteByWindowStartBefore(LocalDateTime.now().minusHours(loginAttemptRetentionHours));
        int ticketsDeleted = tickets.deleteExpiredBefore(LocalDateTime.now());
        long sessionsDeleted = sessions.deleteByExpiresAtBefore(LocalDateTime.now());
        return new Result(attemptsDeleted, ticketsDeleted, sessionsDeleted);
    }

    public record Result(int attemptsDeleted, int ticketsDeleted, long sessionsDeleted) {}
}
