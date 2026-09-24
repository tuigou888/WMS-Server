package com.wms.controller.market;

import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.*;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.MarketCustomer;
import com.wms.model.entity.market.MarketOrder;
import com.wms.service.market.MarketService;
import com.wms.service.market.WechatPayService;
import com.wechat.pay.java.core.exception.WechatPayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 微信支付回调控制器测试。
 * 测试 profile 下 wechat.pay.mock=true：
 * - mock 模式不应收到真实回调，notify 端点应返回 400
 * - 验证 mock 模式拒绝逻辑正确
 * @Transactional 自动回滚保证测试间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WechatPayNotifyControllerTest {

    @Autowired private WechatPayNotifyController controller;
    @Autowired private WechatPayService wechatPay;
    @Autowired private MarketService marketService;
    @Autowired private com.wms.repository.UserAccountRepository users;
    @Autowired private com.wms.repository.market.MarketOrderRepository orders;

    @Test
    void notify_rejectsInMockMode() {
        // mock 模式不应处理真实回调
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Wechatpay-Serial", "serial");
        request.addHeader("Wechatpay-Timestamp", "1234567890");
        request.addHeader("Wechatpay-Nonce", "nonce");
        request.addHeader("Wechatpay-Signature", "signature");

        ResponseEntity<Map<String, Object>> response = controller.notify(request, "{}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("FAIL", response.getBody().get("code"));
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    void notify_mockModeIsAlwaysTrueInTest() {
        assertTrue(wechatPay.isMock(), "test 环境必须是 mock 模式");
    }

    @Test
    void notify_failHelperReturnsCorrectFormat() {
        // 间接验证：mock 模式下 notify 调用应走 fail 路径
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<Map<String, Object>> response = controller.notify(request, "");
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("FAIL", body.get("code"));
        // message 不为空
        assertNotNull(body.get("message"));
    }

    @Test
    void notify_nullBodyStillHandled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 空 body 在 mock 模式下仍应被拒绝
        assertDoesNotThrow(() -> controller.notify(request, ""));
        ResponseEntity<Map<String, Object>> response = controller.notify(request, "");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void markPaid_idempotentViaNotifyPath() {
        // 模拟回调路径的幂等性：markPaid 被调用两次不重复扣库存
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createOrderForPay(user);
        Long itemId = order.getItems().get(0).getItem().getId();
        BigDecimal qtyBefore = getInventoryQty(itemId);

        marketService.markPaid(order.getId(), "TX_001", "test");
        BigDecimal qtyAfterFirst = getInventoryQty(itemId);

        // 第二次 markPaid（模拟重复回调）不重复扣库存
        marketService.markPaid(order.getId(), "TX_001", "test");
        BigDecimal qtyAfterSecond = getInventoryQty(itemId);

        assertEquals(qtyAfterFirst, qtyAfterSecond, "重复回调不应重复扣库存");
        assertEquals(qtyBefore.subtract(new BigDecimal("2")), qtyAfterFirst);
    }

    @Test
    void markPaid_differentTransactionIdStillIdempotent() {
        // 不同 transactionId 的重复回调也应幂等
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createOrderForPay(user);
        Long itemId = order.getItems().get(0).getItem().getId();

        marketService.markPaid(order.getId(), "TX_A", "test");
        BigDecimal qtyAfterFirst = getInventoryQty(itemId);

        MarketOrder second = marketService.markPaid(order.getId(), "TX_B", "test");
        BigDecimal qtyAfterSecond = getInventoryQty(itemId);

        assertEquals(qtyAfterFirst, qtyAfterSecond);
        // 第二次回调不应改 transactionId
        assertEquals("TX_A", second.getTransactionId());
    }

    /** 回调接口免鉴权：异常细节（订单号、SQL、验签内部信息）只能进日志，不能回给调用方。 */
    @Test
    void notify_doesNotEchoExceptionDetail() {
        WechatPayService pay = mock(WechatPayService.class);
        when(pay.isMock()).thenReturn(false);
        when(pay.handleNotify(any(), any(), any(), any(), any())).thenThrow(
                new IllegalStateException("更新失败 order_no=CKD20260923001 sql=update market_order set ..."));

        ResponseEntity<Map<String, Object>> response =
                new WechatPayNotifyController(pay, mock(MarketService.class)).notify(new MockHttpServletRequest(), "{}");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(), "5xx 才能触发微信重试");
        assertEquals("支付回调处理失败", response.getBody().get("message"));
    }

    /** 退款回调同理：验签失败返回固定 400 文案，不回显 SDK 异常内容。 */
    @Test
    void refundNotify_doesNotEchoSignatureDetail() {
        WechatPayService pay = mock(WechatPayService.class);
        when(pay.isMock()).thenReturn(false);
        when(pay.handleRefundNotify(any(), any(), any(), any(), any())).thenThrow(
                new WechatPayException("验签失败：serial=SERIAL-SECRET timestamp=1700000000") {});

        ResponseEntity<Map<String, Object>> response = new WechatPayNotifyController(pay, mock(MarketService.class))
                .refundNotify(new MockHttpServletRequest(), "{}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "验签失败不应让微信持续重试");
        assertEquals("退款回调验签或解密失败", response.getBody().get("message"));
    }

    // ======================== 辅助方法 ========================

    @Autowired private com.wms.repository.InventoryRepository invRepo;

    private BigDecimal getInventoryQty(Long itemId) {
        return invRepo.findAllDetailed().stream()
                .filter(i -> i.getItem().getId().equals(itemId) && i.getWarehouse().getId().equals(1L))
                .map(com.wms.model.entity.Inventory::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MarketOrder createOrderForPay(UserAccount user) {
        // 创建上架商品
        MarketProductRequest prodReq = new MarketProductRequest(
                1L, "回调测试商品", null, null, null, new BigDecimal("20.00"), null, null, 0);
        com.wms.model.entity.market.MarketProduct p = marketService.saveProduct(prodReq);
        marketService.changeStatus(p.getId(), "SHELF_ON");
        // 创建收货人
        MarketCustomer customer = marketService.saveCustomer(user, new MarketCustomerRequest(
                "回调测试", "13800000000", "地址", true, null));
        // 加购物车 + 下单
        marketService.addCart(user, p.getId(), 2);
        return marketService.createOrder(user, new MarketOrderCreateRequest(
                customer.getId(), 1L, "PAY_ONLINE", null));
    }
}
