package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.refund.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 微信支付 SDK 客户端配置（APIv3 / 普通商户 / 小程序支付）。
 * 仅当 wechat.pay.mock=false（默认）时初始化真实 SDK 客户端，避免未配置商户参数时启动失败。
 * mock=true 时本配置类不生效，WechatPayService 走本地模拟通路。
 *
 * 开发参数获取：https://pay.weixin.qq.com/doc/v3/merchant/4013070756
 * 回调验签/解密：用微信支付公钥（RSAPublicKeyConfig 同时实现 Config 与 NotificationConfig）
 */
@Configuration
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "false")
public class WechatPayConfig {
    private static final Logger log = LoggerFactory.getLogger(WechatPayConfig.class);

    @Value("${wechat.appid:}") private String appid;
    @Value("${wechat.pay.mch-id:}") private String mchId;
    @Value("${wechat.pay.mch-serial-no:}") private String mchSerialNo;
    @Value("${wechat.pay.api-v3-key:}") private String apiV3Key;
    @Value("${wechat.pay.private-key-path:}") private String privateKeyPath;
    @Value("${wechat.pay.wechat-pay-public-key-id:}") private String wechatPayPublicKeyId;
    @Value("${wechat.pay.wechat-pay-public-key-path:}") private String wechatPayPublicKeyPath;
    @Value("${wechat.pay.notify-url:}") private String notifyUrl;
    @Value("${wechat.pay.refund-notify-url:}") private String refundNotifyUrl;

    private final Environment environment;

    public WechatPayConfig(Environment environment) { this.environment = environment; }

    /** 真实模式启动前置校验：生产环境严禁缺参。 */
    private void validate() {
        String[] profiles = environment.getActiveProfiles();
        boolean isProd = Arrays.stream(profiles).anyMatch("prod"::equals);
        String missing = "";
        if (isBlank(appid)) missing += " wechat.appid";
        if (isBlank(mchId)) missing += " wechat.pay.mch-id";
        if (isBlank(mchSerialNo)) missing += " wechat.pay.mch-serial-no";
        if (isBlank(apiV3Key)) missing += " wechat.pay.api-v3-key";
        if (isBlank(privateKeyPath)) missing += " wechat.pay.private-key-path";
        if (isBlank(wechatPayPublicKeyId)) missing += " wechat.pay.wechat-pay-public-key-id";
        if (isBlank(wechatPayPublicKeyPath)) missing += " wechat.pay.wechat-pay-public-key-path";
        if (isBlank(notifyUrl)) missing += " wechat.pay.notify-url";
        if (isBlank(refundNotifyUrl) && (isBlank(notifyUrl) || !notifyUrl.endsWith("/notify"))) missing += " wechat.pay.refund-notify-url";
        if (!missing.isEmpty()) {
            throw new BusinessException("微信支付参数未配置（真实模式需补齐）：" + missing.trim()
                    + "；如需本地联调请设置 WECHAT_PAY_MOCK=true");
        }
        if (!Files.exists(Path.of(privateKeyPath))) {
            throw new BusinessException("微信支付商户私钥文件不存在：" + privateKeyPath);
        }
        if (!Files.exists(Path.of(wechatPayPublicKeyPath))) {
            throw new BusinessException("微信支付公钥文件不存在：" + wechatPayPublicKeyPath);
        }
        if (isProd && (notifyUrl.startsWith("http://") || notifyUrl.contains("localhost"))) {
            log.warn("生产环境微信支付回调地址疑似非 HTTPS 或指向本地：{}", notifyUrl);
        }
    }

    /** 一个配置同时用于 API 签名与回调验签/解密（RSAPublicKeyConfig 实现了两者）。 */
    @Bean
    public RSAPublicKeyConfig rsaPublicKeyConfig() {
        validate();
        log.info("初始化微信支付 SDK 客户端：mchId={}, notifyUrl={}", mchId, notifyUrl);
        return new RSAPublicKeyConfig.Builder()
                .merchantId(mchId)
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(mchSerialNo)
                .publicKeyFromPath(wechatPayPublicKeyPath)
                .publicKeyId(wechatPayPublicKeyId)
                .apiV3Key(apiV3Key)
                .build();
    }

    /** JSAPI/小程序下单、查单、关单（含调起支付签名生成）。 */
    @Bean
    public JsapiServiceExtension jsapiServiceExtension(RSAPublicKeyConfig config) {
        return new JsapiServiceExtension.Builder().config(config).build();
    }

    /** 退款申请。 */
    @Bean
    public RefundService refundService(RSAPublicKeyConfig config) {
        return new RefundService.Builder().config(config).build();
    }

    /** 支付/退款回调验签 + AES-GCM 解密。 */
    @Bean
    public NotificationParser notificationParser(RSAPublicKeyConfig config) {
        return new NotificationParser(config);
    }

    @Bean("wechatPayAppid")
    public String appid() { return appid; }
    @Bean("wechatPayMchId")
    public String mchId() { return mchId; }
    @Bean("wechatPayNotifyUrl")
    public String notifyUrl() { return notifyUrl; }
    @Bean("wechatPayRefundNotifyUrl")
    public String refundNotifyUrl() { return refundNotifyUrl; }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
