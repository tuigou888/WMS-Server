package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.StockInRequest;
import com.wms.dto.StocktakeRequest;
import com.wms.harness.Harness;
import com.wms.model.entity.Warehouse;
import com.wms.repository.WarehouseRepository;
import com.wms.security.RolePermissions;
import com.wms.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 盘点单详情（GET /stocktakes/{id} → DocumentService.stocktakeDetail）的集成测试。
 * 此前无详情接口，小程序盘点录入/详情页加载即 404，移动端盘点闭环整体不可用。
 * 详情复用私有 stocktake(id)：findDetailedById + warehouseAccess.require，与审核/执行同一条权限路径。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StocktakeDetailIntegrationTest {

    @Autowired private DocumentService documents;
    @Autowired private InventoryService stock;
    @Autowired private WarehouseRepository warehouses;

    private Long createStocktake(Long warehouseId) {
        Map<String, Object> created = Harness.asAdmin(() ->
                documents.createStocktake(new StocktakeRequest(warehouseId, "详情测试", null, null, null)));
        return ((Number) created.get("id")).longValue();
    }

    /** 详情返回完整视图：单号/状态/仓库/明细行（库位、账面数量、实盘未录入为 null）。 */
    @Test
    void detailReturnsFullView() {
        // 测试 profile 播种主仓库 id=1（含初始库存），全仓盘点能生成明细行
        Long id = createStocktake(1L);

        Map<String, Object> detail = Harness.asAdmin(() -> documents.stocktakeDetail(id));
        assertNotNull(detail.get("stocktakeNo"));
        assertEquals("DRAFT", detail.get("status"));
        assertEquals(1L, ((Number) detail.get("warehouseId")).longValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) detail.get("lines");
        assertFalse(lines.isEmpty());
        Map<String, Object> line = lines.get(0);
        assertNotNull(line.get("itemCode"));
        // 无库位行的 locationCode 恒为空串（创建时归一），账面数量非空、实盘未录入
        assertNotNull(line.get("locationCode"));
        assertNotNull(line.get("bookQuantity"));
        assertNull(line.get("actualQuantity"));
    }

    /** 单据不存在：业务异常"盘点单不存在"，而非 500。 */
    @Test
    void detailRejectsMissingOrder() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> Harness.asAdmin(() -> documents.stocktakeDetail(999999L)));
        assertEquals("盘点单不存在", ex.getMessage());
    }

    /** 仓库受限用户（operator/WAREHOUSE，未分配该仓库）：读取即拒绝。 */
    @Test
    void detailRejectsWarehouseScopedUserWithoutAccess() {
        Warehouse repo = warehouses.save(new Warehouse("T-PD-REPO", "盘点详情测试仓"));
        Harness.asAdmin(() -> stock.stockIn(new StockInRequest(
                "ITEM-001", new BigDecimal("5"), new BigDecimal("10.00"), repo.getId(), "A-01-01", null, "构造盘点库存")));
        Long id = createStocktake(repo.getId());

        TokenService.Principal principal = new TokenService.Principal("operator", "WAREHOUSE", "库管", RolePermissions.forRole("WAREHOUSE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, RolePermissions.forRole("WAREHOUSE").stream().map(SimpleGrantedAuthority::new).toList()));
        try {
            assertThrows(AccessDeniedException.class, () -> documents.stocktakeDetail(id));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
