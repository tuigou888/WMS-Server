package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.MarketOrder;
import com.wms.model.entity.market.MarketOrderStatus;
import com.wms.model.entity.market.MarketPayStatus;
import com.wms.model.entity.market.MarketPayType;
import com.wms.repository.UserAccountRepository;
import com.wms.repository.market.MarketOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微信支付服务单元测试（mock 模式）。
 * 测试 profile 下 wechat.pay.mock=true，验证 mock 通路正确性。
 * 真实模式分支由 WechatPayConfig 的 @ConditionalOnProperty 隔离，mock 测试不会激活真实 SDK Bean。
 * @Transactional 自动回滚保证测试间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WechatPayServiceTest {

    @Autowired private WechatPayService wechatPay;
    @Autowired private UserAccountRepository users;
    @Autowired private MarketOrderRepository orders;
    @Autowired private com.wms.repository.WarehouseRepository warehouses;

    // ======================== mock 模式判断 ========================

    @Test
    void isMock_returnsTrueInTestProfile() {
        assertTrue(wechatPay.isMock(), "test profile 下 wechat.pay.mock=true，isMock 应返回 true");
    }

    // ======================== prepay（mock 模式） ========================

    @Test
    void prepay_mockModeReturnsMockParams() {
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createMockOrder(user, new BigDecimal("99.99"));
        Map<String, Object> params = wechatPay.prepay(order, user);
        assertEquals(Boolean.TRUE, params.get("mock"));
        assertNotNull(params.get("prepayId"));
        assertTrue(params.get("prepayId").toString().startsWith("MOCK_"));
        assertNotNull(params.get("timeStamp"));
        assertNotNull(params.get("nonceStr"));
        assertEquals("prepay_id=" + params.get("prepayId"), params.get("package"));
        assertEquals("RSA", params.get("signType"));
        assertEquals("MOCK_SIGN", params.get("paySign"));
    }

    @Test
    void prepay_mockPrepayIdContainsOrderNo() {
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createMockOrder(user, new BigDecimal("10.00"));
        Map<String, Object> params = wechatPay.prepay(order, user);
        String prepayId = params.get("prepayId").toString();
        assertTrue(prepayId.contains(order.getOrderNo()), "mock prepayId 应包含订单号");
    }

    // ======================== handleNotify ========================

    @Test
    void handleNotify_rejectsInMockMode() {
        // mock 模式不应进入真实回调处理
        assertThrows(BusinessException.class, () ->
                wechatPay.handleNotify("serial", "nonce", "timestamp", "sig", "{}"));
    }

    // ======================== queryByOutTradeNo ========================

    @Test
    void queryByOutTradeNo_returnsEmptyInMockMode() {
        // mock 模式不查单，返回 Optional.empty()（区别于真实模式查单失败）
        assertTrue(wechatPay.queryByOutTradeNo("MO-20260101-0001").isEmpty());
    }

    // ======================== refund ========================

    @Test
    void refund_returnsNullInMockMode() {
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createMockOrder(user, new BigDecimal("50.00"));
        com.wechat.pay.java.service.refund.model.Refund result = wechatPay.refund(order, "测试退款");
        assertNull(result, "mock 模式 refund 应返回 null");
    }

    // ======================== toFen 工具方法 ========================

    @Test
    void toFen_convertsYuanToFen() {
        assertEquals(100, WechatPayService.toFen(new BigDecimal("1.00")));
        assertEquals(1, WechatPayService.toFen(new BigDecimal("0.01")));
        assertEquals(9999, WechatPayService.toFen(new BigDecimal("99.99")));
        assertEquals(0, WechatPayService.toFen(BigDecimal.ZERO));
    }

    @Test
    void toFen_handlesNull() {
        assertEquals(0, WechatPayService.toFen(null));
    }

    @Test
    void toFen_roundsHalfUp() {
        // 0.005 → 1 分（HALF_UP）
        assertEquals(1, WechatPayService.toFen(new BigDecimal("0.005")));
        // 0.004 → 0 分
        assertEquals(0, WechatPayService.toFen(new BigDecimal("0.004")));
        // 1.235 → 124 分（HALF_UP: 123.5 → 124）
        assertEquals(124, WechatPayService.toFen(new BigDecimal("1.235")));
    }

    // ======================== findByOrderNo ========================

    @Test
    void findByOrderNo_returnsEmptyForNonExistent() {
        assertTrue(wechatPay.findByOrderNo("NON-EXIST").isEmpty());
    }

    @Test
    void findByOrderNo_findsExistingOrder() {
        UserAccount user = users.findByUsername("admin").orElseThrow();
        MarketOrder order = createMockOrder(user, new BigDecimal("10"));
        assertTrue(wechatPay.findByOrderNo(order.getOrderNo()).isPresent());
    }

    // ======================== 辅助方法 ========================

    /** 创建一个最小化的 MarketOrder 用于支付测试（不走完整下单流程，直接构造实体）。 */
    private MarketOrder createMockOrder(UserAccount user, BigDecimal totalAmount) {
        MarketOrder order = new MarketOrder();
        order.setOrderNo("MO-TEST-" + System.nanoTime());
        order.setUser(user);
        // warehouse 是 nullable=false，取 WH-001(id=1)
        order.setWarehouse(warehouses.findById(1L).orElseThrow());
        order.setReceiverName("测试");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("测试地址");
        order.setTotalAmount(totalAmount);
        order.setPayType(MarketPayType.PAY_ONLINE);
        order.setOrderStatus(MarketOrderStatus.PENDING);
        order.setPayStatus(MarketPayStatus.UNPAID);
        return orders.save(order);
    }
}
