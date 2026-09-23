package com.wms.model.entity.market;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 商城支付状态枚举。
 * UNPAID → PAID / REFUNDED
 * 替代字符串字面量，提供编译期校验。
 */
public enum MarketPayStatus {
    UNPAID,
    PAID,
    REFUNDING,
    REFUNDED;

    @JsonValue
    public String jsonValue() { return name(); }

    @JsonCreator
    public static MarketPayStatus from(String value) {
        if (value == null) return null;
        return MarketPayStatus.valueOf(value.toUpperCase());
    }
}
