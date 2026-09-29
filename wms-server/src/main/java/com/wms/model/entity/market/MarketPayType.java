package com.wms.model.entity.market;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 商城支付方式枚举。
 * PAY_ONLINE（在线支付/微信支付） / CASH_ON_DELIVERY（货到付款） / CREDIT（挂账）
 * 替代字符串字面量，提供编译期校验。
 */
public enum MarketPayType {
    PAY_ONLINE,
    CASH_ON_DELIVERY,
    CREDIT;

    @JsonValue
    public String jsonValue() { return name(); }

    @JsonCreator
    public static MarketPayType from(String value) {
        if (value == null) return null;
        try {
            return MarketPayType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 非法支付方式走 400 业务异常，而不是落进兜底 handler 变 500
            throw new com.wms.common.BusinessException("支付方式不合法：" + value);
        }
    }
}
