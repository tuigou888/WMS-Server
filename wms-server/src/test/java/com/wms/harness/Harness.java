package com.wms.harness;

import com.wms.security.RolePermissions;
import com.wms.security.TokenService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 测试基座：在 SecurityContext 中注入 ADMIN Principal（含全部权限），
 * 让底层服务调用 SecurityUtils.require()/username() 时通过校验。
 * 同时注入带随机 Idempotency-Key 的请求属性，使 @Idempotent 切面在直连 Controller 调用下可用。
 */
public final class Harness {

    private Harness() {}

    public static void asAdmin(Runnable action) { asAdmin(() -> { action.run(); return null; }); }

    public static <T> T asAdmin(Supplier<T> action) {
        TokenService.Principal principal = new TokenService.Principal("admin", "ADMIN", "管理员", RolePermissions.forRole("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        RolePermissions.forRole("ADMIN").stream().map(SimpleGrantedAuthority::new).toList()));
        // 每次读取 Idempotency-Key 头都返回新值：同一测试块内多次同参调用不会命中幂等缓存
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override public String getHeader(String name) {
                if ("Idempotency-Key".equalsIgnoreCase(name)) return "test-" + UUID.randomUUID();
                return super.getHeader(name);
            }
        };
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return action.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.clearContext();
        }
    }
}