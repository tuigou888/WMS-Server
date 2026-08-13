package com.wms.service.market;

import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.MarketProductRequest;
import com.wms.dto.market.MarketDtos.MarketCustomerRequest;
import com.wms.dto.market.MarketDtos.MarketOrderCreateRequest;
import com.wms.model.entity.*;
import com.wms.model.entity.market.*;
import com.wms.repository.*;
import com.wms.repository.market.*;
import com.wms.service.DocumentNumberService;
import com.wms.service.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ItemRepository items;
    private final CategoryRepository categories;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventories;
    private final InventoryTransactionRepository inventoryTransactions;
    private final DocumentNumberService numbers;

    public MarketService(MarketProductRepository products, MarketCartRepository carts,
                         MarketCustomerRepository customers, MarketOrderRepository orders,
                         MarketOrderLogRepository orderLogs, ItemRepository items,
                         CategoryRepository categories, WarehouseRepository warehouses,
                         InventoryRepository inventories,
                         InventoryTransactionRepository inventoryTransactions,
                         DocumentNumberService numbers) {
        this.products = products; this.carts = carts;
        this.customers = customers; this.orders = orders; this.orderLogs = orderLogs;
        this.items = items; this.categories = categories;
        this.warehouses = warehouses; this.inventories = inventories;
        this.inventoryTransactions = inventoryTransactions;
        this.numbers = numbers;
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
        MarketProduct p = products.findById(id)
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
        MarketProduct p = products.findById(id)
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
        return inventories.availableQty(itemId, warehouseId);
    }

    // ======================== 购物车 ========================

    @Transactional
    public MarketCart addCart(UserAccount user, Long productId, Integer quantity) {
        MarketProduct product = products.findById(productId)
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
        MarketCart c = carts.findById(cartId).orElseThrow(() -> new BusinessException("购物车项不存在"));
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
        boolean def = Boolean.TRUE.equals(req.defaultFlag()) || (user != null && customers.countByUserId(user.getId()) == 0);
        if (def) {
            customers.findFirstByUserIdAndDefaultFlagTrue(user.getId())
                    .ifPresent(old -> { old.setDefaultFlag(false); customers.save(old); });
        }
        c.setDefaultFlag(def);
        return customers.save(c);
    }

    @Transactional
    public MarketCustomer updateCustomer(UserAccount user, Long id, MarketCustomerRequest req) {
        MarketCustomer c = customers.findById(id).orElseThrow(() -> new BusinessException("收货人不存在"));
        if (user != null && !Objects.equals(c.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        c.setName(req.name()); c.setPhone(req.phone()); c.setAddress(req.address()); c.setRemark(req.remark());
        if (Boolean.TRUE.equals(req.defaultFlag())) {
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
        if (user != null && !Objects.equals(c.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
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
        if (!Objects.equals(customer.getUser().getId(), user.getId())) throw new BusinessException("无权使用该收货人");
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
        order.setPayType(req.payType());
        order.setRemark(req.remark());
        order.setOrderStatus("PENDING");
        order.setPayStatus("UNPAID");

        BigDecimal total = BigDecimal.ZERO;
        List<MarketOrderItem> orderItems = new ArrayList<>();
        for (MarketCart cart : cartList) {
            MarketProduct product = cart.getProduct();
            if (!"SHELF_ON".equals(product.getStatus())) throw new BusinessException("商品已下架：" + product.getTitle());
            BigDecimal price = product.getSalePrice() == null ? BigDecimal.ZERO : product.getSalePrice();
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
        orderLogs.save(new MarketOrderLog(saved.getId(), "CREATE", user.getUsername(), "创建订单"));
        return saved;
    }

    @Transactional
    public MarketOrder pay(UserAccount user, Long orderId, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!Objects.equals(order.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        if (!"PENDING".equals(order.getOrderStatus())) throw new BusinessException("当前订单状态不可支付");
        order.setOrderStatus("AUDITED");
        order.setPayStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(saved.getId(), "PAY", operator, "完成支付"));
        return saved;
    }

    @Transactional
    public void cancelByUser(UserAccount user, Long orderId) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!Objects.equals(order.getUser().getId(), user.getId())) throw new BusinessException("无权操作");
        if (!"PENDING".equals(order.getOrderStatus())) throw new BusinessException("已审核订单请走客服");
        order.setOrderStatus("CANCELLED");
        order.setCancelReason("用户主动取消");
        order.setCancelledAt(LocalDateTime.now());
        orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, "CANCEL", user.getUsername(), "用户取消"));
    }

    @Transactional
    public MarketOrder audit(Long orderId, boolean approve, String remark, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!"PENDING".equals(order.getOrderStatus())) throw new BusinessException("当前订单状态不可审核");
        if (approve) {
            for (MarketOrderItem oi : order.getItems()) {
                BigDecimal available = inventories.availableQty(oi.getItem().getId(), order.getWarehouse().getId());
                if (available.compareTo(oi.getQuantity()) < 0) {
                    throw new BusinessException("库存不足：" + oi.getItemName() + "（可用 " + available + "）");
                }
            }
            order.setOrderStatus("AUDITED");
            order.setReviewer(operator);
            order.setReviewRemark(remark);
        } else {
            order.setOrderStatus("REJECTED");
            order.setCancelReason(remark == null ? "审核拒绝" : remark);
            order.setCancelledAt(LocalDateTime.now());
        }
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, "AUDIT", operator,
                (approve ? "审核通过" : "审核拒绝") + (remark == null ? "" : "：" + remark)));
        return saved;
    }

    @Transactional
    public MarketOrder ship(Long orderId, String logisticsCompany, String logisticsNumber, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!"AUDITED".equals(order.getOrderStatus())) throw new BusinessException("订单未审核通过，不能发货");
        if (!"PAID".equals(order.getPayStatus())) throw new BusinessException("订单未支付，不能发货");
        Warehouse warehouse = order.getWarehouse();

        for (MarketOrderItem oi : order.getItems()) {
            BigDecimal remaining = oi.getQuantity();
            List<Inventory> lots = inventories.findFifoForOut(oi.getItem().getId(), warehouse.getId());
            if (lots.stream().map(Inventory::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(remaining) < 0) {
                throw new BusinessException("库存不足：" + oi.getItemName());
            }
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
                tx.setItem(oi.getItem()); tx.setWarehouse(warehouse); tx.setLocation(inv.getLocation());
                tx.setTransactionType(TransactionType.OUT); tx.setReferenceNo(order.getOrderNo());
                tx.setRemark("商城发货：" + (logisticsNumber == null ? "" : logisticsNumber));
                tx.setQuantity(take); tx.setUnitCost(inv.getAvgCost()); tx.setTotalCostAmount(cost);
                tx.setSalePrice(oi.getSalePrice()); tx.setSaleAmount(saleAmount); tx.setProfit(profit);
                tx.setBalanceQuantity(newQty); tx.setBalanceAmount(newAmount); tx.setAvgCostAfter(inv.getAvgCost());
                inventoryTransactions.save(tx);
                remaining = remaining.subtract(take);
            }
        }

        for (MarketOrderItem oi : order.getItems()) {
            products.findByItemId(oi.getItem().getId()).ifPresent(p -> {
                p.setSalesCount((p.getSalesCount() == null ? 0L : p.getSalesCount()) + oi.getQuantity().longValue());
                products.save(p);
            });
        }

        order.setOrderStatus("SHIPPED");
        order.setLogisticsCompany(logisticsCompany);
        order.setLogisticsNumber(logisticsNumber);
        order.setShippedAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, "SHIP", operator,
                "发货：" + (logisticsCompany == null ? "" : logisticsCompany) + " " + (logisticsNumber == null ? "" : logisticsNumber)));
        carts.deleteByUserId(order.getUser().getId());
        return saved;
    }

    @Transactional
    public MarketOrder complete(Long orderId, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!"SHIPPED".equals(order.getOrderStatus())) throw new BusinessException("订单未发货，不能完成");
        order.setOrderStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, "COMPLETE", operator, "确认收货"));
        return saved;
    }

    @Transactional
    public MarketOrder forceCancel(Long orderId, String reason, String operator) {
        MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(() -> new BusinessException("订单不存在"));
        if ("COMPLETED".equals(order.getOrderStatus()) || "CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("订单已完成或已取消");
        }
        order.setOrderStatus("CANCELLED");
        order.setCancelReason(reason == null ? "管理员取消" : reason);
        order.setCancelledAt(LocalDateTime.now());
        MarketOrder saved = orders.save(order);
        orderLogs.save(new MarketOrderLog(orderId, "CANCEL", operator, "管理员取消：" + reason));
        return saved;
    }
}
