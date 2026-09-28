package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.TransferRequest;
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
 * L3：调拨单详情（GET /transfers/{id}）的 service 层集成测试。
 * 此前无详情接口，小程序只能从列表页 storage 缓存读详情，刷新或直达详情即失败。
 * 详情复用私有 transfer(id)：findDetailedById + 源/目标仓库双 require，与审核/执行同一条权限路径。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferDetailIntegrationTest {

    @Autowired private DocumentService documents;
    @Autowired private WarehouseRepository warehouses;

    private Map<String, Object> createTransfer(Warehouse source, Warehouse target) {
        return Harness.asAdmin(() -> documents.createTransfer(new TransferRequest(
                source.getId(), target.getId(), "L3测试调拨",
                List.of(new TransferRequest.TransferLineRequest("ITEM-001", "A-01-01", "B-01-01", null, new BigDecimal("2"))))));
    }

    /** 详情返回完整视图：单号/状态/双仓字段/明细行（itemCode、源/目标库位、数量）。 */
    @Test
    void detailReturnsFullView() {
        Warehouse source = warehouses.save(new Warehouse("T-L3-SRC", "L3源仓"));
        Warehouse target = warehouses.save(new Warehouse("T-L3-DST", "L3目标仓"));
        Long id = ((Number) createTransfer(source, target).get("id")).longValue();

        Map<String, Object> detail = Harness.asAdmin(() -> documents.transferDetail(id));
        assertNotNull(detail.get("transferNo"));
        assertEquals("DRAFT", detail.get("status"));
        assertEquals(source.getId().longValue(), ((Number) detail.get("sourceWarehouseId")).longValue());
        assertEquals("L3源仓", detail.get("sourceWarehouseName"));
        assertEquals(target.getId().longValue(), ((Number) detail.get("targetWarehouseId")).longValue());
        assertEquals("L3目标仓", detail.get("targetWarehouseName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) detail.get("lines");
        assertEquals(1, lines.size());
        assertEquals("ITEM-001", lines.get(0).get("itemCode"));
        assertEquals("A-01-01", lines.get(0).get("sourceLocationCode"));
        assertEquals("B-01-01", lines.get(0).get("targetLocationCode"));
        assertEquals(0, new BigDecimal("2").compareTo(new BigDecimal(lines.get(0).get("quantity").toString())));
    }

    /** 单据不存在：业务异常"调拨单不存在"，而非 500。 */
    @Test
    void detailRejectsMissingOrder() {
        BusinessException ex = assertThrows(BusinessException.class, () -> Harness.asAdmin(() -> documents.transferDetail(999999L)));
        assertEquals("调拨单不存在", ex.getMessage());
    }

    /** 仓库受限用户（operator/WAREHOUSE，未分配这两个测试仓库）：源/目标任一无权即拒绝。 */
    @Test
    void detailRejectsWarehouseScopedUserWithoutAccess() {
        Warehouse source = warehouses.save(new Warehouse("T-L3-S2", "L3源仓2"));
        Warehouse target = warehouses.save(new Warehouse("T-L3-T2", "L3目标仓2"));
        Long id = ((Number) createTransfer(source, target).get("id")).longValue();
        TokenService.Principal principal = new TokenService.Principal("operator", "WAREHOUSE", "库管", RolePermissions.forRole("WAREHOUSE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, RolePermissions.forRole("WAREHOUSE").stream().map(SimpleGrantedAuthority::new).toList()));
        try {
            assertThrows(AccessDeniedException.class, () -> documents.transferDetail(id));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
