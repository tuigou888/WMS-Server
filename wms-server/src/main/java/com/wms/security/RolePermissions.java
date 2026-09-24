package com.wms.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.wms.security.Permissions.*;

/**
 * RBAC 角色 → 权限矩阵（静态定义，与用户角色绑定）。
 * ADMIN 拥有全部权限；WAREHOUSE 拥有除审核/管理类之外的全部业务权限。
 * CUSTOMER（小程序买家）仅拥有商城浏览/下单/读自有订单权限。
 */
public final class RolePermissions {
    private RolePermissions() {}

    private static final Set<String> ALL = Set.of(
            INVENTORY_READ, INVENTORY_WRITE, INVENTORY_SCAN,
            DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_EXECUTE, DOCUMENT_REVIEW,
            TRANSFER_READ, TRANSFER_WRITE, TRANSFER_EXECUTE, TRANSFER_REVIEW,
            STOCKTAKE_READ, STOCKTAKE_WRITE, STOCKTAKE_EXECUTE, STOCKTAKE_REVIEW,
            ADJUSTMENT_READ, ADJUSTMENT_WRITE, ADJUSTMENT_EXECUTE, ADJUSTMENT_REVIEW,
            PURCHASE_READ, PURCHASE_WRITE, PURCHASE_REVIEW,
            ITEM_READ, ITEM_WRITE, PARTNER_READ, PARTNER_WRITE,
            WAREHOUSE_MANAGE, USER_MANAGE, LOG_VIEW, REPORT_VIEW,
            QRCODE_READ, EXCEL_READ, EXCEL_WRITE, OCR_USE, LOCATION_READ,
            MARKET_BUY, MARKET_READ, PRODUCT_READ, PRODUCT_WRITE,
            ORDER_READ, ORDER_REVIEW, ORDER_EXECUTE, CUSTOMER_READ, CUSTOMER_READ_ALL, CUSTOMER_WRITE);

    private static final Set<String> WAREHOUSE = Set.of(
            INVENTORY_READ, INVENTORY_WRITE,
            DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_EXECUTE,
            TRANSFER_READ, TRANSFER_WRITE, TRANSFER_EXECUTE,
            STOCKTAKE_READ, STOCKTAKE_WRITE, STOCKTAKE_EXECUTE,
            ADJUSTMENT_READ, ADJUSTMENT_WRITE, ADJUSTMENT_EXECUTE,
            PURCHASE_READ, PURCHASE_WRITE,
            ITEM_READ, ITEM_WRITE, PARTNER_READ, PARTNER_WRITE,
            REPORT_VIEW, QRCODE_READ, EXCEL_READ, EXCEL_WRITE, OCR_USE, LOCATION_READ,
            // 仓管可参与商城订单履约：发货/确认完成 + 商品在架查询
            PRODUCT_READ, ORDER_READ, ORDER_EXECUTE, CUSTOMER_READ);

    /** 小程序买家：仅可浏览/下单/读自有订单与收货人档案。 */
    private static final Set<String> CUSTOMER = Set.of(
            MARKET_BUY, MARKET_READ);

    /** 采购专员只能维护采购申请和基础档案，不具备审批或库存执行权。 */
    private static final Set<String> PROCUREMENT = Set.of(
            PURCHASE_READ, PURCHASE_WRITE, ITEM_READ, PARTNER_READ, INVENTORY_READ, LOCATION_READ, REPORT_VIEW);
    /** 审计员只读业务数据并执行审核，不具备制单、执行或用户管理权。 */
    private static final Set<String> AUDITOR = Set.of(
            INVENTORY_READ, DOCUMENT_READ, DOCUMENT_REVIEW, TRANSFER_READ, TRANSFER_REVIEW,
            STOCKTAKE_READ, STOCKTAKE_REVIEW, ADJUSTMENT_READ, ADJUSTMENT_REVIEW,
            PURCHASE_READ, PURCHASE_REVIEW, ITEM_READ, PARTNER_READ, LOCATION_READ, LOG_VIEW, REPORT_VIEW,
            PRODUCT_READ, ORDER_READ, CUSTOMER_READ, CUSTOMER_READ_ALL);
    /** 财务角色读取经营数据及商城订单，不具备履约和库存操作权。 */
    private static final Set<String> FINANCE = Set.of(REPORT_VIEW, INVENTORY_READ, DOCUMENT_READ, PURCHASE_READ, ORDER_READ, CUSTOMER_READ, CUSTOMER_READ_ALL);
    /** 客服维护客户档案、查看订单和商品；退款、审核、发货仍需专门职责。 */
    private static final Set<String> CUSTOMER_SERVICE = Set.of(PRODUCT_READ, ORDER_READ, CUSTOMER_READ, CUSTOMER_READ_ALL, CUSTOMER_WRITE);

    private static final Map<String, Set<String>> MATRIX = Map.ofEntries(
            Map.entry("ADMIN", ALL), Map.entry("WAREHOUSE", WAREHOUSE), Map.entry("PROCUREMENT", PROCUREMENT),
            Map.entry("AUDITOR", AUDITOR), Map.entry("FINANCE", FINANCE), Map.entry("CUSTOMER_SERVICE", CUSTOMER_SERVICE),
            Map.entry("CUSTOMER", CUSTOMER));

    public static Set<String> forRole(String role) {
        Set<String> permissions = role == null ? null : MATRIX.get(role);
        return permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
    }

    public static Set<String> all() { return new LinkedHashSet<>(ALL); }
    public static Map<String, Set<String>> roles(){Map<String,Set<String>> result=new java.util.TreeMap<>();MATRIX.forEach((role,permissions)->result.put(role,new LinkedHashSet<>(permissions)));return result;}
}
