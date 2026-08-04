package com.wms.service;

import com.wms.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Map;

@Service
public class WechatService {

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.mock:false}")
    private boolean mock;

    private final Environment environment;

    public WechatService(Environment environment) {
        this.environment = environment;
    }

    private final RestClient restClient = RestClient.create();

    public record SessionResult(String openid, String sessionKey, Integer errcode, String errmsg) {}

    public String getOpenid(String code) {
        if (mock) {
            // mock 模式把 code 直接当 openid，仅允许 dev/test 环境使用；生产误开等于任何人可用任意 code 登录
            String[] profiles = environment.getActiveProfiles();
            boolean isDev = profiles.length == 0 || Arrays.stream(profiles).anyMatch(p -> p.equals("dev") || p.equals("test"));
            if (!isDev) {
                throw new BusinessException("生产环境禁止启用 wechat.mock，请配置真实的 WECHAT_APPID / WECHAT_SECRET");
            }
            return code;
        }
        if (appid.isBlank() || secret.isBlank()) {
            throw new BusinessException("微信小程序未配置：缺少 wechat.appid 或 wechat.secret");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";
        SessionResult result = restClient.get()
                .uri(url, appid, secret, code)
                .retrieve()
                .body(SessionResult.class);
        if (result == null || result.errcode() != null && result.errcode() != 0) {
            throw new BusinessException("微信登录失败: " + (result != null ? result.errmsg() : "无响应"));
        }
        return result.openid();
    }
}