package com.wms.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.wms.security.Permissions.*;

/**
 * RBAC 角色 → 权限矩阵（静态定义，与用户角色绑定）。
 * ADMIN 拥有全部权限；WAREHOUSE 拥有除审核/管理类之外的全部业务权限。
 */
public final class RolePermissions {
    private RolePermissions() {}

    private static final Set<String> ALL = Set.of(
            INVENTORY_READ, INVENTORY_WRITE,
            DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_EXECUTE, DOCUMENT_REVIEW,
            TRANSFER_READ, TRANSFER_WRITE, TRANSFER_EXECUTE, TRANSFER_REVIEW,
            STOCKTAKE_READ, STOCKTAKE_WRITE, STOCKTAKE_EXECUTE, STOCKTAKE_REVIEW,
            ADJUSTMENT_READ, ADJUSTMENT_WRITE, ADJUSTMENT_EXECUTE, ADJUSTMENT_REVIEW,
            PURCHASE_READ, PURCHASE_WRITE, PURCHASE_REVIEW,
            ITEM_READ, ITEM_WRITE, PARTNER_READ, PARTNER_WRITE,
            WAREHOUSE_MANAGE, USER_MANAGE, LOG_VIEW, REPORT_VIEW,
            QRCODE_READ, EXCEL_READ, EXCEL_WRITE, OCR_USE, LOCATION_READ);

    private static final Set<String> WAREHOUSE = Set.of(
            INVENTORY_READ, INVENTORY_WRITE,
            DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_EXECUTE,
            TRANSFER_READ, TRANSFER_WRITE, TRANSFER_EXECUTE,
            STOCKTAKE_READ, STOCKTAKE_WRITE, STOCKTAKE_EXECUTE,
            ADJUSTMENT_READ, ADJUSTMENT_WRITE, ADJUSTMENT_EXECUTE,
            PURCHASE_READ, PURCHASE_WRITE,
            ITEM_READ, ITEM_WRITE, PARTNER_READ, PARTNER_WRITE,
            REPORT_VIEW, QRCODE_READ, EXCEL_READ, EXCEL_WRITE, OCR_USE, LOCATION_READ);

    private static final Map<String, Set<String>> MATRIX = Map.of(
            "ADMIN", ALL, "WAREHOUSE", WAREHOUSE);

    public static Set<String> forRole(String role) {
        Set<String> permissions = role == null ? null : MATRIX.get(role);
        return permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
    }

    public static Set<String> all() { return new LinkedHashSet<>(ALL); }
}
