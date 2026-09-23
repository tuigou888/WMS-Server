package com.wms.model.entity.market;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 商城订单状态枚举。
 * 状态机：PENDING（待付款）→ AUDITED（已审核/待发货）→ SHIPPED（已发货）→ COMPLETED（已完成）
 *        PENDING/AUDITED 可 → CANCELLED（取消并回滚库存）
 * 替代此前散落在 Service/Controller 中的字符串字面量，提供编译期校验。
 * @Enumerated(EnumType.STRING) 持久化时写入枚举 name()，DB 列值与历史数据兼容。
 */
public enum MarketOrderStatus {
    PENDING,
    AUDITED,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    REJECTED;

    @JsonValue
    public String jsonValue() { return name(); }

    /** 反序列化按 name 匹配，大小写不敏感容错。 */
    @JsonCreator
    public static MarketOrderStatus from(String value) {
        if (value == null) return null;
        return MarketOrderStatus.valueOf(value.toUpperCase());
    }
}
