package com.wms.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class RolePermissionsTest {
 @Test void directScanIsExplicitlyAdminOnly(){assertTrue(RolePermissions.forRole("ADMIN").contains(Permissions.INVENTORY_SCAN));assertFalse(RolePermissions.forRole("WAREHOUSE").contains(Permissions.INVENTORY_SCAN));}
}
