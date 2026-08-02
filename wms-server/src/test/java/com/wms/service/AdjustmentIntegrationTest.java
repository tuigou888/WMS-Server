package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.AdjustmentRequest;
import com.wms.dto.DocumentRequest;
import com.wms.dto.ReviewRequest;
import com.wms.harness.Harness;
import com.wms.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报损/报溢 + 退货 + 反审 的业务集成测试。
 * 测试 profile 下 DemoData 会播种：主仓库(id=1) + 3 个物品 + 初始库存。
 */
@SpringBootTest
@ActiveProfiles("test")
class AdjustmentIntegrationTest {

    @Autowired private DocumentService documents;
    @Autowired private AdjustmentService adjustments;
    @Autowired private InventoryRepository inventories;

    private BigDecimal qty(String itemCode) {
        return inventories.findAllDetailed().stream()
                .filter(x -> x.getItem().getCode().equals(itemCode))
                .map(x -> x.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void lossAdjustmentReducesInventory() {
        Harness.asAdmin(() -> {
            BigDecimal before = qty("ITEM-001");
            Map<String, Object> created = adjustments.create(new AdjustmentRequest(
                    "LOSS", 1L, "破损", null,
                    List.of(new AdjustmentRequest.AdjustmentLineRequest("ITEM-001", "A-01-01", null, new BigDecimal("5")))));
            Long id = ((Number) created.get("id")).longValue();
            assertEquals("DRAFT", created.get("status"));

            adjustments.review(id, new ReviewRequest("APPROVE", null));
            adjustments.complete(id);

            assertEquals(before.subtract(new BigDecimal("5")), qty("ITEM-001"));
        });
    }

    @Test
    void returnInIncreasesInventory() {
        Harness.asAdmin(() -> {
            BigDecimal before = qty("ITEM-002");
            Map<String, Object> created = documents.createDocument(new DocumentRequest(
                    "RETURN_IN", null, 1L, null, "客户退货",
                    List.of(new DocumentRequest.DocumentLineRequest("ITEM-002", "A-01-02", new BigDecimal("10"), new BigDecimal("88"), null, null))));
            assertEquals("THI-", ((String) created.get("documentNo")).substring(0, 4));
            Long id = ((Number) created.get("id")).longValue();
            documents.reviewDocument(id, new ReviewRequest("APPROVE", null));
            documents.completeDocument(id);
            assertEquals(before.add(new BigDecimal("10")), qty("ITEM-002"));
        });
    }

    @Test
    void uncompleteRestoresInventory() {
        Harness.asAdmin(() -> {
            BigDecimal before = qty("ITEM-003");
            Map<String, Object> created = documents.createDocument(new DocumentRequest(
                    "RETURN_IN", null, 1L, null, "测试反审",
                    List.of(new DocumentRequest.DocumentLineRequest("ITEM-003", "A-01-03", new BigDecimal("4"), new BigDecimal("8.2"), null, null))));
            Long id = ((Number) created.get("id")).longValue();
            documents.reviewDocument(id, new ReviewRequest("APPROVE", null));
            documents.completeDocument(id);
            assertEquals(before.add(new BigDecimal("4")), qty("ITEM-003"));
            documents.uncompleteDocument(id); // 反审：库存回到 before
            assertEquals(before, qty("ITEM-003"));
        });
    }

    @Test
    void uncompleteOnNonCompletedThrows() {
        Harness.asAdmin(() -> {
            Map<String, Object> created = documents.createDocument(new DocumentRequest(
                    "IN", null, 1L, null, "草稿",
                    List.of(new DocumentRequest.DocumentLineRequest("ITEM-001", "A-01-01", new BigDecimal("2"), new BigDecimal("15"), null, null))));
            Long id = ((Number) created.get("id")).longValue();
            assertThrows(BusinessException.class, () -> documents.uncompleteDocument(id)); // DRAFT 不能反审
        });
    }
}