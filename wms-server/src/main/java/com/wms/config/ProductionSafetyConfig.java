package com.wms.config;
import org.springframework.beans.factory.InitializingBean; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Configuration; import org.springframework.context.annotation.Profile;
/** 生产 Profile 的不可降级安全阀，阻止环境变量意外重新开启开发能力。 */
@Configuration @Profile("prod") public class ProductionSafetyConfig implements InitializingBean {
 @Value("${wechat.mock:false}") private boolean wechatMock; @Value("${wechat.pay.mock:false}") private boolean wechatPayMock; @Value("${wechat.appid:}") private String wechatAppid; @Value("${wechat.secret:}") private String wechatSecret; @Value("${ocr.mock:false}") private boolean ocrMock; @Value("${spring.h2.console.enabled:false}") private boolean h2ConsoleEnabled; @Value("${spring.jpa.hibernate.ddl-auto:}") private String ddlAuto;
 @Override public void afterPropertiesSet(){if(wechatMock||wechatPayMock||ocrMock)throw new IllegalStateException("prod Profile 禁止启用微信登录、支付或 OCR mock");if(wechatAppid.isBlank()||wechatSecret.isBlank())throw new IllegalStateException("prod Profile 必须配置 WECHAT_APPID 和 WECHAT_SECRET");if(h2ConsoleEnabled)throw new IllegalStateException("prod Profile 禁止启用 H2 Console");if(!"validate".equals(ddlAuto))throw new IllegalStateException("prod Profile 的 spring.jpa.hibernate.ddl-auto 必须为 validate");}
}
