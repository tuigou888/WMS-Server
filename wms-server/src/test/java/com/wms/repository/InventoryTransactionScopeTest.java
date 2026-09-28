package com.wms.repository;

import com.wms.model.entity.InventoryTransaction;
import com.wms.model.entity.Item;
import com.wms.model.entity.Warehouse;
import com.wms.repository.WarehouseRepository;
import com.wms.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5：inventory/transactions 的仓库过滤下推语义。
 * 此前是"全局取最近 N 条再内存 filter"——scope 用户被截断漏单；
 * 现在应拿到"自己仓库的最近 N 条"。复用 DemoDataConfig 播种的 ITEM-001。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryTransactionScopeTest {

    @Autowired private InventoryTransactionRepository transactions;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private ItemRepository items;

    /** 单仓 scope：只返回该仓库的流水；limit 语义 = 该仓库的最近 N 条（而非全局 N 条过滤后不足）。 */
    @Test
    void scopedQueryReturnsOnlyRequestedWarehouse() {
        Warehouse wh1 = warehouses.save(new Warehouse("T-M5-1", "M5测试仓1"));
        Warehouse wh2 = warehouses.save(new Warehouse("T-M5-2", "M5测试仓2"));
        Item item = items.findByCode("ITEM-001").orElseThrow();
        for (int i = 1; i <= 3; i++) transactions.save(txn(item, wh1, "M5-A" + i));
        for (int i = 1; i <= 2; i++) transactions.save(txn(item, wh2, "M5-B" + i));

        List<InventoryTransaction> scoped = transactions.findRecentDetailedByWarehouseIds(List.of(wh1.getId()), PageRequest.of(0, 10));
        assertEquals(3, scoped.size(), "单仓 scope 应返回该仓库全部 3 条");
        assertTrue(scoped.stream().allMatch(t -> t.getWarehouse().getId().equals(wh1.getId())), "不应混入其他仓库流水");

        // 关键语义：limit=2 时是 wh1 的最近 2 条；旧实现（全局 2 条再过滤）会只剩 0~2 条且漏掉 wh1 更早流水
        List<InventoryTransaction> limited = transactions.findRecentDetailedByWarehouseIds(List.of(wh1.getId()), PageRequest.of(0, 2));
        assertEquals(2, limited.size());
        assertTrue(limited.stream().allMatch(t -> t.getWarehouse().getId().equals(wh1.getId())));
    }

    /** 多仓 scope：返回并集，排序仍按时间倒序。 */
    @Test
    void scopedQuerySupportsMultipleWarehouses() {
        Warehouse wh1 = warehouses.save(new Warehouse("T-M5-3", "M5测试仓3"));
        Warehouse wh2 = warehouses.save(new Warehouse("T-M5-4", "M5测试仓4"));
        Item item = items.findByCode("ITEM-001").orElseThrow();
        transactions.save(txn(item, wh1, "M5-C1"));
        transactions.save(txn(item, wh2, "M5-D1"));

        assertEquals(2, transactions.findRecentDetailedByWarehouseIds(List.of(wh1.getId(), wh2.getId()), PageRequest.of(0, 10)).size());
    }

    /** 哨兵值（空分配传 -1）：不匹配任何真实仓库，返回空集而非全部数据。 */
    @Test
    void sentinelIdsReturnNothing() {
        Warehouse wh = warehouses.save(new Warehouse("T-M5-5", "M5测试仓5"));
        Item item = items.findByCode("ITEM-001").orElseThrow();
        transactions.save(txn(item, wh, "M5-E1"));

        assertTrue(transactions.findRecentDetailedByWarehouseIds(List.of(-1L), PageRequest.of(0, 10)).isEmpty(),
                "未分配仓库的用户（哨兵 -1）应看不到任何流水");
    }

    private InventoryTransaction txn(Item item, Warehouse wh, String refNo) {
        InventoryTransaction t = new InventoryTransaction();
        t.setItem(item); t.setWarehouse(wh); t.setTransactionType("adjustment"); t.setReferenceNo(refNo);
        t.setQuantity(BigDecimal.ONE); t.setUnitCost(BigDecimal.TEN); t.setTotalCostAmount(BigDecimal.TEN);
        t.setBalanceQuantity(BigDecimal.ONE); t.setBalanceAmount(BigDecimal.TEN); t.setAvgCostAfter(BigDecimal.TEN);
        return t;
    }
}
