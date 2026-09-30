package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.StockInRequest;
import com.wms.dto.StockOutRequest;
import com.wms.harness.Harness;
import com.wms.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4-15：扫码出入库（StockController 的 @Idempotent 库存资金最敏感路径）service 层集成测试。
 * 同时回归 R4-05：batchNo 统一 normalizeBatch（trim），" B1 " 与 "B1" 不得分裂为两条库存行。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockScanIntegrationTest {

    @Autowired private InventoryService stock;
    @Autowired private InventoryRepository inventories;

    private List<com.wms.model.entity.Inventory> rows(String itemCode) {
        return inventories.findAllDetailed().stream()
                .filter(i -> i.getItem().getCode().equals(itemCode))
                .toList();
    }

    @Test
    void stockInCreatesInventoryAndOrderNo() {
        Harness.asAdmin(() -> {
            var result = stock.stockIn(new StockInRequest(
                    "ITEM-001", new BigDecimal("10"), new BigDecimal("12.50"), 1L, "A-01-01", null, "R4-15 入库"));
            assertNotNull(result, "入库应返回结果视图");
            BigDecimal total = rows("ITEM-001").stream().map(i -> i.getQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("110").compareTo(total), "播种 100 + 入库 10 = 110");
        });
    }

    @Test
    void stockOutRejectsWhenQuantityExceedsStock() {
        Harness.asAdmin(() -> stock.stockIn(new StockInRequest(
                "ITEM-001", new BigDecimal("5"), new BigDecimal("12.50"), 1L, "A-01-01", null, "R4-15 少量入库")));
        BusinessException ex = assertThrows(BusinessException.class, () -> Harness.asAdmin(() ->
                stock.stockOut(new StockOutRequest(
                        "ITEM-001", new BigDecimal("500"), new BigDecimal("20"), 1L, "A-01-01", null, "R4-15 超量出库"))));
        assertTrue(ex.getMessage().contains("库存不足") || ex.getMessage().contains("已被商城订单预占"), ex.getMessage());
    }

    /** R4-05 回归：批次带首尾空格入库后，按 trim 后批次出库必须命中同一行。 */
    @Test
    void batchNoIsTrimmedSoPaddedAndCleanBatchesAreSameRow() {
        Harness.asAdmin(() -> {
            stock.stockIn(new StockInRequest(
                    "ITEM-001", new BigDecimal("8"), new BigDecimal("12.50"), 1L, "A-01-01", " B1 ", "R4-05 带空格批次"));
            List<com.wms.model.entity.Inventory> b1Rows = rows("ITEM-001").stream()
                    .filter(i -> "B1".equals(i.getBatchNo()))
                    .toList();
            assertEquals(1, b1Rows.size(), "批次应统一 trim，不得出现 \" B1 \" 行");
            // 按 trim 后批次出库应命中该行（修复前会误报库存不足）
            var result = stock.stockOut(new StockOutRequest(
                    "ITEM-001", new BigDecimal("3"), new BigDecimal("20"), 1L, "A-01-01", "B1", "R4-05 trim 后出库"));
            assertNotNull(result, "出库应返回结果视图");
        });
    }
}
