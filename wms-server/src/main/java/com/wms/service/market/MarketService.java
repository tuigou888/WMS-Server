package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.MarketProductRequest;
import com.wms.dto.market.MarketDtos.MarketCustomerRequest;
import com.wms.dto.market.MarketDtos.MarketOrderCreateRequest;
import com.wms.model.entity.*;
import com.wms.model.entity.market.*;
import com.wms.model.entity.market.MarketOrderStatus;
import com.wms.model.entity.market.MarketPayStatus;
import com.wms.model.entity.market.MarketPayType;
import com.wms.model.entity.market.MarketOrderAction;
import com.wms.repository.*;
import com.wms.repository.market.*;
import com.wms.service.DocumentNumberService;
import com.wms.service.InventoryCostCalculator;
import com.wms.service.InventoryReservationGuard;
import com.wms.service.TransactionType;
import com.wms.service.WarehouseAccessService;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MarketService {
    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final MarketProductRepository products;
    private final MarketCartRepository carts;
    private final MarketCustomerRepository customers;
    private final MarketOrderRepository orders;
    private final MarketOrderLogRepository orderLogs;
    private final MarketFavoriteRepository favorites;
    private final ItemRepository items;
    private final CategoryRepository categories;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventories;
    private final InventoryTransactionRepository inventoryTransactions;
    private final InventoryReservationRepository reservations;
    private final DocumentNumberService numbers;
    private final WechatPayService wechatPay;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final WarehouseAccessService warehouseAccess;
    private final InventoryReservationGuard reservationGuard;

    public MarketService(MarketProductRepository products, MarketCartRepository carts,
                         MarketCustomerRepository customers, MarketOrderRepository orders,
                         MarketOrderLogRepository orderLogs, MarketFavoriteRepository favorites,
                         ItemRepository items,
                         CategoryRepository categories, WarehouseRepository warehouses,
                         InventoryRepository inventories,
                         InventoryTransactionRepository inventoryTransactions,
                         InventoryReservationRepository reservations,
                         DocumentNumberService numbers,
                         @Lazy WechatPayService wechatPay,
                         PlatformTransactionManager transactionManager,
                         WarehouseAccessService warehouseAccess,
                         InventoryReservationGuard reservationGuard) {
        this.products = products; this.carts = carts;
        this.customers = customers; this.orders = orders; this.orderLogs = orderLogs;
        this.favorites = favorites;
        this.items = items; this.categories = categories;
        this.warehouses = warehouses; this.inventories = inventories;
        this.inventoryTransactions = inventoryTransactions;
        this.reservations = reservations;
        this.numbers = numbers;
        this.wechatPay = wechatPay;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.warehouseAccess = warehouseAccess;
        this.reservationGuard = reservationGuard;
    }

    // ======================== 商品管理（后台） ========================

    @Transactional
    public MarketProduct saveProduct(MarketProductRequest req) {
        Item item = items.findById(req.itemId())
                .orElseThrow(() -> new BusinessException("关联物品不存在"));
        if (products.existsByItemId(req.itemId())) {
            throw new BusinessException("该物品已上架，不能重复关联");
        }
        Category cat = req.categoryId() == null ? null
                : categories.findById(req.categoryId()).orElse(null);
        MarketProduct p = new MarketProduct(item, req.title(), req.salePrice(), req.marketPrice(), cat);
        p.setSubTitle(req.subTitle());
        p.setMainImage(req.mainImage());
        p.setGallery(req.gallery());
        p.setSortNo(req.sortNo());
        return products.save(p);
    }

    @Transactional
    public MarketProduct updateProduct(Long id, MarketProductRequest req) {
        // 必须走 join fetch：controller 的 view() 会读 item 字段，而 open-in-view 已关闭
        MarketProduct p = products.findDetailedById(id)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        Category cat = req.categoryId() == null ? null
                : categories.findById(req.categoryId()).orElse(null);
        p.setTitle(req.title());
        p.setSubTitle(req.subTitle());
        p.setMainImage(req.mainImage());
        p.setGallery(req.gallery());
        p.setSalePrice(req.salePrice());
        p.setMarketPrice(req.marketPrice());
        p.setCategory(cat);
        p.setSortNo(req.sortNo());
        return products.save(p);
    }

    @Transactional
    public MarketProduct changeStatus(Long id, String status) {
        MarketProduct p = products.findDetailedById(id)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        if (!Set.of("SHELF_ON", "SHELF_OFF").contains(status)) {
            throw new BusinessException("非法状态：" + status);
        }
        p.setStatus(status);
        return products.save(p);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!products.existsById(id)) throw new BusinessException("商品不存在");
        products.deleteById(id);
    }

    @Transactional
    public void incrementView(Long id) { products.incrementView(id); }

    @Transactional(readOnly = true)
    public BigDecimal available(Long itemId, Long warehouseId) {
        BigDecimal physical = inventories.availableQty(itemId, warehouseId);
        BigDecimal held = reservations.sumHeldQuantity(itemId, warehouseId, LocalDateTime.now());
        return physical.subtract(held).max(BigDecimal.ZERO);
    }

    // ======================== 购物车 ========================

    @Transactional
    public MarketCart addCart(UserAccount user, Long productId, Integer quantity) {
        MarketProduct product = products.findDetailedById(productId)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        if (!"SHELF_ON".equals(product.getStatus())) throw new BusinessException("商品已下架");
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;
        return carts.findByUserIdAndProductId(user.getId(), productId)
                .map(c -> { c.setQuantity(c.getQuantity() + qty); return carts.save(c); })
                .orElseGet(() -> carts.save(new MarketCart(user, product, qty, product.getSalePrice())));
    }

    @Transactional
    public MarketCart updateCartQty(UserAccount user, Long cartId, Integer quantity) {
        if (quantity == null || quantity <= 0) throw new BusinessException("数量必须大于0");
        MarketCart c = carts.findDetailedById(cartId).orElseThrow(() -> new BusinessException("购物车项不存在"));
        if (!Objects.equals(c.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        c.setQuantity(quantity);
        return carts.save(c);
    }

    @Transactional
    public void removeCart(UserAccount user, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) return;
        carts.deleteByIds(user.getId(), cartIds);
    }

    @Transactional
    public void clearCart(UserAccount user) { carts.deleteByUserId(user.getId()); }

    // ======================== 收货人档案 ========================

    @Transactional
    public MarketCustomer saveCustomer(UserAccount user, MarketCustomerRequest req) {
        MarketCustomer c = new MarketCustomer(user, req.name(), req.phone(), req.address());
        c.setRemark(req.remark());
        if (user != null) {
            boolean def = Boolean.TRUE.equals(req.defaultFlag()) || customers.countByUserId(user.getId()) == 0;
            if (def) {
                customers.findFirstByUserIdAndDefaultFlagTrue(user.getId())
                        .ifPresent(old -> { old.setDefaultFlag(false); customers.save(old); });
            }
            c.setDefaultFlag(def);
        } else c.setDefaultFlag(false);
        return customers.save(c);
    }

    @Transactional
    public MarketCustomer updateCustomer(UserAccount user, Long id, MarketCustomerRequest req) {
        MarketCustomer c = customers.findById(id).orElseThrow(() -> new BusinessException("收货人不存在"));
        if (user != null && (c.getUser() == null || !Objects.equals(c.getUser().getId(), user.getId()))) throw new BusinessException("无权操作");
        c.setName(req.name()); c.setPhone(req.phone()); c.setAddress(req.address()); c.setRemark(req.remark());
        if (user == null) {
            c.setDefaultFlag(false);
        } else if (Boolean.TRUE.equals(req.defaultFlag())) {
            customers.findFirstByUserIdAndDefaultFlagTrue(user.getId())
                    .filter(x -> !Objects.equals(x.getId(), id))
                    .ifPresent(old -> { old.setDefaultFlag(false); customers.save(old); });
            c.setDefaultFlag(true);
        }
        return customers.save(c);
    }

    @Transactional
    public void deleteCustomer(UserAccount user, Long id) {
        MarketCustomer c = customers.findById(id).orElseThrow(() -> new BusinessException("收货人不存在"));
        if (user != null && (c.getUser() == null || !Objects.equals(c.getUser().getId(), user.getId()))) throw new BusinessException("无权操作");
        customers.delete(c);
    }

    @Transactional
    public long countMarketCustomers() { return customers.count(); }

    @Transactional
    public void deleteMarketCustomers(Long... ids) {
        for (Long id : ids) {
            customers.deleteById(id);
        }
    }

    // ======================== 下单 ========================

    @Transactional
    public MarketOrder createOrder(UserAccount user, MarketOrderCreateRequest req) {
        MarketCustomer customer = customers.findById(req.customerId())
                .orElseThrow(() -> new BusinessException("收货人不存在"));
        if (customer.getUser() == null || !Objects.equals(customer.getUser().getId(), user.getId())) throw new BusinessException("无权使用该收货人");
        Warehouse warehouse = warehouses.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException("仓库不存在"));
        if (!Boolean.TRUE.equals(warehouse.getStatus())) throw new BusinessException("仓库已禁用");

        List<MarketCart> cartList = carts.findByUserIdOrderByIdDesc(user.getId());
        if (cartList.isEmpty()) throw new BusinessException("购物车为空");

        MarketOrder order = new MarketOrder();
        order.setOrderNo(numbers.next("MO"));
        order.setUser(user);
        order.setReceiverName(customer.getName());
        order.setReceiverPhone(customer.getPhone());
        order.setReceiverAddress(customer.getAddress());
        order.setWarehouse(warehouse);
        order.setPayType(req.payType() == null ? MarketPayType.PAY_ONLINE : MarketPayType.from(req.payType()));
        order.setRemark(req.remark());
        order.setOrderStatus(MarketOrderStatus.PENDING);
        order.setPayStatus(MarketPayStatus.UNPAID);

        BigDecimal total = BigDecimal.ZERO;
        List<MarketOrderItem> orderItems = new ArrayList<>();
        for (MarketCart cart : cartList) {
            MarketProduct product = cart.getProduct();
            if (!"SHELF_ON".equals(product.getStatus())) throw new BusinessException("商品已下架：" + product.getTitle());
            // P2-1：下单使用购物车快照价（加购时价格），而非当前商品售价，使快照机制生效
            BigDecimal price = cart.getSnapshotPrice() == null ? product.getSalePrice() : cart.getSnapshotPrice();
            if (price == null) price = BigDecimal.ZERO;
            BigDecimal sub = price.multiply(BigDecimal.valueOf(cart.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            total = total.add(sub);
            MarketOrderItem oi = new MarketOrderItem();
            oi.setOrder(order);
            oi.setItem(product.getItem());
            oi.setProductId(product.getId());
            oi.setItemName(product.getTitle());
            oi.setItemCode(product.getItem().getCode());
            oi.setUnit(product.getItem().getUnit());
            oi.setSalePrice(price);
            oi.setQuantity(BigDecimal.valueOf(cart.getQuantity()));
            oi.setSubtotal(sub);
            orderItems.add(oi);
        }
        order.replaceItems(orderItems);
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        MarketOrder saved = orders.save(order);
        holdInventory(saved);
        orderLogs.save(new MarketOrderLog(saved.getId(), MarketOrderAction.CREATE, user.getUsername(), "创建订单"));
        // P2-2：下单成功后即清空购物车（发货发生在很久之后，不应在 ship() 中清空）
        carts.deleteByUserId(user.getId());
        return saved;
    }

    /** 拉起支付参数（小程序 requestPayment 所需）。真实模式调微信下单，mock 模式返回模拟参数。
     *  P1-2：仅以 readOnly 事务查询订单，微信下单 HTTP 调用在事务外完成，避免长事务持锁。 */
    public Map<String, Object> prepay(UserAccount user, Long orderId) {
        MarketOrder order = transactionTemplate.execute(status -> {
            MarketOrder found = orders.findDetailedById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
            if (!Objects.equals(found.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
            if (!MarketOrderStatus.PENDING.equals(found.getOrderStatus()) && !MarketOrderStatus.AUDITED.equals(found.getOrderStatus())) throw new BusinessException("当前订单状态不可支付");
            if (!MarketPayStatus.UNPAID.equals(found.getPayStatus())) throw new BusinessException("当前支付状态不可支付");
            assertReservationPayable(found, reservations.findByOrderIdOrderByIdAsc(found.getId()), LocalDateTime.now());
            return found;
        });
        return wechatPay.prepay(order, user);
    }

    /** mock 模式专用：跳过 requestPayment 直接确认支付落单。 */
    @Transactional
    public MarketOrder confirmMockPay(UserAccount user, Long orderId) {
        if (!wechatPay.isMock()) throw new BusinessException("非 mock 模式请走微信支付拉起流程");
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!Objects.equals(order.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        if (!MarketOrderStatus.PENDING.equals(order.getOrderStatus())) throw new BusinessException("当前订单状态不可支付");
        return markPaid(order.getId(), "MOCK_" + order.getOrderNo(), user.getUsername());
    }

    /**
     * 支付成功落单（回调与模拟支付共用）。幂等：已 PAID 直接返回。
     * 执行：扣减库存（FIFO 出库）+ 状态置 AUDITED/PAID + 回填 transactionId/paidAt + 写 PAY 日志。
     * 内部用 findForUpdateById 加悲观锁，应对并发回调。
     */
    @Transactional
    public MarketOrder markPaid(Long orderId, String transactionId, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        return markPaid(order, transactionId, operator);
    }

    /** markPaid 的内部实现，调用方须已对订单加锁（findForUpdateById）。 */
    private MarketOrder markPaid(MarketOrder order, String transactionId, String operator) {
        // 幂等校验：微信可能重复回调，已支付的订单不重复扣库存
        if (MarketPayStatus.PAID.equals(order.getPayStatus())) {
            log.info("订单 {} 已支付，跳过重复处理（transactionId={}）", order.getId(), transactionId);
            return order;
        }
        MarketOrderStatus st = order.getOrderStatus();
        if (MarketOrderStatus.CANCELLED.equals(st)) {
            // 用户取消与微信回调无法共享同一事务；回调晚到时必须保留资金事实，且不能扣已释放的库存。
            order.setPayStatus(MarketPayStatus.PAID); order.setTransactionId(transactionId); order.setPaidAt(LocalDateTime.now());
            order.setRefundReason("订单取消后收到支付回调，等待自动退款");
            MarketOrder saved=orders.save(order);
            orderLogs.save(new MarketOrderLog(saved.getId(),MarketOrderAction.PAY,operator,"取消后支付，未扣库存，等待自动退款，交易号："+transactionId));
            log.warn("订单 {} 取消后收到支付回调，已登记退款任务", order.getId());
            return saved;
        }
        if (MarketOrderStatus.REJECTED.equals(st)) {
            throw new BusinessException("订单已拒绝，支付回调需人工核实退款");
        }
        List<InventoryReservation> held=reservations.findByOrderIdOrderByIdAsc(order.getId());
        assertReservationPayable(order,held,LocalDateTime.now());
        deductStock(order, "支付扣库存");
        held=reservations.findByOrderIdForUpdate(order.getId());
        assertReservationPayable(order,held,LocalDateTime.now());
        consumeReservations(held);
        order.setOrderStatus(MarketOrderStatus.AUDITED);
        order.setPayStatus(MarketPayStatus.PAID);
        order.setTransactionId(transactionId);
        order.setPaidAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(saved.getId(), MarketOrderAction.PAY, operator, "完成支付，交易号：" + transactionId));
        return saved;
    }

    /** 兼容旧接口：模拟支付入口（仅 mock 模式可用，真实模式请用 prepay）。 */
    @Transactional
    public MarketOrder pay(UserAccount user, Long orderId, String operator) {
        if (!wechatPay.isMock()) {
            throw new BusinessException("真实支付模式请调用 prepay 拉起微信支付");
        }
        return confirmMockPay(user, orderId);
    }

    /** 管理员发起退款。真实模式仅记录 REFUNDING，资金成功回调后才回滚库存并置 REFUNDED。 */
    public MarketOrder refund(Long orderId, String reason, String operator) {
        boolean mock = wechatPay.isMock();
        if (!mock) wechatPay.assertRefundConfigured();
        TransactionTemplate tx = mock ? transactionTemplate : requiresNewTransactionTemplate;
        MarketOrder prepared = tx.execute(status -> prepareRefundRequest(orderId, reason, operator, mock));
        if (prepared == null) throw new BusinessException("退款申请准备失败");
        if (mock) {
            MarketOrder refunded = tx.execute(status -> finalizeRefundByNoLocked(prepared.getRefundNo(), null, operator));
            if (refunded == null) throw new BusinessException("退款完成失败");
            return refunded;
        }
        Refund refundResp;
        try {
            refundResp = wechatPay.refund(prepared, reason, prepared.getRefundNo());
        } catch (RuntimeException e) {
            log.error("微信退款申请失败，订单已保留 REFUNDING 状态以便按退款单号查询：orderId={}, refundNo={}", prepared.getId(), prepared.getRefundNo(), e);
            throw e;
        }
        if (refundResp != null && Status.SUCCESS.equals(refundResp.getStatus())) {
            MarketOrder refunded = requiresNewTransactionTemplate.execute(status -> finalizeRefundByNoLocked(prepared.getRefundNo(), refundResp.getTransactionId(), operator));
            if (refunded == null) throw new BusinessException("退款完成失败");
            return refunded;
        }
        return orders.findDetailedById(prepared.getId()).orElse(prepared);
    }

    private MarketOrder prepareRefundRequest(Long orderId, String reason, String operator, boolean mock) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (MarketPayStatus.REFUNDING.equals(order.getPayStatus())) throw new BusinessException("退款处理中，请勿重复发起");
        if (MarketPayStatus.REFUNDED.equals(order.getPayStatus())) throw new BusinessException("订单已退款");
        if (!MarketPayStatus.PAID.equals(order.getPayStatus())) throw new BusinessException("订单未支付，无法退款");
        BigDecimal refundAmount = order.getTotalAmount();
        if (refundAmount == null || refundAmount.signum() <= 0) throw new BusinessException("退款金额必须大于0");
        BigDecimal alreadyRefunded = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        if (alreadyRefunded.add(refundAmount).compareTo(order.getTotalAmount()) > 0) {
            throw new BusinessException("累计退款金额超过订单总额");
        }
        order.setRefundReason(reason == null ? "管理员退款" : reason);
        String refundNo = numbers.next("MRF");
        order.setRefundNo(refundNo);
        order.setRefundAmount(refundAmount);
        if (!mock) {
            order.setPayStatus(MarketPayStatus.REFUNDING);
        }
        MarketOrder saved = orders.save(order);
        if (MarketPayStatus.REFUNDING.equals(saved.getPayStatus())) {
            orderLogs.save(new MarketOrderLog(orderId, MarketOrderAction.REFUND, operator,
                    "发起退款：" + (reason == null ? "" : reason) + "，退款单号：" + saved.getRefundNo()));
        }
        return saved;
    }

    /** 微信退款成功回调/查单共用的幂等最终化逻辑，只有 SUCCESS 才能调用。 */
    @Transactional
    public MarketOrder finalizeRefundByNo(String refundNo, String transactionId, String operator) {
        return finalizeRefundByNoLocked(refundNo, transactionId, operator);
    }

    private MarketOrder finalizeRefundByNoLocked(String refundNo, String transactionId, String operator) {
        MarketOrder order = orders.findByRefundNoForUpdate(refundNo)
                .orElseThrow(() -> new BusinessException("退款单不存在"));
        if (!Objects.equals(refundNo, order.getRefundNo())) throw new BusinessException("退款单号不匹配");
        if (transactionId != null && order.getTransactionId() != null
                && !Objects.equals(transactionId, order.getTransactionId())) throw new BusinessException("退款原支付单号不匹配");
        if (MarketPayStatus.REFUNDED.equals(order.getPayStatus())) return order;
        if (!MarketPayStatus.REFUNDING.equals(order.getPayStatus()) && !MarketPayStatus.PAID.equals(order.getPayStatus())) {
            throw new BusinessException("当前状态不可完成退款：" + order.getPayStatus());
        }
        finalizeRefund(order, operator);
        return orders.save(order);
    }

    /** 微信退款 CLOSED/ABNORMAL 结果处理：不回滚库存，恢复 PAID 以允许管理员重新发起退款。 */
    @Transactional
    public MarketOrder markRefundFailedByNo(String refundNo, String operator, String reason) {
        MarketOrder order = orders.findByRefundNoForUpdate(refundNo)
                .orElseThrow(() -> new BusinessException("退款单不存在"));
        if (MarketPayStatus.REFUNDED.equals(order.getPayStatus())) return order;
        if (MarketPayStatus.REFUNDING.equals(order.getPayStatus())) {
            order.setPayStatus(MarketPayStatus.PAID);
            MarketOrder saved = orders.save(order);
            orderLogs.save(new MarketOrderLog(order.getId(), MarketOrderAction.REFUND, operator,
                    "退款失败，未回滚库存：" + (reason == null ? "未知原因" : reason)));
            return saved;
        }
        return order;
    }

    private void finalizeRefund(MarketOrder order, String operator) {
        if (MarketPayStatus.REFUNDED.equals(order.getPayStatus())) return;
        if (!inventoryTransactions.findByReferenceNoAndType(order.getOrderNo(),TransactionType.OUT).isEmpty()) rollbackStock(order, "退款回滚库存");
        order.setPayStatus(MarketPayStatus.REFUNDED);
        order.setRefundedAt(LocalDateTime.now());
        orderLogs.save(new MarketOrderLog(order.getId(), MarketOrderAction.REFUND, operator, "退款成功"));
    }

    @Transactional
    public void cancelByUser(UserAccount user, Long orderId) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!Objects.equals(order.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        if (!MarketOrderStatus.PENDING.equals(order.getOrderStatus())) throw new BusinessException("已审核订单请走客服");
        releaseReservations(reservations.findByOrderIdForUpdate(order.getId()),InventoryReservationStatus.RELEASED);
        order.setOrderStatus(MarketOrderStatus.CANCELLED);
        order.setCancelReason("用户主动取消");
        order.setCancelledAt(LocalDateTime.now());
        orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, MarketOrderAction.CANCEL, user.getUsername(), "用户取消"));
    }

    @Transactional
    public MarketOrder audit(Long orderId, boolean approve, String remark, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!MarketOrderStatus.PENDING.equals(order.getOrderStatus())) throw new BusinessException("当前订单状态不可审核");
        // P0-1：在线支付订单的库存由支付回调 markPaid 扣减，人工审核不应重复扣库存；
        // 仅货到付款等非在线支付订单走审核扣库存路径。
        boolean isPayOnline = MarketPayType.PAY_ONLINE.equals(order.getPayType());
        if (approve) {
            if (!isPayOnline) {
                // 非在线支付订单：审核通过时扣减库存（FIFO 出库）
                List<InventoryReservation> held=reservations.findByOrderIdOrderByIdAsc(order.getId());
                assertReservationPayable(order,held,LocalDateTime.now());
                deductStock(order, "审核扣库存");
                held=reservations.findByOrderIdForUpdate(order.getId());
                assertReservationPayable(order,held,LocalDateTime.now());
                consumeReservations(held);
            }
            order.setOrderStatus(MarketOrderStatus.AUDITED);
            order.setReviewer(operator);
            order.setReviewRemark(remark);
            // 非在线支付订单审核即视为已收款；在线支付订单的 payStatus 由回调 markPaid 置 PAID
            if (!isPayOnline) order.setPayStatus(MarketPayStatus.PAID);
            if (order.getAuditedAt() == null) order.setAuditedAt(LocalDateTime.now());
            if (!isPayOnline && order.getPaidAt() == null) order.setPaidAt(LocalDateTime.now());
        } else {
            releaseReservations(reservations.findByOrderIdForUpdate(order.getId()),InventoryReservationStatus.RELEASED);
            order.setOrderStatus(MarketOrderStatus.REJECTED);
            order.setCancelReason(remark == null ? "审核拒绝" : remark);
            order.setCancelledAt(LocalDateTime.now());
        }
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, MarketOrderAction.AUDIT, operator,
                (approve ? "审核通过" : "审核拒绝") + (remark == null ? "" : "：" + remark)));
        return saved;
    }

    @Transactional
    public MarketOrder ship(Long orderId, String logisticsCompany, String logisticsNumber, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        warehouseAccess.require(order.getWarehouse().getId());
        if (!MarketOrderStatus.AUDITED.equals(order.getOrderStatus())) throw new BusinessException("订单未审核通过，不能发货");
        if (!MarketPayStatus.PAID.equals(order.getPayStatus())) throw new BusinessException("订单未支付，不能发货");
        // 库存已在审核/支付阶段扣减（deductStock），此处仅更新物流与销量
        for (MarketOrderItem oi : order.getItems()) {
            products.incrementSalesByItemId(oi.getItem().getId(), oi.getQuantity().longValue());
        }

        order.setOrderStatus(MarketOrderStatus.SHIPPED);
        order.setLogisticsCompany(logisticsCompany);
        order.setLogisticsNumber(logisticsNumber);
        order.setShippedAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, MarketOrderAction.SHIP, operator,
                "发货：" + (logisticsCompany == null ? "" : logisticsCompany) + " " + (logisticsNumber == null ? "" : logisticsNumber)));
        return saved;
    }

    @Transactional
    public MarketOrder complete(Long orderId, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        warehouseAccess.require(order.getWarehouse().getId());
        return completeLocked(order, operator);
    }

    /** 用户端确认收货必须校验订单归属，避免仅凭订单 ID 完成其他用户订单。 */
    @Transactional
    public MarketOrder completeByUser(UserAccount user, Long orderId) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (order.getUser() == null || !Objects.equals(order.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        return completeLocked(order, user.getUsername());
    }

    private MarketOrder completeLocked(MarketOrder order, String operator) {
        if (!MarketOrderStatus.SHIPPED.equals(order.getOrderStatus())) throw new BusinessException("订单未发货，不能完成");
        order.setOrderStatus(MarketOrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(order.getId(), MarketOrderAction.COMPLETE, operator, "确认收货"));
        return saved;
    }

    @Transactional
    public MarketOrder forceCancel(Long orderId, String reason, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (MarketOrderStatus.COMPLETED.equals(order.getOrderStatus()) || MarketOrderStatus.CANCELLED.equals(order.getOrderStatus())) {
            throw new BusinessException("订单已完成或已取消");
        }
        // P2-3：已发货订单不应直接取消（货物可能已被签收，回滚库存语义不对），需走退款流程
        if (MarketOrderStatus.SHIPPED.equals(order.getOrderStatus())) {
            throw new BusinessException("已发货订单不可直接取消，请走退款流程");
        }
        // 线上已支付单必须等退款成功后才回滚库存；货到付款单没有第三方资金退款，取消时立即回滚。
        MarketOrderStatus prevStatus = order.getOrderStatus();
        if (MarketOrderStatus.AUDITED.equals(prevStatus) && !MarketPayType.PAY_ONLINE.equals(order.getPayType())) {
            rollbackStock(order, "取消回滚库存");
        }
        releaseReservations(reservations.findByOrderIdForUpdate(order.getId()),InventoryReservationStatus.RELEASED);
        order.setOrderStatus(MarketOrderStatus.CANCELLED);
        order.setCancelReason(reason == null ? "管理员取消" : reason);
        order.setCancelledAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, MarketOrderAction.CANCEL, operator, "管理员取消：" + reason));
        return saved;
    }

    // ======================== 库存预占/支付取消（内部） ========================

    /** 下单事务内先锁物理库存行，再读取已提交预占，避免并发下单超卖。 */
    private void holdInventory(MarketOrder order) {
        LocalDateTime now=LocalDateTime.now(),expiresAt=now.plusMinutes(30); Map<Long,BigDecimal> quantities=new LinkedHashMap<>();Map<Long,Item> orderItems=new LinkedHashMap<>();
        for(MarketOrderItem line:order.getItems()){quantities.merge(line.getItem().getId(),line.getQuantity(),BigDecimal::add);orderItems.put(line.getItem().getId(),line.getItem());}
        for(Map.Entry<Long,BigDecimal> entry:quantities.entrySet()){Long itemId=entry.getKey();BigDecimal requested=entry.getValue();InventoryReservationGuard.Snapshot protection=reservationGuard.lock(itemId,order.getWarehouse().getId());reservationGuard.requireAvailable(protection,null,requested,"库存不足："+orderItems.get(itemId).getName()+"（可售 "+protection.availableExcluding(null).stripTrailingZeros().toPlainString()+"）");reservations.save(new InventoryReservation(order,orderItems.get(itemId),order.getWarehouse(),requested,expiresAt));}
    }
    /** 新订单必须整单存在有效 HELD 预占；仅为上线前历史待支付单保留无预占兼容路径。 */
    private void assertReservationPayable(MarketOrder order,List<InventoryReservation> held,LocalDateTime now){if(held.isEmpty()){log.warn("订单 {} 没有库存预占，按历史订单兼容路径处理",order.getOrderNo());return;}Map<Long,BigDecimal> expected=new LinkedHashMap<>(),actual=new LinkedHashMap<>();for(MarketOrderItem line:order.getItems())expected.merge(line.getItem().getId(),line.getQuantity(),BigDecimal::add);for(InventoryReservation r:held){if(!InventoryReservationStatus.HELD.equals(r.getStatus())||r.getExpiresAt()==null||!r.getExpiresAt().isAfter(now))throw new BusinessException("订单库存预占已失效，请重新下单");actual.merge(r.getItem().getId(),r.getQuantity(),BigDecimal::add);}if(expected.size()!=actual.size()||expected.entrySet().stream().anyMatch(e->actual.get(e.getKey())==null||e.getValue().compareTo(actual.get(e.getKey()))!=0))throw new BusinessException("订单库存预占明细不完整，无法支付");}
    private void consumeReservations(List<InventoryReservation> held){LocalDateTime now=LocalDateTime.now();for(InventoryReservation r:held){r.setStatus(InventoryReservationStatus.CONSUMED);r.setConsumedAt(now);}}
    private void releaseReservations(List<InventoryReservation> held,InventoryReservationStatus target){LocalDateTime now=LocalDateTime.now();for(InventoryReservation r:held)if(InventoryReservationStatus.HELD.equals(r.getStatus())){r.setStatus(target);r.setReleasedAt(now);}}
    /** 到期订单不再占用可售库存；支付窗口已过的未支付订单同时关闭。 */
    @Scheduled(fixedDelayString="${market.reservation.expire-scan-ms:60000}") @Transactional public void expireReservations(){LocalDateTime now=LocalDateTime.now();for(InventoryReservation candidate:reservations.findExpiredHeld(now)){MarketOrder order=orders.findForUpdateById(candidate.getOrder().getId()).orElse(null);if(order==null)continue;List<InventoryReservation> held=reservations.findByOrderIdForUpdate(order.getId());boolean hasExpired=held.stream().anyMatch(r->InventoryReservationStatus.HELD.equals(r.getStatus())&&!r.getExpiresAt().isAfter(now));if(!hasExpired)continue;releaseReservations(held,InventoryReservationStatus.EXPIRED);if(MarketPayStatus.UNPAID.equals(order.getPayStatus())&&(MarketOrderStatus.PENDING.equals(order.getOrderStatus())||MarketOrderStatus.AUDITED.equals(order.getOrderStatus()))){order.setOrderStatus(MarketOrderStatus.CANCELLED);order.setCancelReason("支付超时，库存预占已释放");order.setCancelledAt(now);orders.save(order);orderLogs.save(new MarketOrderLog(order.getId(),MarketOrderAction.CANCEL,"system","支付超时取消并释放库存预占"));}}}
    /** 取消后到达的线上支付回调会先登记 PAID，再由该任务安全发起退款；失败保留 PAID 以便重试。 */
    @Scheduled(fixedDelayString="${market.cancelled-payment-refund-scan-ms:30000}",initialDelayString="${market.cancelled-payment-refund-initial-delay-ms:60000}") public void refundCancelledPaidOrders(){for(Long id:orders.findCancelledPaidOnlineOrderIds())try{refund(id,"订单取消后支付自动退款","system");}catch(RuntimeException e){log.error("取消后支付订单自动退款失败，稍后重试：orderId={}",id,e);}}

    // ======================== 库存扣减/回滚（内部） ========================

    /** FIFO 扣减订单库存，写入 OUT 流水。库存不足抛异常整体回滚。 */
    private void deductStock(MarketOrder order, String remark) {
        Warehouse warehouse = order.getWarehouse();
        for (MarketOrderItem oi : order.getItems()) {
            BigDecimal remaining = oi.getQuantity();
            InventoryReservationGuard.Snapshot protection = reservationGuard.lock(oi.getItem().getId(), warehouse.getId());
            reservationGuard.requireAvailable(protection, order.getId(), remaining, "库存不足或其他订单已预占：" + oi.getItemName());
            List<Inventory> lots = protection.lots();
            for (Inventory inv : lots) {
                if (remaining.signum() <= 0) break;
                BigDecimal take = inv.getQuantity().min(remaining);
                BigDecimal newQty = inv.getQuantity().subtract(take);
                BigDecimal newAmount = inv.getAvgCost().multiply(newQty);
                BigDecimal cost = inv.getAvgCost().multiply(take);
                inv.setQuantity(newQty);
                inv.setTotalAmount(newAmount);
                inventories.save(inv);

                BigDecimal saleAmount = oi.getSalePrice().multiply(take).setScale(2, RoundingMode.HALF_UP);
                BigDecimal profit = saleAmount.subtract(cost).setScale(2, RoundingMode.HALF_UP);

                InventoryTransaction tx = new InventoryTransaction();
                tx.setItem(oi.getItem()); tx.setWarehouse(warehouse); tx.setLocation(inv.getLocation()); tx.setBatchNo(inv.getBatchNo());
                tx.setTransactionType(TransactionType.OUT); tx.setReferenceNo(order.getOrderNo());
                tx.setRemark("商城" + remark + "：" + order.getOrderNo());
                tx.setQuantity(take.negate()); tx.setUnitCost(inv.getAvgCost()); tx.setTotalCostAmount(cost);
                tx.setSalePrice(oi.getSalePrice()); tx.setSaleAmount(saleAmount); tx.setProfit(profit);
                tx.setBalanceQuantity(newQty); tx.setBalanceAmount(newAmount); tx.setAvgCostAfter(inv.getAvgCost());
                inventoryTransactions.save(tx);
                remaining = remaining.subtract(take);
            }
        }
    }

    /** 按原商城 OUT 流水的库位、批次、数量和成本逐笔回滚，避免恢复到错误库存批次。 */
    private void rollbackStock(MarketOrder order, String remark) {
        Warehouse warehouse = order.getWarehouse();
        List<InventoryTransaction> outTxs = inventoryTransactions.findByReferenceNoAndType(order.getOrderNo(), TransactionType.OUT);
        Map<Long, java.util.LinkedList<InventoryTransaction>> outTxByItem = outTxs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.getItem().getId(), java.util.stream.Collectors.toCollection(java.util.LinkedList::new)));
        for (MarketOrderItem oi : order.getItems()) {
            java.util.LinkedList<InventoryTransaction> itemOutTxs = outTxByItem.get(oi.getItem().getId());
            BigDecimal remaining = oi.getQuantity();
            if (itemOutTxs == null) throw new BusinessException("订单缺少原始出库流水，无法回滚库存：" + oi.getItemCode());
            for (InventoryTransaction outTx : itemOutTxs) {
                if (remaining.signum() <= 0) break;
                BigDecimal addQty = outTx.getQuantity() == null ? BigDecimal.ZERO : outTx.getQuantity().abs().min(remaining);
                if (addQty.signum() <= 0) continue;
                Inventory inv = inventories.findForUpdate(oi.getItem().getId(), warehouse.getId(),
                                outTx.getLocation() == null ? null : outTx.getLocation().getId(), outTx.getBatchNo())
                        .orElseThrow(() -> new BusinessException("原出库库存记录不存在，无法回滚：" + oi.getItemCode()));
                BigDecimal unitCost = outTx.getUnitCost() == null ? BigDecimal.ZERO : outTx.getUnitCost();
                BigDecimal addCost = outTx.getTotalCostAmount() == null
                        ? unitCost.multiply(addQty).setScale(2, RoundingMode.HALF_UP)
                        : outTx.getTotalCostAmount().multiply(addQty).divide(outTx.getQuantity().abs(), 2, RoundingMode.HALF_UP);
                BigDecimal newQty = inv.getQuantity().add(addQty);
                BigDecimal newAmount = inv.getTotalAmount().add(addCost).setScale(2, RoundingMode.HALF_UP);
                BigDecimal newAvg = InventoryCostCalculator.averageCost(inv.getQuantity(), inv.getTotalAmount(), addQty, addCost);
                inv.setQuantity(newQty); inv.setTotalAmount(newAmount); inv.setAvgCost(newAvg); inv.setLastInCost(unitCost);
                inventories.save(inv);

                InventoryTransaction tx = new InventoryTransaction();
                tx.setItem(oi.getItem()); tx.setWarehouse(warehouse); tx.setLocation(outTx.getLocation()); tx.setBatchNo(outTx.getBatchNo());
                tx.setTransactionType(TransactionType.IN); tx.setReferenceNo(order.getOrderNo());
                tx.setRemark("商城" + remark + "：" + order.getOrderNo());
                tx.setQuantity(addQty); tx.setUnitCost(unitCost); tx.setTotalCostAmount(addCost);
                tx.setBalanceQuantity(newQty); tx.setBalanceAmount(newAmount); tx.setAvgCostAfter(newAvg);
                inventoryTransactions.save(tx);
                remaining = remaining.subtract(addQty);
            }
            if (remaining.signum() > 0) throw new BusinessException("原始出库流水数量不足，无法完整回滚库存：" + oi.getItemCode());
        }
    }

    // ======================== 商品收藏（小程序） ========================

    /** 收藏或取消收藏商品（toggle）。返回 true=已收藏，false=已取消。
     *  刻意不加 @Transactional：exists 只是快路径，并发下插入仍可能撞唯一约束，冲突时必须让插入自身回滚。
     *  若挂在调用方的事务上，异常会把该事务标记为 rollback-only，catch 住后提交仍抛 UnexpectedRollbackException。 */
    public boolean toggleFavorite(UserAccount user, Long productId) {
        MarketProduct product = products.findById(productId).orElseThrow(() -> new BusinessException("商品不存在"));
        Long userId = user.getId();
        if (favorites.existsByUserIdAndProductId(userId, productId)) {
            transactionTemplate.executeWithoutResult(status -> favorites.deleteByUserAndProduct(userId, productId));
            return false;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> favorites.save(new MarketFavorite(user, product)));
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发场景：另一请求已收藏，视为已收藏
            return true;
        }
    }

    /** 是否已收藏。 */
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long productId) {
        return favorites.existsByUserIdAndProductId(userId, productId);
    }

    /** 我的收藏分页（按收藏时间倒序，过滤已下架商品避免懒加载孤儿异常）。 */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MarketFavorite> myFavorites(Long userId, int page, int pageSize) {
        return favorites.findByUserIdOrderByIdDesc(userId, PageRequest.of(Math.max(0, page - 1), Math.min(50, Math.max(1, pageSize))));
    }
}
