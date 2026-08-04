package com.wms.controller;

import com.wms.dto.WxBindRequest;
import com.wms.dto.WxLoginRequest;
import com.wms.harness.Harness;
import com.wms.repository.UserAccountRepository;
import com.wms.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerWxTest {

    @Autowired private AuthController authController;
    @Autowired private UserAccountRepository users;
    @Autowired private TokenService tokens;

    private static HttpServletRequest req() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/wx-bind");
    }

    @Test
    void wxLoginMockModeCreatesOpenidBindingFlow() {
        // 测试 mock 模式下：code 直接作为 openid
        // 1. 未绑定 openid 时返回 needBind
        var loginResp = authController.wxLogin(new WxLoginRequest("test-openid-123"));
        assertEquals(200, loginResp.code());
        assertTrue((Boolean) loginResp.data().get("needBind"));
        assertEquals("test-openid-123", loginResp.data().get("openid"));

        // 2. 绑定账号密码后可登录
        var bindResp = authController.wxBind(new WxBindRequest("test-openid-123", "admin", "admin123"), req());
        assertEquals(200, bindResp.code());
        assertNotNull(bindResp.data().get("token"));

        // 3. 再次 wx-login 直接返回 token（已绑定）
        var loginResp2 = authController.wxLogin(new WxLoginRequest("test-openid-123"));
        assertEquals(200, loginResp2.code());
        assertNotNull(loginResp2.data().get("token"));
        assertEquals("admin", loginResp2.data().get("username"));

        // 4. 重复绑定同一 openid 报错
        assertThrows(com.wms.common.BusinessException.class,
                () -> authController.wxBind(new WxBindRequest("test-openid-123", "operator", "operator123"), req()));

        // 5. 绑定错误密码报错
        assertThrows(com.wms.common.BusinessException.class,
                () -> authController.wxBind(new WxBindRequest("another-openid", "admin", "wrongpass"), req()));

        // 6. 验证 openid 已持久化到数据库
        var user = users.findByOpenid("test-openid-123").orElseThrow();
        assertEquals("admin", user.getUsername());
    }

    @Test
    void wxLoginWithPreBoundOpenidDirectLogin() {
        // 先通过 asAdmin 预绑定 openid
        Harness.asAdmin(() -> {
            var user = users.findByUsername("admin").orElseThrow();
            user.setOpenid("prebound-openid");
            users.save(user);
        });

        // 直接 wx-login 应返回 token
        var loginResp = authController.wxLogin(new WxLoginRequest("prebound-openid"));
        assertEquals(200, loginResp.code());
        assertNotNull(loginResp.data().get("token"));
        assertEquals("admin", loginResp.data().get("username"));
    }

    @Test
    void wxBindRejectsDisabledUser() {
        // 使用独立用户避免干扰其他测试
        Harness.asAdmin(() -> {
            var u = new com.wms.model.entity.UserAccount();
            u.setUsername("test_disabled");
            u.setPassword(tokens.getClass().getDeclaredFields()[0].getName()); // 仅占位，实际不使用密码登录
            u.setRole("WAREHOUSE");
            u.setEnabled(false);
            u.setPassword("$2a$10$dummy"); // BCrypt 占位
            users.save(u);
        });

        assertThrows(com.wms.common.BusinessException.class,
                () -> authController.wxBind(new WxBindRequest("new-openid-disabled", "test_disabled", "anypass"), req()));
    }
}