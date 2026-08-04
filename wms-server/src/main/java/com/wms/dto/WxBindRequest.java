package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
public record WxBindRequest(@NotBlank(message="openid不能为空") String openid, @NotBlank(message="用户名不能为空") String username, @NotBlank(message="密码不能为空") String password) {}