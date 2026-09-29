package com.wms.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class RolePermissionsTest {
 @Test void directScanIsExplicitlyAdminOnly(){assertTrue(RolePermissions.forRole("ADMIN").contains(Permissions.INVENTORY_SCAN));assertFalse(RolePermissions.forRole("WAREHOUSE").contains(Permissions.INVENTORY_SCAN));}
 /** market:credit 只能授 ADMIN——挂账单审核通过即记为已收款，开放给买家等于零支付拿货。 */
 @Test void marketCreditIsExplicitlyAdminOnly(){assertTrue(RolePermissions.forRole("ADMIN").contains(Permissions.MARKET_CREDIT));assertFalse(RolePermissions.forRole("WAREHOUSE").contains(Permissions.MARKET_CREDIT));assertFalse(RolePermissions.forRole("CUSTOMER").contains(Permissions.MARKET_CREDIT));assertFalse(RolePermissions.forRole("FINANCE").contains(Permissions.MARKET_CREDIT));}
}
