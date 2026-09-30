package com.wms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局 IP 限流（L2）：固定窗口计数，超阈值返回 429 + 统一响应壳。
 *
 * <p>由 SecurityConfig 以 {@code FilterRegistrationBean} 注册在 Servlet 链最前（早于 Spring Security），
 * 因此登录/微信支付回调等 permitAll 路径同样受保护——此前只有登录有 LoginRateLimiter（按 IP|用户名，DB 落库）。
 *
 * <p>IP 取 {@code X-Forwarded-For} 首段：nginx 反代后 remoteAddr 是反代容器 IP，全站会挤进同一个桶互相误伤；
 * 信任前提是公网入口都经反代覆盖 XFF（8088 默认只绑 127.0.0.1，直连伪造 XFF 的绕过面可控）。
 *
 * <p>内存实现：多实例各自计数（限流被弱化为"每实例阈值"，但不产生错误行为）；窗口惰性清理防计数表膨胀。
 * 需要精确跨实例限流时再考虑引入 Redis/Bucket4j。
 */
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    /** 计数表超过该键数时顺带清一轮过期窗口，防长期运行下无用 IP 条目积累。 */
    private static final int CLEANUP_THRESHOLD = 100_000;
    /** 滑动条目：[窗口起始毫秒, 窗口内请求数]。包私有仅为单测可拨动窗口起始。 */
    final Map<String, long[]> counters = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMillis;

    public GlobalRateLimitFilter(boolean enabled, int maxRequests, int windowSeconds) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) { // CORS 预检不占用配额
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        long[] window = counters.compute(clientIp(request), (k, w) ->
                w == null || now - w[0] >= windowMillis ? new long[]{now, 1} : new long[]{w[0], w[1] + 1});
        if (window[1] > maxRequests) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (window[0] + windowMillis - now) / 1000)));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return;
        }
        if (counters.size() > CLEANUP_THRESHOLD && now - lastCleanupAt >= CLEANUP_MIN_INTERVAL_MILLIS) {
            lastCleanupAt = now;
            counters.entrySet().removeIf(e -> now - e.getValue()[0] >= windowMillis);
        }
        chain.doFilter(request, response);
    }

    /** 清理节流：距上次清理不足 60s 时跳过，防止键数恒超阈值时每请求全表扫描（CPU 放大 DoS）。 */
    private static final long CLEANUP_MIN_INTERVAL_MILLIS = 60_000L;
    private volatile long lastCleanupAt = 0L;

    /**
     * R4-02：取 XFF <b>末段</b>——nginx `$proxy_add_x_forwarded_for` 是追加语义，客户端可伪造首段，
     * 而末段恒为最近一跳可信代理写入的真实来源；无 XFF（直连）时退回 remoteAddr。
     * 需配合 deploy/nginx-wms.conf 的覆盖式 X-Forwarded-For 传递。
     */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.lastIndexOf(',');
            String last = (comma >= 0 ? xff.substring(comma + 1) : xff).trim();
            if (!last.isEmpty()) return last;
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
