package com.wms.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：统一从 SecurityContextHolder 读取当前登录用户（Spring Security 鉴权后）。
 * 服务层通过 require(permission) 做权限校验，与控制器 @PreAuthorize 构成双保险。
 */
public final class SecurityUtils {
    private SecurityUtils() {}

    public static TokenService.Principal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TokenService.Principal p) return p;
        return null;
    }

    public static String username() {
        TokenService.Principal p = currentUser();
        return p == null ? "system" : p.username();
    }

    public static boolean hasPermission(String permission) {
        TokenService.Principal p = currentUser();
        return p != null && p.permissions().contains(permission);
    }

    public static void require(String permission) {
        if (!hasPermission(permission)) throw new AccessDeniedException("无权限执行该操作（需要权限：" + permission + "）");
    }
}
