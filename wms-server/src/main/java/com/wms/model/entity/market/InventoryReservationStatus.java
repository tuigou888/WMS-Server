package com.wms.model.entity.market;
/** 商城订单库存预占状态：HELD 可售扣减，CONSUMED 已转正式出库，RELEASED/EXPIRED 不再占用库存。 */
public enum InventoryReservationStatus { HELD, CONSUMED, RELEASED, EXPIRED }
