package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
public record ReviewRequest(@NotBlank(message="审核动作不能为空") String action, String remark) {}
