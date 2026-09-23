package com.wms.security;

import com.wms.model.entity.AuthSession;
import com.wms.model.entity.UserAccount;
import com.wms.repository.AuthSessionRepository;
import com.wms.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 持久化登录会话，使重启和多实例部署不会丢失认证状态。数据库只保存 token 摘要。 */
@Service
public class TokenService {
    public record Principal(String username, String role, String displayName, Set<String> permissions) {}

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;

    public TokenService(UserAccountRepository users, AuthSessionRepository sessions) { this.users = users; this.sessions = sessions; }

    @Transactional
    public String issue(UserAccount user) {
        cleanup();
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession();
        session.setTokenHash(hash(token));
        session.setUsername(user.getUsername());
        session.setExpiresAt(LocalDateTime.now().plus(Duration.ofHours(12)));
        sessions.save(session);
        return token;
    }

    @Transactional
    public Optional<Principal> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        AuthSession session = sessions.findByTokenHash(hash(token)).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (session == null || session.getExpiresAt().isBefore(now)) {
            if (session != null) sessions.delete(session);
            return Optional.empty();
        }
        UserAccount user = users.findByUsername(session.getUsername()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            sessions.delete(session);
            return Optional.empty();
        }
        return Optional.of(new Principal(user.getUsername(), user.getRole(), user.getDisplayName(), RolePermissions.forRole(user.getRole())));
    }

    @Transactional
    public void revoke(String token) { if (token != null && !token.isBlank()) sessions.deleteByTokenHash(hash(token)); }

    @Transactional
    public void revokeByUsername(String username) { if (username != null) sessions.deleteByUsername(username); }

    private void cleanup() { sessions.deleteByExpiresAtBefore(LocalDateTime.now()); }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
}
