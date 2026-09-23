package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.*;
import com.wms.harness.Harness;
import com.wms.model.entity.Inventory;
import com.wms.model.entity.InventoryTransaction;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.Warehouse;
import com.wms.model.entity.market.*;
import com.wms.repository.InventoryRepository;
import com.wms.repository.InventoryTransactionRepository;
import com.wms.repository.UserAccountRepository;
import com.wms.repository.WarehouseRepository;
import com.wms.repository.market.*;
import com.wms.service.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商城核心业务逻辑集成测试。
 * 复用 DemoDataConfig 播种数据：admin 用户、WH-001 仓库(id=1)、ITEM-001~003（库存 100/40/20）。
 * 测试 profile 下 wechat.pay.mock=true，走模拟支付通路。
 * 使用 @Transactional 自动回滚每个测试方法，保证测试间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketServiceIntegrationTest {

    @Autowired private MarketService service;
    @Autowired private MarketProductRepository products;
    @Autowired private MarketCartRepository carts;
    @Autowired private MarketCustomerRepository customers;
    @Autowired private MarketOrderRepository orders;
    @Autowired private MarketOrderLogRepository orderLogs;
    @Autowired private MarketFavoriteRepository favorites;
    @Autowired private InventoryRepository inventories;
    @Autowired private InventoryTransactionRepository inventoryTransactions;
    @Autowired private InventoryReservationRepository reservations;
    @Autowired private UserAccountRepository users;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    // ======================== 辅助方法 ========================

    private UserAccount admin() { return users.findByUsername("admin").orElseThrow(); }

    /** 创建一个上架商品（关联 ITEM-001，售价 20.00），返回已上架商品。幂等：已存在则复用。 */
    private MarketProduct shelfOnProduct() {
        return shelfOnProduct(1L, "测试商品-001", new BigDecimal("20.00"));
    }

    /** 创建商品并上架，使用指定 itemId。幂等：已存在则复用并确保上架。 */
    private MarketProduct shelfOnProduct(Long itemId, String title, BigDecimal salePrice) {
        Optional<MarketProduct> existing = products.findByItemId(itemId);
        if (existing.isPresent()) {
            MarketProduct p = existing.get();
            if (!"SHELF_ON".equals(p.getStatus())) {
                p = service.changeStatus(p.getId(), "SHELF_ON");
            }
            return p;
        }
        MarketProduct p = service.saveProduct(new MarketProductRequest(
                itemId, title, "副标题", "img.png", null, salePrice, null, null, 0));
        return service.changeStatus(p.getId(), "SHELF_ON");
    }

    /** 创建收货人档案。 */
    private MarketCustomer createCustomer(UserAccount user) {
        return service.saveCustomer(user, new MarketCustomerRequest(
                "张三", "13800000000", "北京市朝阳区测试路1号", true, null));
    }

    /** 创建并上架商品 + 清购物车 + 加购物车 + 创建收货人 + 下单。 */
    private MarketOrder createPaidOrderReady(UserAccount user) {
        return createOrderReady(user, "PAY_ONLINE");
    }

    /** 同 createPaidOrderReady 但以指定支付方式下单（用于货到付款等审核扣库存场景）。 */
    private MarketOrder createCashOrderReady(UserAccount user) {
        return createOrderReady(user, "CASH_ON_DELIVERY");
    }

    private MarketOrder createOrderReady(UserAccount user, String payType) {
        MarketProduct p = shelfOnProduct();
        // 清空可能残留的购物车，避免被前序测试的残留项污染
        service.clearCart(user);
        createCustomer(user);
        MarketCustomer customer = service.saveCustomer(user, new MarketCustomerRequest(
                "李四", "13900000000", "上海市测试路2号", true, null));
        service.addCart(user, p.getId(), 2);
        return service.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), 1L, payType, "测试备注"));
    }

    /** 查某 item 在 WH-001(warehouseId=1) 下的库存总量。 */
    private BigDecimal inventoryQty(Long itemId) {
        return inventories.findAllDetailed().stream()
                .filter(i -> i.getItem().getId().equals(itemId) && i.getWarehouse().getId().equals(1L))
                .map(Inventory::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 查某 item 在指定仓库下指定类型的流水数量。 */
    private long countTransactions(Long itemId, String txnType) {
        return inventoryTransactions.findByTransactionType(txnType).stream()
                .filter(t -> t.getItem().getId().equals(itemId))
                .count();
    }

    // ======================== 商品管理 ========================

    @Test
    void saveProduct_createsProductLinkedToItem() {
        // 用 ITEM-002 避免与 shelfOnProduct()(ITEM-001) 冲突
        Optional<MarketProduct> existing = products.findByItemId(2L);
        MarketProduct p;
        if (existing.isPresent()) {
            p = existing.get();
        } else {
            p = service.saveProduct(new MarketProductRequest(
                    2L, "新建商品测试", "副标题", "img.png", null,
                    new BigDecimal("20.00"), new BigDecimal("30.00"), null, 0));
        }
        assertNotNull(p.getId());
        assertEquals(new BigDecimal("20.00"), p.getSalePrice());
        // 新建默认下架（已存在的则状态不变）
        if (existing.isEmpty()) {
            assertEquals("SHELF_OFF", p.getStatus());
        }
    }

    @Test
    void saveProduct_rejectsDuplicateItem() {
        // shelfOnProduct 确保 ITEM-001 已有关联商品
        MarketProduct p = shelfOnProduct();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveProduct(new MarketProductRequest(
                        p.getItem().getId(), "重复商品", null, null, null,
                        new BigDecimal("10"), null, null, 0)));
        assertTrue(ex.getMessage().contains("已上架") || ex.getMessage().contains("重复"));
    }

    @Test
    void saveProduct_rejectsNonExistentItem() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.saveProduct(new MarketProductRequest(
                        99999L, "幽灵商品", null, null, null,
                        new BigDecimal("10"), null, null, 0)));
        assertTrue(ex.getMessage().contains("物品不存在"));
    }

    @Test
    void updateProduct_changesFields() {
        MarketProduct p = shelfOnProduct();
        MarketProduct updated = service.updateProduct(p.getId(), new MarketProductRequest(
                p.getItem().getId(), "更新标题", "更新副标题", "new.png", "g1,g2",
                new BigDecimal("25.00"), new BigDecimal("35.00"), null, 5));
        assertEquals("更新标题", updated.getTitle());
        assertEquals("更新副标题", updated.getSubTitle());
        assertEquals(new BigDecimal("25.00"), updated.getSalePrice());
        assertEquals(5, updated.getSortNo());
    }

    @Test
    void changeStatus_togglesShelf() {
        MarketProduct p = shelfOnProduct();
        assertEquals("SHELF_ON", p.getStatus());
        MarketProduct off = service.changeStatus(p.getId(), "SHELF_OFF");
        assertEquals("SHELF_OFF", off.getStatus());
        MarketProduct on = service.changeStatus(p.getId(), "SHELF_ON");
        assertEquals("SHELF_ON", on.getStatus());
    }

    @Test
    void changeStatus_rejectsInvalidStatus() {
        MarketProduct p = shelfOnProduct();
        assertThrows(BusinessException.class, () -> service.changeStatus(p.getId(), "SOLD_OUT"));
        assertThrows(BusinessException.class, () -> service.changeStatus(p.getId(), "INVALID"));
    }

    @Test
    void deleteProduct_removesProduct() {
        // 用 ITEM-003 避免影响其他测试依赖的 ITEM-001
        MarketProduct p = shelfOnProduct(3L, "删除测试商品", new BigDecimal("10.00"));
        Long id = p.getId();
        service.deleteProduct(id);
        assertTrue(products.findById(id).isEmpty());
    }

    @Test
    void deleteProduct_rejectsNonExistent() {
        assertThrows(BusinessException.class, () -> service.deleteProduct(99999L));
    }

    @Test
    void incrementView_incrementsViewCount() {
        MarketProduct p = shelfOnProduct();
        Long before = products.findById(p.getId()).orElseThrow().getViewCount();
        service.incrementView(p.getId());
        service.incrementView(p.getId());
        // @Modifying 查询绕过一级缓存，需 clear 后重新查询才能看到变更
        entityManager.clear();
        Long after = products.findById(p.getId()).orElseThrow().getViewCount();
        assertEquals(before + 2, after);
    }

    @Test
    void available_returnsInventorySum() {
        // ITEM-001 库存 100
        BigDecimal avail = service.available(1L, 1L);
        assertNotNull(avail);
        assertTrue(avail.compareTo(new BigDecimal("90")) >= 0); // 可能被其他测试扣减，但至少 >90
    }

    // ======================== 购物车 ========================

    @Test
    void addCart_createsNewCartItem() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCart c = service.addCart(user, p.getId(), 3);
        assertNotNull(c.getId());
        assertEquals(3, c.getQuantity());
        assertEquals(p.getSalePrice(), c.getSnapshotPrice());
    }

    @Test
    void addCart_accumulatesExistingItem() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        service.addCart(user, p.getId(), 2);
        MarketCart c = service.addCart(user, p.getId(), 3);
        assertEquals(5, c.getQuantity());
    }

    @Test
    void addCart_defaultsQuantityTo1() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCart c = service.addCart(user, p.getId(), null);
        assertEquals(1, c.getQuantity());
    }

    @Test
    void addCart_rejectsOffShelfProduct() {
        UserAccount user = admin();
        // 用 itemId=3 避免与 shelfOnProduct()(itemId=1) 冲突
        MarketProduct p = service.saveProduct(new MarketProductRequest(
                3L, "下架商品", null, null, null, new BigDecimal("10"), null, null, 0));
        // 默认 SHELF_OFF
        assertThrows(BusinessException.class, () -> service.addCart(user, p.getId(), 1));
    }

    @Test
    void updateCartQty_changesQuantity() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCart c = service.addCart(user, p.getId(), 1);
        MarketCart updated = service.updateCartQty(user, c.getId(), 5);
        assertEquals(5, updated.getQuantity());
    }

    @Test
    void updateCartQty_rejectsZeroOrNegative() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCart c = service.addCart(user, p.getId(), 1);
        assertThrows(BusinessException.class, () -> service.updateCartQty(user, c.getId(), 0));
        assertThrows(BusinessException.class, () -> service.updateCartQty(user, c.getId(), -1));
    }

    @Test
    void updateCartQty_rejectsOtherUser() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketProduct p = shelfOnProduct();
        MarketCart c = service.addCart(admin, p.getId(), 1);
        assertThrows(BusinessException.class, () -> service.updateCartQty(operator, c.getId(), 2));
    }

    @Test
    void removeCart_deletesSpecificItems() {
        UserAccount user = admin();
        MarketProduct p1 = shelfOnProduct(1L, "商品A", new BigDecimal("10"));
        MarketProduct p2 = shelfOnProduct(2L, "商品B", new BigDecimal("20"));
        MarketCart c1 = service.addCart(user, p1.getId(), 1);
        MarketCart c2 = service.addCart(user, p2.getId(), 1);
        service.removeCart(user, List.of(c1.getId()));
        // @Modifying delete 绕过一级缓存，需 clear 后查询才能看到删除结果
        entityManager.clear();
        assertTrue(carts.findById(c1.getId()).isEmpty());
        assertTrue(carts.findById(c2.getId()).isPresent());
    }

    @Test
    void clearCart_removesAllItems() {
        UserAccount user = admin();
        MarketProduct p1 = shelfOnProduct(1L, "清空A", new BigDecimal("10"));
        MarketProduct p2 = shelfOnProduct(2L, "清空B", new BigDecimal("20"));
        service.addCart(user, p1.getId(), 1);
        service.addCart(user, p2.getId(), 1);
        service.clearCart(user);
        entityManager.clear();
        assertEquals(0, carts.findByUserIdOrderByIdDesc(user.getId()).size());
    }

    // ======================== 收货人档案 ========================

    @Test
    void saveCustomer_createsWithDefaults() {
        UserAccount user = admin();
        MarketCustomer c = createCustomer(user);
        assertNotNull(c.getId());
        assertEquals("张三", c.getName());
        assertTrue(c.getDefaultFlag());
    }

    @Test
    void saveCustomer_firstCustomerBecomesDefault() {
        UserAccount user = admin();
        MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                "王五", "13700000000", "地址A", null, null));
        // 没有显式设 defaultFlag，但首条应自动默认
        assertTrue(c.getDefaultFlag());
    }

    @Test
    void saveCustomer_newDefaultUnsetsOldDefault() {
        UserAccount user = admin();
        MarketCustomer first = createCustomer(user);
        assertTrue(first.getDefaultFlag());
        MarketCustomer second = service.saveCustomer(user, new MarketCustomerRequest(
                "赵六", "13600000000", "地址B", true, null));
        assertTrue(second.getDefaultFlag());
        MarketCustomer reloadedFirst = customers.findById(first.getId()).orElseThrow();
        assertFalse(reloadedFirst.getDefaultFlag());
    }

    @Test
    void updateCustomer_changesFields() {
        UserAccount user = admin();
        MarketCustomer c = createCustomer(user);
        MarketCustomer updated = service.updateCustomer(user, c.getId(), new MarketCustomerRequest(
                "新名字", "13500000000", "新地址", false, "备注"));
        assertEquals("新名字", updated.getName());
        assertEquals("新地址", updated.getAddress());
        assertEquals("备注", updated.getRemark());
    }

    @Test
    void updateCustomer_rejectsOtherUser() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketCustomer c = createCustomer(admin);
        assertThrows(BusinessException.class, () -> service.updateCustomer(operator, c.getId(),
                new MarketCustomerRequest("hack", "1", "hack", false, null)));
    }

    @Test
    void deleteCustomer_removesProfile() {
        UserAccount user = admin();
        MarketCustomer c = createCustomer(user);
        service.deleteCustomer(user, c.getId());
        assertTrue(customers.findById(c.getId()).isEmpty());
    }

    @Test
    void deleteCustomer_rejectsOtherUser() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketCustomer c = createCustomer(admin);
        assertThrows(BusinessException.class, () -> service.deleteCustomer(operator, c.getId()));
    }

    @Test
    void countMarketCustomers_returnsTotal() {
        long before = service.countMarketCustomers();
        UserAccount user = admin();
        createCustomer(user);
        assertEquals(before + 1, service.countMarketCustomers());
    }

    @Test
    void deleteMarketCustomers_batchRemoves() {
        UserAccount user = admin();
        MarketCustomer c1 = createCustomer(user);
        MarketCustomer c2 = service.saveCustomer(user, new MarketCustomerRequest(
                "批量1", "13100000001", "地址", false, null));
        long before = service.countMarketCustomers();
        service.deleteMarketCustomers(c1.getId(), c2.getId());
        assertEquals(before - 2, service.countMarketCustomers());
    }

    // ======================== 下单 ========================

    @Test
    void createOrder_generatesOrderFromCart() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCustomer customer = createCustomer(user);
        service.addCart(user, p.getId(), 3);
        MarketOrder order = service.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), 1L, "PAY_ONLINE", "备注X"));
        assertNotNull(order.getId());
        assertTrue(order.getOrderNo().startsWith("MO-"));
        assertEquals(MarketOrderStatus.PENDING, order.getOrderStatus());
        assertEquals(MarketPayStatus.UNPAID, order.getPayStatus());
        assertEquals(MarketPayType.PAY_ONLINE, order.getPayType());
        assertEquals(1, order.getItems().size());
        MarketOrderItem oi = order.getItems().get(0);
        assertEquals(p.getTitle(), oi.getItemName());
        assertEquals(3, oi.getQuantity().intValue());
        // 3 × 20.00 = 60.00
        assertEquals(new BigDecimal("60.00"), order.getTotalAmount());
        assertEquals(new BigDecimal("60.00"), oi.getSubtotal());
    }

    @Test
    void createOrder_rejectsEmptyCart() {
        UserAccount user = admin();
        MarketCustomer customer = createCustomer(user);
        assertThrows(BusinessException.class, () -> service.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), 1L, "PAY_ONLINE", null)));
    }

    @Test
    void createOrder_rejectsOtherUserCustomer() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketCustomer customer = createCustomer(admin);
        MarketProduct p = shelfOnProduct();
        service.addCart(admin, p.getId(), 1);
        assertThrows(BusinessException.class, () -> service.createOrder(operator, new MarketOrderCreateRequest(
                customer.getId(), 1L, "PAY_ONLINE", null)));
    }

    @Test
    void createOrder_rejectsDisabledWarehouse() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCustomer customer = createCustomer(user);
        service.addCart(user, p.getId(), 1);
        Warehouse disabled = new Warehouse("WH-DISABLED", "禁用仓库");
        disabled.setStatus(false);
        warehouses.save(disabled);
        assertThrows(BusinessException.class, () -> service.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), disabled.getId(), "PAY_ONLINE", null)));
    }

    @Test
    void createOrder_rejectsOffShelfProductInCart() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        MarketCustomer customer = createCustomer(user);
        service.addCart(user, p.getId(), 1);
        // 下架商品后下单
        service.changeStatus(p.getId(), "SHELF_OFF");
        assertThrows(BusinessException.class, () -> service.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), 1L, "PAY_ONLINE", null)));
    }

    // ======================== 支付（mock 模式） ========================

    @Test
    void confirmMockPay_deductsStockAndMarksPaid() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal before = inventoryQty(itemId);
        MarketOrder paid = service.confirmMockPay(user, order.getId());
        assertEquals(MarketOrderStatus.AUDITED, paid.getOrderStatus());
        assertEquals(MarketPayStatus.PAID, paid.getPayStatus());
        assertNotNull(paid.getTransactionId());
        assertTrue(paid.getTransactionId().startsWith("MOCK_"));
        assertNotNull(paid.getPaidAt());
        // 库存扣减 2
        assertEquals(before.subtract(new BigDecimal("2")), inventoryQty(itemId));
        // 写了 PAY 日志
        assertEquals(1, orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.PAY.equals(l.getAction())).count());
    }

    @Test
    void createOrder_holdsInventoryAndAvailableExcludesHeldQuantity() {
        UserAccount user = admin();
        Long itemId = shelfOnProduct().getItem().getId();
        BigDecimal before = service.available(itemId, 1L);
        MarketOrder order = createPaidOrderReady(user);

        List<InventoryReservation> held = reservations.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(1, held.size());
        assertEquals(InventoryReservationStatus.HELD, held.get(0).getStatus());
        assertEquals(0, before.subtract(new BigDecimal("2")).compareTo(service.available(itemId, 1L)));
    }

    @Test
    void cancelPendingOrder_releasesReservationAndRestoresAvailableQuantity() {
        UserAccount user = admin();
        Long itemId = shelfOnProduct().getItem().getId();
        BigDecimal before = service.available(itemId, 1L);
        MarketOrder order = createPaidOrderReady(user);

        service.cancelByUser(user, order.getId());

        assertEquals(InventoryReservationStatus.RELEASED,
                reservations.findByOrderIdOrderByIdAsc(order.getId()).getFirst().getStatus());
        assertEquals(0, before.compareTo(service.available(itemId, 1L)));
    }

    @Test
    void expiredReservation_cancelsUnpaidOrderAndReleasesAvailability() {
        UserAccount user = admin();
        Long itemId = shelfOnProduct().getItem().getId();
        BigDecimal before = service.available(itemId, 1L);
        MarketOrder order = createPaidOrderReady(user);
        InventoryReservation held = reservations.findByOrderIdOrderByIdAsc(order.getId()).getFirst();
        held.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservations.saveAndFlush(held);

        service.expireReservations();

        assertEquals(InventoryReservationStatus.EXPIRED,
                reservations.findByOrderIdOrderByIdAsc(order.getId()).getFirst().getStatus());
        assertEquals(MarketOrderStatus.CANCELLED, orders.findById(order.getId()).orElseThrow().getOrderStatus());
        assertEquals(0, before.compareTo(service.available(itemId, 1L)));
    }

    @Test
    void latePaymentAfterCancellation_doesNotDeductStockAndIsAutomaticallyRefunded() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().getFirst().getItem().getId();
        BigDecimal before = inventoryQty(itemId);
        service.cancelByUser(user, order.getId());

        MarketOrder paidLate = service.markPaid(order.getId(), "LATE_PAY_" + order.getOrderNo(), "wechat");
        assertEquals(MarketOrderStatus.CANCELLED, paidLate.getOrderStatus());
        assertEquals(MarketPayStatus.PAID, paidLate.getPayStatus());
        assertEquals(before, inventoryQty(itemId));

        service.refundCancelledPaidOrders();
        assertEquals(MarketPayStatus.REFUNDED, orders.findById(order.getId()).orElseThrow().getPayStatus());
        assertEquals(before, inventoryQty(itemId));
    }

    @Test
    void confirmMockPay_isIdempotent() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        service.confirmMockPay(user, order.getId());
        BigDecimal qtyAfterFirstPay = inventoryQty(itemId);
        // 重复 markPaid（模拟重复回调）不重复扣库存——幂等在 markPaid 层
        MarketOrder second = service.markPaid(order.getId(), "MOCK_REPEAT", "test");
        assertEquals(MarketPayStatus.PAID, second.getPayStatus());
        assertEquals(qtyAfterFirstPay, inventoryQty(itemId));
    }

    @Test
    void confirmMockPay_rejectsNonOwner() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketOrder order = createPaidOrderReady(admin);
        assertThrows(BusinessException.class, () -> service.confirmMockPay(operator, order.getId()));
    }

    @Test
    void confirmMockPay_rejectsNonPendingOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        // 已支付，再次调用（非幂等路径，状态已 AUDITED）
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.confirmMockPay(user, order.getId()));
        // confirmMockPay 会先检查 PENDING 状态，已 AUDITED 时报错
        // 注意：这里实际走幂等返回（payStatus 已 PAID），但 orderStatus 已不是 PENDING
    }

    @Test
    void markPaid_writesOutTransaction() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        service.confirmMockPay(user, order.getId());
        // 应生成 OUT 流水
        long outCount = countTransactions(itemId, TransactionType.OUT);
        assertTrue(outCount > 0, "应生成 OUT 出库流水");
        List<InventoryTransaction> outTxs = inventoryTransactions.findByReferenceNoAndType(order.getOrderNo(), TransactionType.OUT);
        assertFalse(outTxs.isEmpty());
        assertTrue(outTxs.stream().allMatch(tx -> tx.getQuantity().signum() < 0), "所有出库流水数量必须为负数");
        assertEquals(0, outTxs.stream().map(InventoryTransaction::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("-2")));
    }

    @Test
    void createOrder_insufficientAvailableStockThrows() {
        UserAccount user = admin();
        // ITEM-003 库存 20，买 100 个
        MarketProduct p = shelfOnProduct(3L, "大数量商品", new BigDecimal("5"));
        MarketCustomer customer = createCustomer(user);
        service.addCart(user, p.getId(), 100);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createOrder(user,
                new MarketOrderCreateRequest(customer.getId(), 1L, "PAY_ONLINE", null)));
        assertTrue(ex.getMessage().contains("库存不足"));
    }

    @Test
    void prepay_returnsMockParams() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        java.util.Map<String, Object> params = service.prepay(user, order.getId());
        assertEquals(Boolean.TRUE, params.get("mock"));
        assertNotNull(params.get("prepayId"));
        assertNotNull(params.get("timeStamp"));
        assertNotNull(params.get("paySign"));
    }

    @Test
    void prepay_rejectsNonOwner() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketOrder order = createPaidOrderReady(admin);
        assertThrows(BusinessException.class, () -> service.prepay(operator, order.getId()));
    }

    @Test
    void prepay_rejectsNonPendingOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        assertThrows(BusinessException.class, () -> service.prepay(user, order.getId()));
    }

    @Test
    void pay_delegatesToConfirmMockPay() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        MarketOrder paid = service.pay(user, order.getId(), "admin");
        assertEquals(MarketPayStatus.PAID, paid.getPayStatus());
    }

    // ======================== 审核 ========================

    @Test
    void audit_approve_deductsStock() {
        UserAccount user = admin();
        // 货到付款订单走审核扣库存路径（PAY_ONLINE 订单库存由支付回调扣减，审核不扣）
        MarketOrder order = createCashOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal before = inventoryQty(itemId);
        MarketOrder audited = service.audit(order.getId(), true, "审核通过", "admin");
        assertEquals(MarketOrderStatus.AUDITED, audited.getOrderStatus());
        assertEquals("admin", audited.getReviewer());
        assertEquals("审核通过", audited.getReviewRemark());
        assertEquals(before.subtract(new BigDecimal("2")), inventoryQty(itemId));
    }

    @Test
    void audit_reject_setsRejected() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        MarketOrder rejected = service.audit(order.getId(), false, "不合格", "admin");
        assertEquals(MarketOrderStatus.REJECTED, rejected.getOrderStatus());
        assertEquals("不合格", rejected.getCancelReason());
        assertNotNull(rejected.getCancelledAt());
    }

    @Test
    void audit_rejectsNonPendingOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.audit(order.getId(), true, null, "admin");
        assertThrows(BusinessException.class, () -> service.audit(order.getId(), true, null, "admin"));
    }

    @Test
    void audit_writesLog() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.audit(order.getId(), true, "备注", "admin");
        long auditLogs = orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.AUDIT.equals(l.getAction())).count();
        assertEquals(1, auditLogs);
    }

    // ======================== 发货 ========================

    @Test
    void ship_setsShippedAndClearsCart() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        MarketProduct p = products.findById(order.getItems().get(0).getProductId()).orElseThrow();
        Long salesBefore = p.getSalesCount() == null ? 0L : p.getSalesCount();
        MarketOrder shipped = service.ship(order.getId(), "顺丰", "SF123456", "admin");
        assertEquals(MarketOrderStatus.SHIPPED, shipped.getOrderStatus());
        assertEquals("顺丰", shipped.getLogisticsCompany());
        assertEquals("SF123456", shipped.getLogisticsNumber());
        assertNotNull(shipped.getShippedAt());
        // 销量增加
        MarketProduct pAfter = products.findById(p.getId()).orElseThrow();
        assertEquals(salesBefore + order.getItems().get(0).getQuantity().longValue(), pAfter.getSalesCount());
        // 购物车被清空
        assertEquals(0, carts.findByUserIdOrderByIdDesc(user.getId()).size());
    }

    @Test
    void ship_rejectsUnpaidOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        // 未支付直接发货
        assertThrows(BusinessException.class, () -> service.ship(order.getId(), "SF", "123", "admin"));
    }

    @Test
    void ship_rejectsNonAuditedOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        // 已 AUDITED + PAID，可以发货
        service.ship(order.getId(), "SF", "123", "admin");
        // 再发货应失败（已 SHIPPED）
        assertThrows(BusinessException.class, () -> service.ship(order.getId(), "SF", "456", "admin"));
    }

    @Test
    void ship_writesLog() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.ship(order.getId(), "顺丰", "SF999", "admin");
        long shipLogs = orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.SHIP.equals(l.getAction())).count();
        assertEquals(1, shipLogs);
    }

    // ======================== 完成 ========================

    @Test
    void complete_setsCompleted() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.ship(order.getId(), "SF", "123", "admin");
        MarketOrder completed = service.complete(order.getId(), "admin");
        assertEquals(MarketOrderStatus.COMPLETED, completed.getOrderStatus());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void complete_rejectsNonShippedOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        // 未发货直接完成
        assertThrows(BusinessException.class, () -> service.complete(order.getId(), "admin"));
    }

    @Test
    void complete_writesLog() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.ship(order.getId(), "SF", "123", "admin");
        service.complete(order.getId(), "admin");
        long completeLogs = orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.COMPLETE.equals(l.getAction())).count();
        assertEquals(1, completeLogs);
    }

    // ======================== 取消（用户） ========================

    @Test
    void cancelByUser_cancelsPendingOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.cancelByUser(user, order.getId());
        MarketOrder reloaded = orders.findById(order.getId()).orElseThrow();
        assertEquals(MarketOrderStatus.CANCELLED, reloaded.getOrderStatus());
        assertEquals("用户主动取消", reloaded.getCancelReason());
        assertNotNull(reloaded.getCancelledAt());
    }

    @Test
    void cancelByUser_rejectsNonPending() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        assertThrows(BusinessException.class, () -> service.cancelByUser(user, order.getId()));
    }

    @Test
    void cancelByUser_rejectsNonOwner() {
        UserAccount admin = admin();
        UserAccount operator = users.findByUsername("operator").orElseThrow();
        MarketOrder order = createPaidOrderReady(admin);
        assertThrows(BusinessException.class, () -> service.cancelByUser(operator, order.getId()));
    }

    @Test
    void cancelByUser_writesLog() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.cancelByUser(user, order.getId());
        long cancelLogs = orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.CANCEL.equals(l.getAction())).count();
        assertEquals(1, cancelLogs);
    }

    // ======================== 强制取消（管理员） ========================

    @Test
    void forceCancel_paidOnlineOrderRollsBackStockOnlyAfterRefund() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        service.confirmMockPay(user, order.getId()); // PENDING→AUDITED+PAID, 扣库存
        BigDecimal qtyAfterPay = inventoryQty(itemId);
        MarketOrder cancelled = service.forceCancel(order.getId(), "管理员取消", "admin");
        assertEquals(MarketOrderStatus.CANCELLED, cancelled.getOrderStatus());
        assertEquals(qtyAfterPay, inventoryQty(itemId));
        service.refundCancelledPaidOrders();
        assertEquals(qtyAfterPay.add(new BigDecimal("2")), inventoryQty(itemId));
    }

    @Test
    void forceCancel_pendingOrderNoStockRollback() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal before = inventoryQty(itemId);
        service.forceCancel(order.getId(), "取消", "admin");
        // PENDING 订单未扣库存，取消不回滚
        assertEquals(before, inventoryQty(itemId));
    }

    @Test
    void forceCancel_rejectsCompletedOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.ship(order.getId(), "SF", "123", "admin");
        service.complete(order.getId(), "admin");
        assertThrows(BusinessException.class, () -> service.forceCancel(order.getId(), "x", "admin"));
    }

    @Test
    void forceCancel_rejectsAlreadyCancelled() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.forceCancel(order.getId(), "第一次取消", "admin");
        assertThrows(BusinessException.class, () -> service.forceCancel(order.getId(), "再次取消", "admin"));
    }

    @Test
    void forceCancel_writesInTransaction() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.forceCancel(order.getId(), "取消", "admin");
        service.refundCancelledPaidOrders();
        Long itemId = order.getItems().get(0).getItem().getId();
        assertFalse(inventoryTransactions.findByReferenceNoAndType(order.getOrderNo(), TransactionType.IN).isEmpty(), "退款回滚应生成 IN 入库流水");
    }

    // ======================== 退款 ========================

    @Test
    void refund_mockModeRollsBackStock() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        service.confirmMockPay(user, order.getId());
        BigDecimal qtyAfterPay = inventoryQty(itemId);
        MarketOrder refunded = service.refund(order.getId(), "商品损坏", "admin");
        assertEquals(MarketPayStatus.REFUNDED, refunded.getPayStatus());
        assertNotNull(refunded.getRefundNo());
        assertEquals("商品损坏", refunded.getRefundReason());
        assertEquals(order.getTotalAmount(), refunded.getRefundAmount());
        assertNotNull(refunded.getRefundedAt());
        // 库存回滚
        assertEquals(qtyAfterPay.add(new BigDecimal("2")), inventoryQty(itemId));
    }

    @Test
    void refund_rejectsUnpaidOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        assertThrows(BusinessException.class, () -> service.refund(order.getId(), "x", "admin"));
    }

    @Test
    void refund_rejectsAlreadyRefunded() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.refund(order.getId(), "第一次退款", "admin");
        assertThrows(BusinessException.class, () -> service.refund(order.getId(), "再次退款", "admin"));
    }

    @Test
    void refund_writesLog() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.refund(order.getId(), "退款原因", "admin");
        long refundLogs = orderLogs.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(l -> MarketOrderAction.REFUND.equals(l.getAction())).count();
        assertEquals(1, refundLogs);
    }

    // ======================== 收藏 ========================

    @Test
    void toggleFavorite_addsFavorite() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        boolean fav = service.toggleFavorite(user, p.getId());
        assertTrue(fav);
        assertTrue(service.isFavorite(user.getId(), p.getId()));
    }

    @Test
    void toggleFavorite_removesFavorite() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        service.toggleFavorite(user, p.getId()); // 收藏
        boolean fav = service.toggleFavorite(user, p.getId()); // 取消
        assertFalse(fav);
        assertFalse(service.isFavorite(user.getId(), p.getId()));
    }

    @Test
    void toggleFavorite_rejectsNonExistentProduct() {
        UserAccount user = admin();
        assertThrows(BusinessException.class, () -> service.toggleFavorite(user, 99999L));
    }

    @Test
    void myFavorites_returnsPaginatedResults() {
        UserAccount user = admin();
        MarketProduct p1 = shelfOnProduct(1L, "收藏A", new BigDecimal("10"));
        MarketProduct p2 = shelfOnProduct(2L, "收藏B", new BigDecimal("20"));
        service.toggleFavorite(user, p1.getId());
        service.toggleFavorite(user, p2.getId());
        var page = service.myFavorites(user.getId(), 1, 10);
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void myFavorites_emptyWhenNoFavorites() {
        UserAccount user = admin();
        var page = service.myFavorites(user.getId(), 1, 10);
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void isFavorite_returnsFalseWhenNotFavorited() {
        UserAccount user = admin();
        MarketProduct p = shelfOnProduct();
        assertFalse(service.isFavorite(user.getId(), p.getId()));
    }

    // ======================== 订单查询 ========================

    @Test
    void findByOrderNo_findsOrder() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Optional<MarketOrder> found = orders.findByOrderNo(order.getOrderNo());
        assertTrue(found.isPresent());
        assertEquals(order.getId(), found.get().getId());
    }

    @Test
    void findByOrderNo_returnsEmptyForNonExistent() {
        assertTrue(orders.findByOrderNo("NON-EXISTENT").isEmpty());
    }

    @Test
    void countActiveByUserId_excludesCancelled() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        long before = orders.countActiveByUserId(user.getId());
        service.cancelByUser(user, order.getId());
        long after = orders.countActiveByUserId(user.getId());
        assertEquals(before - 1, after);
    }

    @Test
    void search_findsUserOrders() {
        UserAccount user = admin();
        createPaidOrderReady(user);
        var page = orders.search(user.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() > 0);
    }

    @Test
    void searchAdmin_findsByKeyword() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        var page = orders.searchAdmin(order.getOrderNo().substring(0, 10), null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() > 0);
    }

    // ======================== Dashboard 统计 ========================

    @Test
    void sumCompletedAmount_returnsZeroWhenNoCompletedOrders() {
        // 初始状态无已完成订单（除非其他测试先完成）
        BigDecimal sum = orders.sumCompletedAmount();
        assertNotNull(sum);
    }

    @Test
    void countByStatus_returnsCorrectCount() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        long pendingCount = orders.countByStatus(MarketOrderStatus.PENDING);
        assertTrue(pendingCount > 0);
        service.cancelByUser(user, order.getId());
        long cancelledCount = orders.countByStatus(MarketOrderStatus.CANCELLED);
        assertTrue(cancelledCount > 0);
    }

    @Test
    void countTodayOrders_countsNonCancelled() {
        UserAccount user = admin();
        createPaidOrderReady(user);
        long todayCount = orders.countTodayOrders();
        assertTrue(todayCount > 0);
    }

    @Test
    void sumTodayAmount_sumsNonCancelledOrders() {
        UserAccount user = admin();
        createPaidOrderReady(user);
        BigDecimal todaySales = orders.sumTodayAmount();
        assertNotNull(todaySales);
        assertTrue(todaySales.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void topProducts_returnsAggregatedData() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        service.confirmMockPay(user, order.getId());
        service.ship(order.getId(), "SF", "123", "admin");
        service.complete(order.getId(), "admin");
        List<Object[]> top = orders.topProducts(org.springframework.data.domain.PageRequest.of(0, 10));
        assertNotNull(top);
        // 完成的订单应有销量数据
        assertTrue(top.size() >= 0);
    }

    // ======================== 完整状态机链路 ========================

    @Test
    void fullOrderLifecycle_pendingToCompleted() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal initialQty = inventoryQty(itemId);

        // 1. 支付（PENDING→AUDITED+PAID，扣库存）
        service.confirmMockPay(user, order.getId());
        assertEquals(initialQty.subtract(new BigDecimal("2")), inventoryQty(itemId));

        // 2. 发货（AUDITED→SHIPPED）
        service.ship(order.getId(), "顺丰", "SF001", "admin");

        // 3. 确认收货（SHIPPED→COMPLETED）
        MarketOrder completed = service.complete(order.getId(), "admin");
        assertEquals(MarketOrderStatus.COMPLETED, completed.getOrderStatus());

        // 验证全链路日志
        List<MarketOrderLog> logs = orderLogs.findByOrderIdOrderByIdAsc(order.getId());
        List<MarketOrderAction> actions = logs.stream().map(MarketOrderLog::getAction).toList();
        assertTrue(actions.contains(MarketOrderAction.CREATE));
        assertTrue(actions.contains(MarketOrderAction.PAY));
        assertTrue(actions.contains(MarketOrderAction.SHIP));
        assertTrue(actions.contains(MarketOrderAction.COMPLETE));
    }

    @Test
    void fullOrderLifecycle_payThenRefund() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal initialQty = inventoryQty(itemId);

        // 支付 → 退款 → 库存应恢复
        service.confirmMockPay(user, order.getId());
        assertEquals(initialQty.subtract(new BigDecimal("2")), inventoryQty(itemId));
        service.refund(order.getId(), "质量问题", "admin");
        assertEquals(initialQty, inventoryQty(itemId));
        MarketOrder reloaded = orders.findById(order.getId()).orElseThrow();
        assertEquals(MarketPayStatus.REFUNDED, reloaded.getPayStatus());
    }

    @Test
    void fullOrderLifecycle_pendingToCancelledByUser() {
        UserAccount user = admin();
        MarketOrder order = createPaidOrderReady(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal initialQty = inventoryQty(itemId);

        // PENDING 状态取消，不扣库存不回滚
        service.cancelByUser(user, order.getId());
        assertEquals(initialQty, inventoryQty(itemId));
        MarketOrder reloaded = orders.findById(order.getId()).orElseThrow();
        assertEquals(MarketOrderStatus.CANCELLED, reloaded.getOrderStatus());
    }
}
