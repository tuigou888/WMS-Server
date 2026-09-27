package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
public record WxLoginRequest(@NotBlank(message="code不能为空") String code, String app) {
  /** 兼容商城端/旧调用：不带 app 字段默认走商城端配置。 */
  public WxLoginRequest(String code) { this(code, null); }
}
