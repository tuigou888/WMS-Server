package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.MarketOrder;
import com.wms.repository.market.MarketOrderRepository;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 微信支付业务服务（APIv3 / 普通商户 / 小程序支付）。
 * - mock 模式（wechat.pay.mock=true）：prepay 返回模拟调起参数，handleNotify 解析模拟回调报文。
 * - 真实模式：调官方 SDK 完成 JSAPI 下单、查单、退款、回调验签解密。
 *
 * 文档依据：
 * - 下单 doc_id 4012791897、调起支付 4012791898、支付回调 4012791902
 * - 查单 4012791900、退款申请 4012791903
 */
@Service
public class WechatPayService {
    private static final Logger log = LoggerFactory.getLogger(WechatPayService.class);

    /** RFC3339 格式（微信支付 time_expire / success_time）。 */
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final MarketOrderRepository orders;
    private final Environment environment;

    @Value("${wechat.pay.mock:false}") private boolean mock;
    @Value("${wechat.appid:}") private String appid;
    @Value("${wechat.pay.mch-id:}") private String mchId;
    @Value("${wechat.pay.notify-url:}") private String notifyUrl;
    @Value("${wechat.pay.refund-notify-url:}") private String refundNotifyUrl;

    // 真实模式下注入的 SDK 客户端；mock 模式下为 null
    private final JsapiServiceExtension jsapiService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;

    public WechatPayService(MarketOrderRepository orders,
                            Environment environment,
                            @Autowired(required = false) JsapiServiceExtension jsapiService,
                            @Autowired(required = false) RefundService refundService,
                            @Autowired(required = false) NotificationParser notificationParser,
                            @Qualifier("wechatPayAppid") @Autowired(required = false) String appidBean,
                            @Qualifier("wechatPayMchId") @Autowired(required = false) String mchIdBean,
                            @Qualifier("wechatPayNotifyUrl") @Autowired(required = false) String notifyUrlBean,
                            @Qualifier("wechatPayRefundNotifyUrl") @Autowired(required = false) String refundNotifyUrlBean) {
        this.orders = orders;
        this.environment = environment;
        this.jsapiService = jsapiService;
        this.refundService = refundService;
        this.notificationParser = notificationParser;
        // 真实模式时用 Config Bean 的值覆盖（更可靠）；mock 模式下 Bean 不存在，保持 yml 原值
        if (appidBean != null) this.appid = appidBean;
        if (mchIdBean != null) this.mchId = mchIdBean;
        if (notifyUrlBean != null) this.notifyUrl = notifyUrlBean;
        if (refundNotifyUrlBean != null) this.refundNotifyUrl = refundNotifyUrlBean;
    }

    /** 当前是否为 mock 模式。 */
    public boolean isMock() {
        if (!mock) return false;
        // 生产环境严禁开启 mock（同 WechatService 的校验逻辑）
        String[] profiles = environment.getActiveProfiles();
        boolean isDev = profiles.length == 0 || Arrays.stream(profiles).anyMatch(p -> "dev".equals(p) || "test".equals(p));
        if (!isDev) {
            throw new BusinessException("生产环境禁止启用 wechat.pay.mock，请配置真实微信支付参数");
        }
        return true;
    }

    /** 拉起支付参数（小程序 requestPayment 所需）。 */
    public Map<String, Object> prepay(MarketOrder order, UserAccount user) {
        if (isMock()) {
            String mockPrepayId = "MOCK_" + order.getOrderNo() + "_" + System.currentTimeMillis();
            // mock 模式：返回可直接用于「模拟支付确认」的标识；前端拿到 mock=true 后跳过 requestPayment
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", true);
            params.put("prepayId", mockPrepayId);
            params.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("nonceStr", mockPrepayId);
            params.put("package", "prepay_id=" + mockPrepayId);
            params.put("signType", "RSA");
            params.put("paySign", "MOCK_SIGN");
            return params;
        }
        // 真实模式：校验 openid（JSAPI 下单必填）
        String openid = user.getOpenid();
        if (openid == null || openid.isBlank()) {
            throw new BusinessException("当前账号未绑定微信，请使用微信登录后再支付");
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appid);
        request.setMchid(mchId);
        request.setDescription(buildDescription(order));
        request.setOutTradeNo(order.getOrderNo());
        request.setNotifyUrl(notifyUrl);
        // 支付有效期 30 分钟（RFC3339），超时后用户无法支付，需关单重新下单
        request.setTimeExpire(RFC3339.format(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(30)));
        request.setAttach("orderId:" + order.getId());
        Amount amount = new Amount();
        amount.setTotal(toFen(order.getTotalAmount()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        // prepayWithRequestPayment 直接返回含签名的调起参数（SDK 已代为生成 paySign）
        PrepayWithRequestPaymentResponse resp = jsapiService.prepayWithRequestPayment(request);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mock", false);
        params.put("prepayId", ""); // prepayWithRequestPayment 不直接返回 prepay_id，packageVal 已含
        params.put("appId", appid);
        params.put("timeStamp", resp.getTimeStamp());
        params.put("nonceStr", resp.getNonceStr());
        params.put("package", resp.getPackageVal());
        params.put("signType", resp.getSignType());
        params.put("paySign", resp.getPaySign());
        return params;
    }

    /** 处理支付成功回调：验签 + AES-GCM 解密，返回解密后的 Transaction。
     *  SDK 内部用 timestamp+nonce+body 构造验签串校验 signature。 */
    public Transaction handleNotify(String serial, String nonce, String timestamp, String signature, String body) {
        if (isMock()) {
            // mock 模式走 confirmMockPay 直接落单，不应进入真实回调处理
            throw new BusinessException("mock 模式不应进入真实回调处理");
        }
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .timestamp(timestamp)
                .nonce(nonce)
                .signature(signature)
                .body(body)
                .build();
        return notificationParser.parse(requestParam, Transaction.class);
    }

    /** 商户订单号查单（用于前端 requestPayment 成功后兜底确认）。
     *  P2-5：mock 模式返回 Optional.empty() 表示「mock 跳过查单」，与真实模式查单失败区分开。 */
    public Optional<Transaction> queryByOutTradeNo(String orderNo) {
        if (isMock()) {
            // mock 模式由 confirmMockPay 直接落单，不查单
            return Optional.empty();
        }
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(orderNo);
        request.setMchid(mchId);
        return Optional.ofNullable(jsapiService.queryOrderByOutTradeNo(request));
    }

    /** 发起退款，返回退款单信息（受理成功仅表示已受理，最终以退款回调/查询为准）。 */
    public Refund refund(MarketOrder order, String reason) {
        String outRefundNo = order == null ? null : order.getRefundNo();
        if (isBlank(outRefundNo)) {
            if (isMock()) return null;
            throw new BusinessException("缺少商户退款单号，无法发起退款");
        }
        return refund(order, reason, outRefundNo);
    }

    /** 发起退款，商户退款单号必须由业务层持久化序列生成，避免重启/并发下重复或丢失。 */
    public Refund refund(MarketOrder order, String reason, String outRefundNo) {
        if (isMock()) {
            // mock 模式无法调真实退款接口，直接返回 null（调用方据此置 REFUNDED 状态）
            return null;
        }
        if (refundService == null) throw new BusinessException("微信退款服务未初始化");
        if (isBlank(outRefundNo)) throw new BusinessException("缺少商户退款单号，无法退款");
        if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
            throw new BusinessException("订单缺少微信支付订单号，无法退款");
        }
        BigDecimal refundAmount = order.getRefundAmount() == null ? order.getTotalAmount() : order.getRefundAmount();
        Integer refundFen = toFen(refundAmount);
        Integer totalFen = toFen(order.getTotalAmount());
        if (refundFen <= 0 || totalFen <= 0) throw new BusinessException("退款金额必须大于0");
        String effectiveRefundNotifyUrl = effectiveRefundNotifyUrl();
        if (isBlank(effectiveRefundNotifyUrl)) throw new BusinessException("微信退款回调地址未配置");
        CreateRequest request = new CreateRequest();
        request.setTransactionId(order.getTransactionId());
        request.setOutRefundNo(outRefundNo);
        request.setNotifyUrl(effectiveRefundNotifyUrl);
        if (reason != null && !reason.isBlank()) request.setReason(reason);
        AmountReq amount = new AmountReq();
        amount.setRefund(refundFen.longValue());
        amount.setTotal(totalFen.longValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        return refundService.create(request);
    }

    /** 商户退款单号查单，用于退款回调缺失时的人工/定时兜底。 */
    public Optional<Refund> queryRefundByOutRefundNo(String outRefundNo) {
        if (isMock()) return Optional.empty();
        if (refundService == null) throw new BusinessException("微信退款服务未初始化");
        if (isBlank(outRefundNo)) throw new BusinessException("缺少商户退款单号");
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);
        return Optional.ofNullable(refundService.queryByOutRefundNo(request));
    }

    /** 真实退款发起前的轻量配置校验，避免本地事务打开后才发现 SDK/回调配置缺失。 */
    public void assertRefundConfigured() {
        if (isMock()) return;
        if (refundService == null) throw new BusinessException("微信退款服务未初始化");
        if (notificationParser == null) throw new BusinessException("微信回调解析器未初始化");
        if (isBlank(effectiveRefundNotifyUrl())) throw new BusinessException("微信退款回调地址未配置");
    }

    /** 处理退款回调：验签 + AES-GCM 解密，返回解密后的退款通知。 */
    public RefundNotification handleRefundNotify(String serial, String nonce, String timestamp, String signature, String body) {
        if (isMock()) throw new BusinessException("mock 模式不应进入真实退款回调处理");
        if (notificationParser == null) throw new BusinessException("微信回调解析器未初始化");
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .timestamp(timestamp)
                .nonce(nonce)
                .signature(signature)
                .body(body)
                .build();
        return notificationParser.parse(requestParam, RefundNotification.class);
    }

    /** 元转分（Integer），HALF_UP。微信支付金额单位为分，必须 >0。 */
    public static Integer toFen(BigDecimal yuan) {
        if (yuan == null) return 0;
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** 商品描述：取首个商品名 + 订单号，截断 127 字符（微信限制）。 */
    private String buildDescription(MarketOrder order) {
        String first = order.getItems().isEmpty() ? "商城订单" : order.getItems().get(0).getItemName();
        String desc = "商城订单-" + first + "-" + order.getOrderNo();
        return desc.length() > 127 ? desc.substring(0, 127) : desc;
    }

    /** 按订单号查订单实体（回调按 out_trade_no 查）。 */
    public Optional<MarketOrder> findByOrderNo(String orderNo) { return orders.findByOrderNo(orderNo); }

    /** 按商户退款单号查订单实体（退款回调按 out_refund_no 查）。 */
    public Optional<MarketOrder> findByRefundNo(String refundNo) { return orders.findByRefundNo(refundNo); }

    private String effectiveRefundNotifyUrl() {
        if (!isBlank(refundNotifyUrl)) return refundNotifyUrl;
        if (!isBlank(notifyUrl) && notifyUrl.endsWith("/notify")) {
            return notifyUrl.substring(0, notifyUrl.length() - "/notify".length()) + "/refund-notify";
        }
        return refundNotifyUrl;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
