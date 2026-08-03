package com.wms.security;

/**
 * 系统权限常量（RBAC）。权限以字符串形式作为 Spring Security Authority 授予，
 * 同时被 @PreAuthorize 与服务层 SecurityUtils.require 共用。
 */
public final class Permissions {
    private Permissions() {}

    public static final String INVENTORY_READ = "inventory:read";
    public static final String INVENTORY_WRITE = "inventory:write";
    public static final String DOCUMENT_READ = "document:read";
    public static final String DOCUMENT_WRITE = "document:write";
    public static final String DOCUMENT_EXECUTE = "document:execute";
    public static final String DOCUMENT_REVIEW = "document:review";
    public static final String TRANSFER_READ = "transfer:read";
    public static final String TRANSFER_WRITE = "transfer:write";
    public static final String TRANSFER_EXECUTE = "transfer:execute";
    public static final String TRANSFER_REVIEW = "transfer:review";
    public static final String STOCKTAKE_READ = "stocktake:read";
    public static final String STOCKTAKE_WRITE = "stocktake:write";
    public static final String STOCKTAKE_EXECUTE = "stocktake:execute";
    public static final String STOCKTAKE_REVIEW = "stocktake:review";
    public static final String ADJUSTMENT_READ = "adjustment:read";
    public static final String ADJUSTMENT_WRITE = "adjustment:write";
    public static final String ADJUSTMENT_EXECUTE = "adjustment:execute";
    public static final String ADJUSTMENT_REVIEW = "adjustment:review";
    public static final String PURCHASE_READ = "purchase-request:read";
    public static final String PURCHASE_WRITE = "purchase-request:write";
    public static final String PURCHASE_REVIEW = "purchase-request:review";
    public static final String ITEM_READ = "item:read";
    public static final String ITEM_WRITE = "item:write";
    public static final String PARTNER_READ = "partner:read";
    public static final String PARTNER_WRITE = "partner:write";
    public static final String WAREHOUSE_MANAGE = "warehouse:manage";
    public static final String USER_MANAGE = "user:manage";
    public static final String LOG_VIEW = "log:view";
    public static final String REPORT_VIEW = "report:view";
    public static final String QRCODE_READ = "qrcode:read";
    public static final String EXCEL_READ = "excel:read";
    public static final String EXCEL_WRITE = "excel:write";
    public static final String OCR_USE = "ocr:use";
    public static final String LOCATION_READ = "location:read";
}
