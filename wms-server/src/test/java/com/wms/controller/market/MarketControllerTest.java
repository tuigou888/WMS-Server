package com.wms.controller.market;

import com.wms.dto.market.MarketDtos.*;
import com.wms.harness.Harness;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.*;
import com.wms.repository.UserAccountRepository;
import com.wms.repository.market.*;
import com.wms.security.Permissions;
import com.wms.security.SecurityUtils;
import com.wms.service.market.MarketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商城控制器层测试（MarketController + MarketAdminController）。
 * 验证 API 层的权限校验、响应壳封装、视图转换、分页等。
 * 使用 Harness.asAdmin 模拟登录上下文，@Transactional 自动回滚保证测试隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketControllerTest {

    @Autowired private MarketController marketController;
    @Autowired private MarketAdminController adminController;
    @Autowired private MarketService service;
    @Autowired private MarketProductRepository products;
    @Autowired private MarketCartRepository carts;
    @Autowired private MarketCustomerRepository customers;
    @Autowired private MarketOrderRepository orders;
    @Autowired private MarketFavoriteRepository favorites;
    @Autowired private UserAccountRepository users;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private UserAccount admin() { return users.findByUsername("admin").orElseThrow(); }

    /** 创建上架商品（API 路径）。 */
    /** 创建上架商品（API 路径），幂等：已存在则复用。 */
    private Map<String, Object> createShelfOnProductViaApi() {
        return ensureProductViaApi(1L, "API测试商品", new BigDecimal("20.00"));
    }

    /** 确保 itemId 对应的商品存在（幂等），返回视图 Map。 */
    private Map<String, Object> ensureProductViaApi(Long itemId, String title, BigDecimal salePrice) {
        return Harness.asAdmin(() -> {
            var existing = products.findByItemId(itemId);
            if (existing.isPresent()) {
                return MarketController.view(existing.get());
            }
            Map<String, Object> p = adminController.create(new MarketProductRequest(
                    itemId, title, "副标题", "img.png", null,
                    salePrice, new BigDecimal("30.00"), null, 0)).data();
            adminController.shelf((Long) p.get("id"), new MarketShelfRequest("SHELF_ON"));
            return p;
        });
    }

    // ==================== 商品浏览 API ====================

    @Test
    void categories_returnsEnabledCategories() {
        Harness.asAdmin(() -> {
            var resp = marketController.categories();
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertFalse(resp.data().isEmpty());
        });
    }

    @Test
    void products_returnsPaginatedShelfOnProducts() {
        createShelfOnProductViaApi();
        Harness.asAdmin(() -> {
            var resp = marketController.products(1, 10, null, null);
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            // 应返回分页结构
            assertTrue(resp.data().containsKey("records"));
            assertTrue(resp.data().containsKey("total"));
            assertTrue(resp.data().containsKey("page"));
        });
    }

    @Test
    void products_filtersByKeyword() {
        createShelfOnProductViaApi();
        Harness.asAdmin(() -> {
            var resp = marketController.products(1, 50, null, "API测试");
            assertNotNull(resp.data());
            assertFalse(((List<?>) resp.data().get("records")).isEmpty());
        });
    }

    @Test
    void productDetail_returnsProductWithStock() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long id = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            var resp = marketController.product(id);
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertEquals("API测试商品", resp.data().get("title"));
            assertNotNull(resp.data().get("availableStock"));
        });
    }

    @Test
    void productDetail_rejectsNonExistent() {
        Harness.asAdmin(() -> {
            assertThrows(com.wms.common.BusinessException.class, () -> marketController.product(99999L));
        });
    }

    @Test
    void warehouses_returnsEnabledWarehouses() {
        Harness.asAdmin(() -> {
            var resp = marketController.warehouses();
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertFalse(resp.data().isEmpty());
        });
    }

    // ==================== 购物车 API ====================

    @Test
    void addCart_returnsCartItemView() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            var resp = marketController.addCart(new MarketCartAddRequest(productId, 3));
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertEquals(3, ((Number) resp.data().get("quantity")).intValue());
        });
    }

    @Test
    void cartList_returnsItemsAndTotal() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            marketController.addCart(new MarketCartAddRequest(productId, 2));
            var resp = marketController.cart();
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertNotNull(resp.data().get("items"));
            assertNotNull(resp.data().get("total"));
            assertNotNull(resp.data().get("count"));
        });
    }

    @Test
    void updateCart_changesQuantity() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            Map<String, Object> cart = marketController.addCart(
                    new MarketCartAddRequest(productId, 1)).data();
            Long cartId = ((Number) cart.get("id")).longValue();
            var resp = marketController.updateCart(cartId, new MarketCartUpdateRequest(5));
            assertEquals(5, ((Number) resp.data().get("quantity")).intValue());
        });
    }

    @Test
    void clearCart_removesAllItems() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            marketController.addCart(new MarketCartAddRequest(productId, 1));
            var resp = marketController.clearCart(null);
            assertEquals(200, resp.code());
            entityManager.clear();
            var cart = marketController.cart();
            assertEquals(0, cart.data().get("count"));
        });
    }

    @Test
    void deleteCart_removesSpecificItems() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            Map<String, Object> cart = marketController.addCart(
                    new MarketCartAddRequest(productId, 1)).data();
            Long cartId = ((Number) cart.get("id")).longValue();
            marketController.clearCart(Map.of("ids", List.of(cartId)));
            // @Modifying delete 绕过一级缓存，需 clear 后查询
            entityManager.clear();
            assertTrue(carts.findById(cartId).isEmpty());
        });
    }

    // ==================== 收货人 API ====================

    @Test
    void customers_crudLifecycle() {
        Harness.asAdmin(() -> {
            // 创建
            var created = marketController.saveCustomer(new MarketCustomerRequest(
                    "测试人", "13800000000", "测试地址1", true, "备注"));
            assertEquals(200, created.code());
            Long id = ((Number) created.data().get("id")).longValue();
            // 查询列表
            var list = marketController.customers();
            assertFalse(list.data().isEmpty());
            // 更新
            var updated = marketController.updateCustomer(id, new MarketCustomerRequest(
                    "更新名", "13900000000", "更新地址", false, null));
            assertEquals("更新名", updated.data().get("name"));
            // 删除
            marketController.deleteCustomer(id);
            assertTrue(customers.findById(id).isEmpty());
        });
    }

    // ==================== 订单 API ====================

    @Test
    void createOrder_returnsOrderView() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "下单人", "13700000000", "下单地址", true, null));
            service.addCart(user, productId, 2);
            var resp = marketController.createOrder(new MarketOrderCreateRequest(
                    c.getId(), 1L, "PAY_ONLINE", "API下单"));
            assertEquals(200, resp.code());
            assertNotNull(resp.data());
            assertEquals(MarketOrderStatus.PENDING, resp.data().get("orderStatus"));
            assertTrue(((String) resp.data().get("orderNo")).startsWith("MO-"));
        });
    }

    @Test
    void orders_listReturnsUserOrders() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "列表", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.orders(1, 10, null);
            assertEquals(200, resp.code());
            assertTrue(((Number) resp.data().get("total")).longValue() > 0);
        });
    }

    @Test
    void orderDetail_includesLogs() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "详情", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.order(o.getId());
            assertEquals(200, resp.code());
            assertNotNull(resp.data().get("logs"));
            assertFalse(((List<?>) resp.data().get("logs")).isEmpty());
        });
    }

    @Test
    void prepay_returnsMockPaymentParams() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "支付", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.prepay(o.getId());
            assertEquals(200, resp.code());
            assertEquals(Boolean.TRUE, resp.data().get("mock"));
        });
    }

    @Test
    void mockPay_marksOrderPaid() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "模拟支付", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.mockPay(o.getId());
            assertEquals(200, resp.code());
            assertEquals(MarketPayStatus.PAID, resp.data().get("payStatus"));
            assertEquals(MarketOrderStatus.AUDITED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void cancelOrder_cancelsPendingOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "取消", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.cancel(o.getId());
            assertEquals(200, resp.code());
            MarketOrder reloaded = orders.findById(o.getId()).orElseThrow();
            assertEquals(MarketOrderStatus.CANCELLED, reloaded.getOrderStatus());
        });
    }

    @Test
    void receive_completesShippedOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "收货", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            service.confirmMockPay(user, o.getId());
            service.ship(o.getId(), "SF", "123", "admin");
            var resp = marketController.receive(o.getId());
            assertEquals(200, resp.code());
            assertEquals(MarketOrderStatus.COMPLETED, resp.data().get("orderStatus"));
        });
    }

    // ==================== 收藏 API ====================

    @Test
    void toggleFavorite_addsAndRemoves() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            // 收藏
            var resp1 = marketController.toggleFavorite(productId);
            assertTrue((Boolean) resp1.data().get("favorited"));
            // 取消收藏
            var resp2 = marketController.toggleFavorite(productId);
            assertFalse((Boolean) resp2.data().get("favorited"));
        });
    }

    @Test
    void checkFavorite_returnsStatus() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            // 未收藏
            var resp1 = marketController.checkFavorite(productId);
            assertFalse((Boolean) resp1.data().get("favorited"));
            // 收藏后
            marketController.toggleFavorite(productId);
            var resp2 = marketController.checkFavorite(productId);
            assertTrue((Boolean) resp2.data().get("favorited"));
        });
    }

    @Test
    void favoritesList_returnsPaginatedResults() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            marketController.toggleFavorite(productId);
            var resp = marketController.favorites(1, 10);
            assertEquals(200, resp.code());
            assertNotNull(resp.data().get("records"));
        });
    }

    // ==================== 管理后台 API ====================

    @Test
    void adminProducts_listAll() {
        createShelfOnProductViaApi();
        Harness.asAdmin(() -> {
            var resp = adminController.products(1, 50, null, null);
            assertEquals(200, resp.code());
            assertTrue(((Number) resp.data().get("total")).longValue() > 0);
        });
    }

    @Test
    void adminProducts_filterByStatus() {
        createShelfOnProductViaApi();
        Harness.asAdmin(() -> {
            var resp = adminController.products(1, 50, null, "SHELF_ON");
            assertEquals(200, resp.code());
            List<?> records = (List<?>) resp.data().get("records");
            assertFalse(records.isEmpty());
        });
    }

    @Test
    void adminShelf_togglesStatus() {
        // 先在 Harness 内创建商品（避免嵌套 Harness 清除上下文）
        Harness.asAdmin(() -> {
            var existing = products.findByItemId(2L);
            Long id;
            if (existing.isPresent()) {
                id = existing.get().getId();
            } else {
                Map<String, Object> p = adminController.create(new MarketProductRequest(
                        2L, "上架测试", null, null, null, new BigDecimal("10"), null, null, 0)).data();
                id = ((Number) p.get("id")).longValue();
            }
            var resp = adminController.shelf(id, new MarketShelfRequest("SHELF_ON"));
            assertEquals("SHELF_ON", resp.data().get("status"));
        });
    }

    @Test
    void adminDeleteProduct_removesProduct() {
        Harness.asAdmin(() -> {
            var existing = products.findByItemId(3L);
            Long id;
            if (existing.isPresent()) {
                id = existing.get().getId();
            } else {
                Map<String, Object> p = adminController.create(new MarketProductRequest(
                        3L, "删除测试", null, null, null, new BigDecimal("10"), null, null, 0)).data();
                id = ((Number) p.get("id")).longValue();
            }
            adminController.delete(id);
            assertTrue(products.findById(id).isEmpty());
        });
    }

    @Test
    void adminOrders_listAll() {
        // 先创建商品（独立 Harness，不嵌套）
        createShelfOnProductViaApi();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "管理订单", "13800000000", "地址", true, null));
            // 用 service 而非 controller 创建第二件商品，避免嵌套 Harness 清除上下文
            var existing = products.findByItemId(1L).orElseThrow();
            service.addCart(user, existing.getId(), 1);
            service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = adminController.orders(1, 20, null, null);
            assertEquals(200, resp.code());
            assertTrue(((Number) resp.data().get("total")).longValue() > 0);
        });
    }

    @Test
    void adminAudit_approvesOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "审核", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = adminController.audit(o.getId(), new MarketOrderAuditRequest(true, "审核通过"));
            assertEquals(MarketOrderStatus.AUDITED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void adminAudit_rejectsOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "驳回", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = adminController.audit(o.getId(), new MarketOrderAuditRequest(false, "不合格"));
            assertEquals(MarketOrderStatus.REJECTED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void adminShip_shipsAuditedOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "发货", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            service.confirmMockPay(user, o.getId());
            var resp = adminController.ship(o.getId(), new MarketOrderShipRequest("顺丰", "SF999"));
            assertEquals(MarketOrderStatus.SHIPPED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void adminComplete_completesShippedOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "完成", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            service.confirmMockPay(user, o.getId());
            service.ship(o.getId(), "SF", "123", "admin");
            var resp = adminController.complete(o.getId());
            assertEquals(MarketOrderStatus.COMPLETED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void adminCancel_cancelsOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "管理取消", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = adminController.cancel(o.getId(), Map.of("reason", "管理员取消"));
            assertEquals(MarketOrderStatus.CANCELLED, resp.data().get("orderStatus"));
        });
    }

    @Test
    void adminRefund_refundsPaidOrder() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "退款", "13800000000", "地址", true, null));
            service.addCart(user, productId, 1);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            service.confirmMockPay(user, o.getId());
            var resp = adminController.refund(o.getId(), Map.of("reason", "退款测试"));
            assertEquals(MarketPayStatus.REFUNDED, resp.data().get("payStatus"));
        });
    }

    @Test
    void adminStats_returnsDashboardData() {
        Harness.asAdmin(() -> {
            var resp = adminController.stats();
            assertEquals(200, resp.code());
            Map<String, Object> data = resp.data();
            assertNotNull(data.get("totalSales"));
            assertNotNull(data.get("todayOrders"));
            assertNotNull(data.get("todaySales"));
            assertNotNull(data.get("totalProducts"));
            assertNotNull(data.get("totalCustomers"));
            assertNotNull(data.get("statusCounts"));
            assertNotNull(data.get("topProducts"));
        });
    }

    @Test
    void adminCustomers_listAll() {
        Harness.asAdmin(() -> {
            service.saveCustomer(admin(), new MarketCustomerRequest(
                    "客户列表", "13800000000", "地址", true, null));
            var resp = adminController.customers(1, 20);
            assertEquals(200, resp.code());
            assertTrue(((Number) resp.data().get("total")).longValue() > 0);
        });
    }

    @Test
    void adminCustomers_crud() {
        Harness.asAdmin(() -> {
            // 创建
            var created = adminController.createCustomer(new MarketCustomerRequest(
                    "管理客户", "13800000000", "地址", false, null));
            assertEquals(200, created.code());
            Long id = ((Number) created.data().get("id")).longValue();
            // 更新
            var updated = adminController.updateCustomer(id, new MarketCustomerRequest(
                    "更新客户", "13900000000", "新地址", false, null));
            assertEquals("更新客户", updated.data().get("name"));
            // 删除
            adminController.deleteCustomer(id);
            assertTrue(customers.findById(id).isEmpty());
        });
    }

    // ==================== 视图转换 ====================

    @Test
    void view_productContainsAllFields() {
        Harness.asAdmin(() -> {
            Map<String, Object> p = ensureProductViaApi(2L, "视图测试", new BigDecimal("20.00"));
            // view(MarketProduct) 应包含所有展示字段
            assertNotNull(p.get("id"));
            assertNotNull(p.get("title"));
            assertNotNull(p.get("salePrice"));
            assertNotNull(p.get("marketPrice"));
            assertNotNull(p.get("status"));
            assertNotNull(p.get("itemId"));
            assertNotNull(p.get("itemCode"));
            assertNotNull(p.get("itemName"));
            assertNotNull(p.get("unit"));
            // gallery 应被拆分成数组
            assertNotNull(p.get("gallery"));
            assertTrue(p.get("gallery") instanceof List);
        });
    }

    @Test
    void view_orderContainsItemsAndStatus() {
        Map<String, Object> p = createShelfOnProductViaApi();
        Long productId = ((Number) p.get("id")).longValue();
        Harness.asAdmin(() -> {
            UserAccount user = admin();
            MarketCustomer c = service.saveCustomer(user, new MarketCustomerRequest(
                    "视图", "13800000000", "地址", true, null));
            service.addCart(user, productId, 2);
            MarketOrder o = service.createOrder(user, new MarketOrderCreateRequest(c.getId(), 1L, "PAY_ONLINE", null));
            var resp = marketController.order(o.getId());
            Map<String, Object> view = resp.data();
            assertNotNull(view.get("orderNo"));
            assertNotNull(view.get("orderStatus"));
            assertNotNull(view.get("payStatus"));
            assertNotNull(view.get("totalAmount"));
            assertNotNull(view.get("items"));
            assertFalse(((List<?>) view.get("items")).isEmpty());
        });
    }
}
