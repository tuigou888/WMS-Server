package com.wms.dto;

import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(
        @NotBlank(message = "仓库编码不能为空") String code,
        @NotBlank(message = "仓库名称不能为空") String name,
        Boolean status
) {
}
