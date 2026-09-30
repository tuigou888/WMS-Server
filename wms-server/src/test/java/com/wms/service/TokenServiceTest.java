package com.wms.service;

import com.wms.model.entity.UserAccount;
import com.wms.repository.UserAccountRepository;
import com.wms.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4-15：TokenService 会话生命周期测试（此前该类零测试覆盖）。
 * 覆盖：签发可解析、撤销后失效、revokeByUsername 撤销该用户全部会话。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenServiceTest {

    @Autowired private TokenService tokens;
    @Autowired private UserAccountRepository users;

    private UserAccount user(String username, String role) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setRole(role);
        u.setEnabled(true);
        u.setPassword("{noop}r4-test-password"); // password 列 NOT NULL
        // resolve 会回查 users 表校验存在与启用，必须持久化
        return users.save(u);
    }

    @Test
    void issuedTokenResolvesBack() {
        UserAccount u = user("r4-token-user", "ADMIN");
        String token = tokens.issue(u);
        assertNotNull(token);
        TokenService.Principal principal = tokens.resolve(token).orElse(null);
        assertNotNull(principal);
        assertEquals("r4-token-user", principal.username());
    }

    @Test
    void revokedTokenNoLongerResolves() {
        UserAccount u = user("r4-token-revoke", "ADMIN");
        String token = tokens.issue(u);
        assertTrue(tokens.resolve(token).isPresent());
        tokens.revoke(token);
        assertTrue(tokens.resolve(token).isEmpty());
    }

    @Test
    void revokeByUsernameRevokesAllSessionsOfUser() {
        UserAccount u = user("r4-token-all", "WAREHOUSE");
        String t1 = tokens.issue(u);
        String t2 = tokens.issue(u);
        assertTrue(tokens.resolve(t1).isPresent());
        assertTrue(tokens.resolve(t2).isPresent());
        tokens.revokeByUsername("r4-token-all");
        assertTrue(tokens.resolve(t1).isEmpty());
        assertTrue(tokens.resolve(t2).isEmpty());
    }
}
