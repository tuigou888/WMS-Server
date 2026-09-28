package com.wms.service;

import com.wms.model.entity.AuthSession;
import com.wms.model.entity.LoginAttempt;
import com.wms.model.entity.WechatBindTicket;
import com.wms.repository.AuthSessionRepository;
import com.wms.repository.LoginAttemptRepository;
import com.wms.repository.WechatBindTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录临时表清理：限速计数只删超保留期（默认 24h）的行；
 * 绑定票据只删已过期的行，TTL 内的必须保留（等待绑定消费）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthCleanupServiceTest {

    @Autowired private AuthCleanupService cleanup;
    @Autowired private LoginAttemptRepository attempts;
    @Autowired private WechatBindTicketRepository tickets;
    @Autowired private AuthSessionRepository sessions;

    @Test
    void deletesOnlyStaleAttemptsAndExpiredTickets() {
        LoginAttempt stale = attempt("cleanup-stale-key", LocalDateTime.now().minusHours(72));
        LoginAttempt fresh = attempt("cleanup-fresh-key", LocalDateTime.now().minusMinutes(5));
        WechatBindTicket expired = ticket("cleanup-expired-hash", LocalDateTime.now().minusMinutes(5));
        WechatBindTicket valid = ticket("cleanup-valid-hash", LocalDateTime.now().plusMinutes(5));
        attempts.saveAll(java.util.List.of(stale, fresh));
        tickets.saveAll(java.util.List.of(expired, valid));

        AuthCleanupService.Result r = cleanup.runOnce();

        assertEquals(1, r.attemptsDeleted());
        assertEquals(1, r.ticketsDeleted());
        assertTrue(attempts.findByRateKey("cleanup-stale-key").isEmpty(), "超保留期限速计数应被删除");
        assertTrue(attempts.findByRateKey("cleanup-fresh-key").isPresent(), "窗口内限速计数必须保留");
        assertTrue(tickets.findByTicketHash("cleanup-expired-hash").isEmpty(), "过期票据应被删除");
        assertTrue(tickets.findByTicketHash("cleanup-valid-hash").isPresent(), "TTL 内票据必须保留");
    }

    /** 过期会话由本服务兜底清理（原先 TokenService.issue 每次登录顺带全表 DELETE）；有效会话必须保留。 */
    @Test
    void deletesOnlyExpiredSessions() {
        sessions.save(session("cleanup-expired-session-hash", LocalDateTime.now().minusHours(1)));
        sessions.save(session("cleanup-active-session-hash", LocalDateTime.now().plusHours(11)));

        AuthCleanupService.Result r = cleanup.runOnce();

        assertEquals(1, r.sessionsDeleted());
        assertTrue(sessions.findByTokenHash("cleanup-expired-session-hash").isEmpty(), "过期会话应被清理");
        assertTrue(sessions.findByTokenHash("cleanup-active-session-hash").isPresent(), "有效会话必须保留");
    }

    @Test
    void nothingStaleDeletesNothing() {
        attempts.save(attempt("cleanup-none-key", LocalDateTime.now()));
        tickets.save(ticket("cleanup-valid-hash2", LocalDateTime.now().plusMinutes(5)));

        AuthCleanupService.Result r = cleanup.runOnce();

        assertEquals(0, r.attemptsDeleted());
        assertEquals(0, r.ticketsDeleted());
    }

    private LoginAttempt attempt(String key, LocalDateTime windowStart) {
        LoginAttempt a = new LoginAttempt();
        a.setRateKey(key);
        a.setAttemptCount(1);
        a.setWindowStart(windowStart);
        return a;
    }

    private WechatBindTicket ticket(String hash, LocalDateTime expiresAt) {
        WechatBindTicket t = new WechatBindTicket();
        t.setTicketHash(hash);
        t.setOpenid("cleanup-openid");
        t.setExpiresAt(expiresAt);
        return t;
    }

    private AuthSession session(String hash, LocalDateTime expiresAt) {
        AuthSession s = new AuthSession();
        s.setTokenHash(hash);
        s.setUsername("cleanup-user");
        s.setExpiresAt(expiresAt);
        return s;
    }
}
