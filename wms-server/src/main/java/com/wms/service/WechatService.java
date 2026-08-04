package com.wms.service;

import com.wms.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class WechatService {

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.mock:false}")
    private boolean mock;

    private final RestClient restClient = RestClient.create();

    public record SessionResult(String openid, String sessionKey, Integer errcode, String errmsg) {}

    public String getOpenid(String code) {
        if (mock) {
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