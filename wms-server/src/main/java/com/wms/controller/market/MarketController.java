package com.wms.controller.market;

import com.wms.common.ApiResponse;
import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.MarketCartAddRequest;
import com.wms.dto.market.MarketDtos.MarketCartUpdateRequest;
import com.wms.dto.market.MarketDtos.MarketCustomerRequest;
import com.wms.dto.market.MarketDtos.MarketOrderCreateRequest;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.*;
import com.wms.repository.CategoryRepository;
import com.wms.repository.UserAccountRepository;
import com.wms.repository.WarehouseRepository;
import com.wms.repository.market.*;
import com.wms.security.Permissions;
import com.wms.security.SecurityUtils;
import com.wms.service.market.MarketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 商城面向小程序用户的接口（/market/*）。
 * 浏览类（category/product 详情）允许 CUSTOMER/任何登录用户访问；
 * 购物车/下单类强制校验 market:buy 权限。
 */
@RestController
@RequestMapping("/market")
public class MarketController {
    private final MarketService service;
    private final MarketProductRepository products;
    private final MarketCartRepository carts;
    private final MarketCustomerRepository customers;
    private final MarketOrderRepository orders;
    private final MarketOrderLogRepository orderLogs;
    private final WarehouseRepository warehouses;
    private final UserAccountRepository users;
    private final CategoryRepository categories;

    public MarketController(MarketService service, MarketProductRepository products,
                            MarketCartRepository carts, MarketCustomerRepository customers,
                            MarketOrderRepository orders, MarketOrderLogRepository orderLogs,
                            WarehouseRepository warehouses, UserAccountRepository users,
                            CategoryRepository categories) {
        this.service = service; this.products = products;
        this.carts = carts; this.customers = customers;
        this.orders = orders; this.orderLogs = orderLogs; this.warehouses = warehouses;
        this.users = users; this.categories = categories;
    }

    /** 取当前登录的 UserAccount；未登录或会话失效抛业务异常。 */
    private UserAccount user() {
        String username = SecurityUtils.username();
        if (username == null || "system".equals(username)) throw new BusinessException("未登录");
        return users.findByUsername(username).orElseThrow(() -> new BusinessException("用户不存在"));
    }

    // ==================== 商城分类（直接复用 WMS categories） ====================
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(categories.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getStatus()))
                .sorted(Comparator.comparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId()); m.put("name", c.getName());
                    m.put("sortOrder", c.getSortOrder());
                    return m;
                }).toList());
    }

    // ==================== 商品浏览 ====================
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(50, Math.max(1, pageSize)));
        Page<MarketProduct> p = products.searchShelfOn(categoryId, keyword, pageable);
        return ApiResponse.ok(pageOf(p));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Map<String, Object>> product(@PathVariable Long id) {
        MarketProduct product = products.findDetailedById(id)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        service.incrementView(id);
        Map<String, Object> view = view(product);
        view.put("availableStock", service.available(product.getItem().getId(),
                product.getItem().getDefaultWarehouse() == null ? null
                        : product.getItem().getDefaultWarehouse().getId()));
        return ApiResponse.ok(view);
    }

    // ==================== 购物车 ====================
    @PostMapping("/cart")
    public ApiResponse<Map<String, Object>> addCart(@Valid @RequestBody MarketCartAddRequest req) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok(view(service.addCart(user, req.productId(), req.quantity())));
    }

    @GetMapping("/cart")
    public ApiResponse<Map<String, Object>> cart() {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        List<MarketCart> list = carts.findByUserIdOrderByIdDesc(user.getId());
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarketCart c : list) {
            BigDecimal sub = c.getSnapshotPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
            total = total.add(sub);
            rows.add(view(c));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", total);
        result.put("count", list.size());
        return ApiResponse.ok(result);
    }

    @PutMapping("/cart/{id}")
    public ApiResponse<Map<String, Object>> updateCart(@PathVariable Long id,
                                                        @Valid @RequestBody MarketCartUpdateRequest req) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok(view(service.updateCartQty(user, id, req.quantity())));
    }

    @DeleteMapping("/cart")
    public ApiResponse<Void> clearCart(@RequestBody(required = false) Map<String, List<Long>> body) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        if (body != null && body.get("ids") != null && !body.get("ids").isEmpty()) {
            service.removeCart(user, body.get("ids"));
        } else {
            service.clearCart(user);
        }
        return ApiResponse.ok("已清空", null);
    }

    // ==================== 收货人 ====================
    @GetMapping("/customers")
    public ApiResponse<List<Map<String, Object>>> customers() {
        SecurityUtils.require(Permissions.MARKET_READ);
        UserAccount user = user();
        return ApiResponse.ok(customers.findByUserIdOrderByIdDesc(user.getId()).stream()
                .map(MarketController::view).toList());
    }

    @PostMapping("/customers")
    public ApiResponse<Map<String, Object>> saveCustomer(@Valid @RequestBody MarketCustomerRequest req) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok(view(service.saveCustomer(user, req)));
    }

    @PutMapping("/customers/{id}")
    public ApiResponse<Map<String, Object>> updateCustomer(@PathVariable Long id,
                                                            @Valid @RequestBody MarketCustomerRequest req) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok(view(service.updateCustomer(user, id, req)));
    }

    @DeleteMapping("/customers/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable Long id) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        service.deleteCustomer(user, id);
        return ApiResponse.ok("已删除", null);
    }

    // ==================== 订单 ====================
    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(@Valid @RequestBody MarketOrderCreateRequest req) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        MarketOrder o = service.createOrder(user, req);
        return ApiResponse.ok("下单成功", view(o));
    }

    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        SecurityUtils.require(Permissions.MARKET_READ);
        UserAccount user = user();
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(50, Math.max(1, pageSize)));
        Page<MarketOrder> p = orders.search(user.getId(), status, pageable);
        return ApiResponse.ok(orderPageOf(p));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> order(@PathVariable Long id) {
        SecurityUtils.require(Permissions.MARKET_READ);
        UserAccount user = user();
        MarketOrder o = orders.findDetailedById(id).orElseThrow(() -> new BusinessException("订单不存在"));
        if (!Objects.equals(o.getUser().getId(), user.getId())) throw new BusinessException("无权查看");
        Map<String, Object> view = view(o);
        view.put("logs", orderLogs.findByOrderIdOrderByIdAsc(id).stream()
                .map(MarketController::view).toList());
        return ApiResponse.ok(view);
    }

    @PostMapping("/orders/{id}/pay")
    public ApiResponse<Map<String, Object>> pay(@PathVariable Long id) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok("支付成功", view(service.pay(user, id, user.getUsername())));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        service.cancelByUser(user, id);
        return ApiResponse.ok("已取消", null);
    }

    @PostMapping("/orders/{id}/receive")
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id) {
        SecurityUtils.require(Permissions.MARKET_BUY);
        UserAccount user = user();
        return ApiResponse.ok("已确认收货", view(service.complete(id, user.getUsername())));
    }

    @GetMapping("/warehouses")
    public ApiResponse<List<Map<String, Object>>> warehouses() {
        SecurityUtils.require(Permissions.MARKET_READ);
        return ApiResponse.ok(warehouses.findByStatusTrueOrderByNameAsc().stream()
                .map(w -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", w.getId()); m.put("code", w.getCode()); m.put("name", w.getName());
                    return m;
                }).toList());
    }

    // ==================== View helpers ====================
    private Map<String, Object> pageOf(Page<MarketProduct> p) {
        return Map.of("records", p.getContent().stream().map(MarketController::view).toList(),
                "total", p.getTotalElements(), "page", p.getNumber() + 1, "pageSize", p.getSize());
    }

    static Map<String, Object> orderPageOf(Page<MarketOrder> p) {
        return Map.of("records", p.getContent().stream().map(MarketController::view).toList(),
                "total", p.getTotalElements(), "page", p.getNumber() + 1, "pageSize", p.getSize());
    }

    public static Map<String, Object> view(MarketProduct p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("subTitle", p.getSubTitle());
        m.put("mainImage", p.getMainImage());
        m.put("salePrice", p.getSalePrice());
        m.put("marketPrice", p.getMarketPrice());
        m.put("status", p.getStatus());
        m.put("salesCount", p.getSalesCount());
        m.put("viewCount", p.getViewCount());
        m.put("categoryId", p.getCategory() == null ? null : p.getCategory().getId());
        m.put("categoryName", p.getCategory() == null ? null : p.getCategory().getName());
        m.put("itemId", p.getItem().getId());
        m.put("itemCode", p.getItem().getCode());
        m.put("itemName", p.getItem().getName());
        m.put("unit", p.getItem().getUnit());
        m.put("specs", p.getItem().getSpecs());
        m.put("brand", p.getItem().getBrand());
        m.put("model", p.getItem().getModel());
        m.put("gallery", p.getGallery() == null || p.getGallery().isBlank()
                ? List.of() : Arrays.stream(p.getGallery().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    public static Map<String, Object> view(MarketCart c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("product", view(c.getProduct()));
        m.put("quantity", c.getQuantity());
        m.put("snapshotPrice", c.getSnapshotPrice());
        m.put("subtotal", c.getSnapshotPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    public static Map<String, Object> view(MarketCustomer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("address", c.getAddress());
        m.put("defaultFlag", c.getDefaultFlag());
        m.put("remark", c.getRemark());
        return m;
    }

    public static Map<String, Object> view(MarketOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("orderStatus", o.getOrderStatus());
        m.put("payStatus", o.getPayStatus());
        m.put("payType", o.getPayType());
        m.put("totalAmount", o.getTotalAmount());
        m.put("receiverName", o.getReceiverName());
        m.put("receiverPhone", o.getReceiverPhone());
        m.put("receiverAddress", o.getReceiverAddress());
        m.put("warehouseId", o.getWarehouse().getId());
        m.put("warehouseName", o.getWarehouse().getName());
        m.put("logisticsCompany", o.getLogisticsCompany());
        m.put("logisticsNumber", o.getLogisticsNumber());
        m.put("remark", o.getRemark());
        m.put("adminRemark", o.getAdminRemark());
        m.put("paidAt", o.getPaidAt());
        m.put("auditedAt", o.getAuditedAt());
        m.put("shippedAt", o.getShippedAt());
        m.put("completedAt", o.getCompletedAt());
        m.put("cancelledAt", o.getCancelledAt());
        m.put("cancelReason", o.getCancelReason());
        m.put("reviewer", o.getReviewer());
        m.put("reviewRemark", o.getReviewRemark());
        m.put("createdAt", o.getCreatedAt());
        m.put("items", o.getItems().stream().map(MarketController::view).toList());
        return m;
    }

    public static Map<String, Object> view(MarketOrderItem oi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", oi.getId());
        m.put("productId", oi.getProductId());
        m.put("itemId", oi.getItem().getId());
        m.put("itemCode", oi.getItemCode());
        m.put("itemName", oi.getItemName());
        m.put("unit", oi.getUnit());
        m.put("salePrice", oi.getSalePrice());
        m.put("quantity", oi.getQuantity());
        m.put("subtotal", oi.getSubtotal());
        return m;
    }

    public static Map<String, Object> view(MarketOrderLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("action", l.getAction());
        m.put("operator", l.getOperator());
        m.put("remark", l.getRemark());
        m.put("createdAt", l.getCreatedAt());
        return m;
    }
}
