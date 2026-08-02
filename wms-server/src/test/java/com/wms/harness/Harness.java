package com.wms.harness;

import com.wms.security.TokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * 测试基座：在 SecurityContext 中注入 ADMIN Principal，
 * 让底层服务调用 ensureAdmin()/username() 时通过校验。
 */
public final class Harness {

    private Harness() {}

    public static void asAdmin(Runnable action) { asAdmin(() -> { action.run(); return null; }); }

    public static <T> T asAdmin(Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TokenService.Principal("admin", "ADMIN", "管理员"),
                        null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}