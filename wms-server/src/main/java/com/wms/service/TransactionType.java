package com.wms.service;

/**
 * 库存交易类型常量，替代魔法字符串。
 */
public final class TransactionType {

    private TransactionType() {}

    /** 直接入库（扫码入库、单据执行） */
    public static final String IN = "in";

    /** 直接出库（扫码出库、单据执行） */
    public static final String OUT = "out";

    /** 调拨出库 */
    public static final String TRANSFER_OUT = "transfer_out";

    /** 调拨入库 */
    public static final String TRANSFER_IN = "transfer_in";

    /** 盘点调整（盘盈） */
    public static final String ADJUST_IN = "adjust_in";

    /** 盘点调整（盘亏） */
    public static final String ADJUST_OUT = "adjust_out";
}