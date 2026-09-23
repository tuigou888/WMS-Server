package com.wms.model.entity.market;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 商城订单操作日志动作枚举。
 * CREATE / PAY / AUDIT / SHIP / COMPLETE / CANCEL / REFUND
 * 替代字符串字面量，提供编译期校验。
 */
public enum MarketOrderAction {
    CREATE,
    PAY,
    AUDIT,
    SHIP,
    COMPLETE,
    CANCEL,
    REFUND;

    @JsonValue
    public String jsonValue() { return name(); }

    @JsonCreator
    public static MarketOrderAction from(String value) {
        if (value == null) return null;
        return MarketOrderAction.valueOf(value.toUpperCase());
    }
}
