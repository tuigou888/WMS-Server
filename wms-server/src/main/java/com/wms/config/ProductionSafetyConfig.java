package com.wms.config;
import org.springframework.beans.factory.InitializingBean; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Configuration; import org.springframework.context.annotation.Profile;
/** 安全阀：除 dev/test 外的所有 Profile 一律生效（R4-10——原只绑 prod，staging 等自定义 Profile 可带 mock 上线）。 */
@Configuration public class ProductionSafetyConfig implements InitializingBean {
 @Value("${spring.profiles.active:}") private String activeProfiles;
 @Value("${wechat.mock:false}") private boolean wechatMock; @Value("${wechat.pay.mock:false}") private boolean wechatPayMock; @Value("${wechat.appid:}") private String wechatAppid; @Value("${wechat.secret:}") private String wechatSecret; @Value("${ocr.mock:false}") private boolean ocrMock; @Value("${spring.h2.console.enabled:false}") private boolean h2ConsoleEnabled; @Value("${spring.jpa.hibernate.ddl-auto:}") private String ddlAuto;
 @Override public void afterPropertiesSet(){
  String profiles = activeProfiles == null ? "" : activeProfiles;
  boolean dev = profiles.isEmpty() || profiles.contains("dev") || profiles.contains("test");
  if (dev) return; // 本地开发默认（无 profile=dev）不拦，见 R4-24 决策记录
  if(wechatMock||wechatPayMock||ocrMock)throw new IllegalStateException("非开发 Profile 禁止启用微信登录、支付或 OCR mock（当前 profiles="+profiles+"）");
  if(wechatAppid.isBlank()||wechatSecret.isBlank())throw new IllegalStateException("非开发 Profile 必须配置 WECHAT_APPID 和 WECHAT_SECRET（当前 profiles="+profiles+"）");
  if(h2ConsoleEnabled)throw new IllegalStateException("非开发 Profile 禁止启用 H2 Console");
  if(!"validate".equals(ddlAuto))throw new IllegalStateException("非开发 Profile 的 spring.jpa.hibernate.ddl-auto 必须为 validate");
 }
}
