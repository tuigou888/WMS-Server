package com.wms.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** L2：全局 IP 限流过滤器纯单元测试（不起 Spring 上下文；集成测试 profile 已关闭限流防集体 429）。 */
class GlobalRateLimitFilterTest {

    private MockHttpServletResponse run(GlobalRateLimitFilter filter, MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void disabledFilterPassesEverythingThrough() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(false, 2, 60);
        for (int i = 0; i < 5; i++) assertEquals(200, run(filter, new MockHttpServletRequest("GET", "/api/v1/items")).getStatus());
    }

    @Test
    void overLimitRequestsGet429WithEnvelope() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(true, 2, 60);
        assertEquals(200, run(filter, new MockHttpServletRequest("GET", "/x")).getStatus());
        assertEquals(200, run(filter, new MockHttpServletRequest("GET", "/x")).getStatus());
        MockHttpServletResponse blocked = run(filter, new MockHttpServletRequest("GET", "/x"));
        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("请求过于频繁"), "实际响应: " + blocked.getContentAsString());
        assertNotNull(blocked.getHeader("Retry-After"));
    }

    @Test
    void differentIpsCountSeparatelyViaXff() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(true, 1, 60);
        MockHttpServletRequest a = new MockHttpServletRequest("GET", "/x");
        a.addHeader("X-Forwarded-For", "1.1.1.1, 10.0.0.1");
        MockHttpServletRequest b = new MockHttpServletRequest("GET", "/x");
        b.addHeader("X-Forwarded-For", "2.2.2.2, 10.0.0.1");
        assertEquals(200, run(filter, a).getStatus());
        assertEquals(200, run(filter, b).getStatus());
        assertEquals(429, run(filter, a).getStatus(), "各 IP 独立计数，1.1.1.1 第二次应被限");
    }

    @Test
    void windowExpiryResetsCounter() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(true, 1, 60);
        assertEquals(200, run(filter, new MockHttpServletRequest("GET", "/x")).getStatus());
        assertEquals(429, run(filter, new MockHttpServletRequest("GET", "/x")).getStatus());
        // 把窗口起始拨回过去模拟窗口过期，计数应重置
        filter.counters.put("127.0.0.1", new long[]{System.currentTimeMillis() - 61_000, 5});
        assertEquals(200, run(filter, new MockHttpServletRequest("GET", "/x")).getStatus());
    }

    @Test
    void corsPreflightDoesNotConsumeQuota() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(true, 1, 60);
        for (int i = 0; i < 3; i++) assertEquals(200, run(filter, new MockHttpServletRequest("OPTIONS", "/x")).getStatus());
    }
}
