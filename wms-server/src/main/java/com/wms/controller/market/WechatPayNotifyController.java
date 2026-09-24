package com.wms.controller.market;

import com.wms.common.BusinessException;
import com.wms.model.entity.market.MarketOrder;
import com.wms.service.market.MarketService;
import com.wms.service.market.WechatPayService;
import com.wechat.pay.java.core.exception.WechatPayException;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 微信支付回调入口（/market/pay/*）。
 * 该路径在 SecurityConfig 中放行（微信服务器不带 token）。
 * 注意：回调不走统一 ApiResponse 壳，直接返回 200/4xx/5xx + {code,message}，
 * 否则微信侧解析失败会持续重试。详见支付成功回调通知 doc_id 4012791902。
 */
@RestController
@RequestMapping("/market/pay")
public class WechatPayNotifyController {
    private static final Logger log = LoggerFactory.getLogger(WechatPayNotifyController.class);

    private final WechatPayService wechatPay;
    private final MarketService marketService;

    public WechatPayNotifyController(WechatPayService wechatPay, MarketService marketService) {
        this.wechatPay = wechatPay;
        this.marketService = marketService;
    }

    /**
     * 支付成功回调通知。
     * 处理步骤（按文档 4012791902）：
     * 1. 读请求头 Wechatpay-Serial/Timestamp/Nonce/Signature
     * 2. 验签 + AES-GCM 解密得 Transaction
     * 3. 按 out_trade_no 查单 → markPaid（幂等）
     * 4. 验签/解密失败返回 4xx FAIL；业务异常返回 5xx FAIL 触发微信重试
     */
    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> notify(HttpServletRequest request,
                                                       @RequestBody String body) {
        String serial = request.getHeader("Wechatpay-Serial");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        if (wechatPay.isMock()) {
            // mock 模式不应收到真实回调
            log.warn("mock 模式收到回调请求，已忽略");
            return fail(HttpStatus.BAD_REQUEST, "mock 模式不接收回调");
        }
        try {
            Transaction tx = wechatPay.handleNotify(serial, nonce, timestamp, signature, body);
            if (tx == null || tx.getOutTradeNo() == null) {
                log.warn("支付回调解密后缺少 out_trade_no：{}", tx);
                return fail(HttpStatus.BAD_REQUEST, "回调报文缺少商户订单号");
            }
            // 仅处理支付成功状态
            if (tx.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
                log.info("支付回调非 SUCCESS 状态：orderNo={}, tradeState={}", tx.getOutTradeNo(), tx.getTradeState());
                return ok();
            }
            MarketOrder order = wechatPay.findByOrderNo(tx.getOutTradeNo())
                    .orElseThrow(() -> new BusinessException("回调对应订单不存在：" + tx.getOutTradeNo()));
            // P1-1：防御性金额校验——回调金额（分）必须与订单 totalAmount 一致，防止订单被篡改后以错误金额支付成功
            Integer notifyTotal = (tx.getAmount() == null) ? null : tx.getAmount().getTotal();
            if (notifyTotal == null) {
                log.warn("支付回调缺少金额信息：orderNo={}", tx.getOutTradeNo());
                return fail(HttpStatus.BAD_REQUEST, "回调报文缺少支付金额");
            }
            Integer expectedFen = WechatPayService.toFen(order.getTotalAmount());
            if (!expectedFen.equals(notifyTotal)) {
                log.error("支付回调金额不一致：orderNo={}, expected={}分, actual={}分", tx.getOutTradeNo(), expectedFen, notifyTotal);
                return fail(HttpStatus.BAD_REQUEST, "支付金额与订单金额不一致");
            }
            // 幂等落单：扣库存 + 状态置 AUDITED/PAID + 回填 transactionId
            marketService.markPaid(order.getId(), tx.getTransactionId(), "wxpay-notify");
            log.info("支付回调处理成功：orderNo={}, transactionId={}", tx.getOutTradeNo(), tx.getTransactionId());
            return ok();
        } catch (Exception e) {
            log.error("支付回调处理失败：{}", e.getMessage(), e);
            // 返回 5xx 触发微信按 15s/15s/30s/... 频次重试；本接口免鉴权，只回固定文案，细节留在日志
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "支付回调处理失败");
        }
    }

    /** 退款结果回调通知：只有 SUCCESS 才完成库存回滚；PROCESSING/CLOSED/ABNORMAL 均不回滚库存。 */
    @PostMapping(value = "/refund-notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> refundNotify(HttpServletRequest request,
                                                             @RequestBody String body) {
        String serial = request.getHeader("Wechatpay-Serial");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        if (wechatPay.isMock()) {
            log.warn("mock 模式收到退款回调请求，已忽略");
            return fail(HttpStatus.BAD_REQUEST, "mock 模式不接收退款回调");
        }
        try {
            RefundNotification rn = wechatPay.handleRefundNotify(serial, nonce, timestamp, signature, body);
            if (rn == null || rn.getOutRefundNo() == null || rn.getOutRefundNo().isBlank()) {
                log.warn("退款回调解密后缺少 out_refund_no：{}", rn);
                return fail(HttpStatus.BAD_REQUEST, "回调报文缺少商户退款单号");
            }
            MarketOrder order = wechatPay.findByRefundNo(rn.getOutRefundNo())
                    .orElseThrow(() -> new BusinessException("退款回调对应订单不存在：" + rn.getOutRefundNo()));
            if (!Objects.equals(order.getRefundNo(), rn.getOutRefundNo())) {
                log.error("退款回调商户退款单号不一致：orderId={}, expected={}, actual={}", order.getId(), order.getRefundNo(), rn.getOutRefundNo());
                return fail(HttpStatus.BAD_REQUEST, "商户退款单号不一致");
            }
            if (rn.getOutTradeNo() != null && !Objects.equals(order.getOrderNo(), rn.getOutTradeNo())) {
                log.error("退款回调商户订单号不一致：refundNo={}, expected={}, actual={}", rn.getOutRefundNo(), order.getOrderNo(), rn.getOutTradeNo());
                return fail(HttpStatus.BAD_REQUEST, "商户订单号不一致");
            }
            if (rn.getTransactionId() != null && order.getTransactionId() != null && !Objects.equals(order.getTransactionId(), rn.getTransactionId())) {
                log.error("退款回调微信支付订单号不一致：refundNo={}, expected={}, actual={}", rn.getOutRefundNo(), order.getTransactionId(), rn.getTransactionId());
                return fail(HttpStatus.BAD_REQUEST, "微信支付订单号不一致");
            }
            Long notifyRefund = rn.getAmount() == null ? null : rn.getAmount().getRefund();
            long expectedFen = WechatPayService.toFen(order.getRefundAmount() == null ? order.getTotalAmount() : order.getRefundAmount()).longValue();
            if (notifyRefund == null || notifyRefund.longValue() != expectedFen) {
                log.error("退款回调金额不一致：refundNo={}, expected={}分, actual={}分", rn.getOutRefundNo(), expectedFen, notifyRefund);
                return fail(HttpStatus.BAD_REQUEST, "退款金额与订单金额不一致");
            }
            Status status = rn.getRefundStatus();
            if (Status.SUCCESS.equals(status)) {
                marketService.finalizeRefundByNo(rn.getOutRefundNo(), rn.getTransactionId(), "wxpay-refund-notify");
                log.info("退款回调处理成功：refundNo={}, transactionId={}", rn.getOutRefundNo(), rn.getTransactionId());
            } else if (Status.PROCESSING.equals(status)) {
                log.info("退款仍在处理中：refundNo={}", rn.getOutRefundNo());
            } else if (Status.CLOSED.equals(status) || Status.ABNORMAL.equals(status)) {
                marketService.markRefundFailedByNo(rn.getOutRefundNo(), "wxpay-refund-notify", "微信退款状态：" + status);
                log.warn("退款未成功：refundNo={}, status={}", rn.getOutRefundNo(), status);
            } else {
                log.warn("退款回调未知状态：refundNo={}, status={}", rn.getOutRefundNo(), status);
                return fail(HttpStatus.BAD_REQUEST, "未知退款状态");
            }
            return ok();
        } catch (WechatPayException e) {
            log.warn("退款回调验签或解密失败：{}", e.getMessage(), e);
            return fail(HttpStatus.BAD_REQUEST, "退款回调验签或解密失败");
        } catch (BusinessException e) {
            log.error("退款回调业务处理失败：{}", e.getMessage(), e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "退款回调业务处理失败");
        } catch (Exception e) {
            log.error("退款回调处理失败：{}", e.getMessage(), e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "退款回调处理失败");
        }
    }

    /** 应答成功：200 + 空 Map（方法签名声明返回 Map，统一返回空 Map 语义清晰）。 */
    private ResponseEntity<Map<String, Object>> ok() {
        return ResponseEntity.ok(new LinkedHashMap<>());
    }

    /** 应答失败：返回 4xx/5xx + {code:"FAIL", message}。 */
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "FAIL");
        body.put("message", message == null ? "处理失败" : message);
        return ResponseEntity.status(status).body(body);
    }
}
