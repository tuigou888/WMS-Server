package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
public record WxLoginRequest(@NotBlank(message="code不能为空") String code) {}