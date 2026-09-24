package com.wms.controller.market;

import com.wms.common.ApiResponse;
import com.wms.common.BusinessException;
import com.wms.dto.market.MarketDtos.MarketProductRequest;
import com.wms.dto.market.MarketDtos.MarketShelfRequest;
import com.wms.dto.market.MarketDtos.MarketOrderAuditRequest;
import com.wms.dto.market.MarketDtos.MarketOrderShipRequest;
import com.wms.dto.market.MarketDtos.MarketCustomerRequest;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.market.*;
import com.wms.repository.market.*;
import com.wms.security.Permissions;
import com.wms.security.SecurityUtils;
import com.wms.service.market.MarketService;
import com.wms.service.WarehouseAccessService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商城管理接口（/admin/market/*），仅 ADMIN 可调用。
 */
@RestController
@RequestMapping("/admin/market")
public class MarketAdminController {
    private final MarketService service;
    private final MarketProductRepository products;
    private final MarketOrderRepository orders;
    private final MarketCustomerRepository customers;
    private final MarketOrderLogRepository orderLogs;
    private final com.wms.repository.UserAccountRepository users;
    private final WarehouseAccessService warehouseAccess;

    public MarketAdminController(MarketService service,
                                 MarketProductRepository products,
                                 MarketOrderRepository orders,
                                 MarketCustomerRepository customers,
                                 MarketOrderLogRepository orderLogs,
                                 com.wms.repository.UserAccountRepository users,
                                 WarehouseAccessService warehouseAccess) {
        this.service = service; this.products = products;
        this.orders = orders; this.customers = customers;
        this.orderLogs = orderLogs; this.users = users;
        this.warehouseAccess = warehouseAccess;
    }

    private UserAccount currentUser() {
        String username = SecurityUtils.username();
        if ("system".equals(username)) throw new BusinessException("未登录");
        return users.findByUsername(username).orElseThrow(() -> new BusinessException("用户不存在"));
    }

    // ==================== 商品管理 ====================
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        SecurityUtils.require(Permissions.PRODUCT_READ);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, pageSize)));
        // P2-7：status 条件下沉到 Repository 查询，避免内存过滤导致分页 total 不准
        Page<MarketProduct> p = products.searchAll(keyword, status, pageable);
        List<Map<String, Object>> rows = p.getContent().stream().map(MarketController::view).toList();
        return ApiResponse.ok(Map.of("records", rows, "total", p.getTotalElements(),
                "page", p.getNumber() + 1, "pageSize", p.getSize()));
    }

    @com.wms.security.Idempotent @PostMapping("/products")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody MarketProductRequest req) {
        SecurityUtils.require(Permissions.PRODUCT_WRITE);
        return ApiResponse.ok("创建成功", MarketController.view(service.saveProduct(req)));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                    @Valid @RequestBody MarketProductRequest req) {
        SecurityUtils.require(Permissions.PRODUCT_WRITE);
        return ApiResponse.ok("更新成功", MarketController.view(service.updateProduct(id, req)));
    }

    @com.wms.security.Idempotent @PostMapping("/products/{id}/shelf")
    public ApiResponse<Map<String, Object>> shelf(@PathVariable Long id,
                                                   @Valid @RequestBody MarketShelfRequest req) {
        SecurityUtils.require(Permissions.PRODUCT_WRITE);
        return ApiResponse.ok("状态已更新", MarketController.view(service.changeStatus(id, req.status())));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SecurityUtils.require(Permissions.PRODUCT_WRITE);
        service.deleteProduct(id);
        return ApiResponse.ok("已删除", null);
    }

    // ==================== 订单管理 ====================
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        SecurityUtils.require(Permissions.ORDER_READ);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, pageSize)));
        MarketOrderStatus orderStatus = (status == null || status.isBlank()) ? null : MarketOrderStatus.from(status);
        List<Long> warehouseIds = warehouseAccess.currentWarehouseIds();
        Page<MarketOrder> p = warehouseAccess.isWarehouseScoped()
                ? (warehouseIds.isEmpty() ? Page.empty(pageable) : orders.searchAdminInWarehouses(keyword, orderStatus, warehouseIds, pageable))
                : orders.searchAdmin(keyword, orderStatus, pageable);
        return ApiResponse.ok(pageOf(p));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        SecurityUtils.require(Permissions.ORDER_READ);
        MarketOrder o = orders.findDetailedById(id).orElseThrow(() -> new BusinessException("订单不存在"));
        warehouseAccess.require(o.getWarehouse().getId());
        Map<String, Object> view = MarketController.view(o);
        view.put("logs", orderLogs.findByOrderIdOrderByIdAsc(id).stream()
                .map(MarketController::view).toList());
        return ApiResponse.ok(view);
    }

    /** 审核：approve=true 通过，approve=false 拒绝并置为 CANCELLED/REJECTED。 */
    @com.wms.security.Idempotent @PostMapping("/orders/{id}/audit")
    public ApiResponse<Map<String, Object>> audit(@PathVariable Long id,
                                                   @Valid @RequestBody MarketOrderAuditRequest req) {
        SecurityUtils.require(Permissions.ORDER_REVIEW);
        MarketOrder o = service.audit(id, req.approve(), req.remark(), SecurityUtils.username());
        return ApiResponse.ok("审核完成", MarketController.view(o));
    }

    /** 发货：填写物流公司/单号后触发库存扣减。 */
    @com.wms.security.Idempotent @PostMapping("/orders/{id}/ship")
    public ApiResponse<Map<String, Object>> ship(@PathVariable Long id,
                                                  @Valid @RequestBody MarketOrderShipRequest req) {
        SecurityUtils.require(Permissions.ORDER_EXECUTE);
        MarketOrder o = service.ship(id, req.logisticsCompany(), req.logisticsNumber(), SecurityUtils.username());
        return ApiResponse.ok("已发货", MarketController.view(o));
    }

    /** 确认收货（与用户端 receive 一致，管理员也可代点）。 */
    @com.wms.security.Idempotent @PostMapping("/orders/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id) {
        SecurityUtils.require(Permissions.ORDER_EXECUTE);
        MarketOrder o = service.complete(id, SecurityUtils.username());
        return ApiResponse.ok("已完成", MarketController.view(o));
    }

    /** 强制取消（任意可取消状态）。已审核/已发货的订单已扣库存，取消时自动回滚（IN 流水）。 */
    @com.wms.security.Idempotent @PostMapping("/orders/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, String> body) {
        SecurityUtils.require(Permissions.ORDER_REVIEW);
        MarketOrder o = service.forceCancel(id,
                body == null ? null : body.getOrDefault("reason", "管理员取消"),
                SecurityUtils.username());
        return ApiResponse.ok("已取消", MarketController.view(o));
    }

    /** 发起退款：调微信退款接口，受理成功即回滚库存 + 置 REFUNDED。仅已支付订单可退款。 */
    @PostMapping("/orders/{id}/refund")
    public ApiResponse<Map<String, Object>> refund(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, String> body) {
        SecurityUtils.require(Permissions.ORDER_REVIEW);
        String reason = body == null ? null : body.get("reason");
        MarketOrder o = service.refund(id, reason, SecurityUtils.username());
        return ApiResponse.ok("退款已发起", MarketController.view(o));
    }

    // ==================== 客户/收货人 ====================
    @GetMapping("/customers")
    public ApiResponse<Map<String, Object>> customers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        SecurityUtils.require(Permissions.CUSTOMER_READ_ALL);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, pageSize)));
        Page<MarketCustomer> p = customers.findAll(pageable);
        return ApiResponse.ok(Map.of("records", p.getContent().stream().map(MarketController::view).toList(),
                "total", p.getTotalElements(), "page", pNum(pageable), "pageSize", pageable.getPageSize()));
    }

    // ==================== 客户管理 ====================
    @com.wms.security.Idempotent @PostMapping("/customers")
    public ApiResponse<Map<String, Object>> createCustomer(@Valid @RequestBody MarketCustomerRequest req) {
        SecurityUtils.require(Permissions.CUSTOMER_WRITE);
        return ApiResponse.ok("创建成功", MarketController.view(service.saveCustomer(null, req)));
    }

    @PutMapping("/customers/{id}")
    public ApiResponse<Map<String, Object>> updateCustomer(@PathVariable Long id,
                                                     @Valid @RequestBody MarketCustomerRequest req) {
        SecurityUtils.require(Permissions.CUSTOMER_WRITE);
        return ApiResponse.ok("更新成功", MarketController.view(service.updateCustomer(null, id, req)));
    }

    @DeleteMapping("/customers/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable Long id) {
        SecurityUtils.require(Permissions.CUSTOMER_WRITE);
        service.deleteCustomer(null, id);
        return ApiResponse.ok("已删除", null);
    }

    // ==================== 销售汇总（dashboard） ====================
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        SecurityUtils.require(Permissions.REPORT_VIEW);
        boolean scoped = warehouseAccess.isWarehouseScoped();
        List<Long> warehouseIds = warehouseAccess.currentWarehouseIds();
        Map<String, Object> m = new LinkedHashMap<>();
        // 核心指标
        m.put("totalSales", scoped ? (warehouseIds.isEmpty() ? java.math.BigDecimal.ZERO : orders.sumCompletedAmountInWarehouses(warehouseIds)) : orders.sumCompletedAmount());
        m.put("todayOrders", scoped ? (warehouseIds.isEmpty() ? 0L : orders.countTodayOrdersInWarehouses(warehouseIds)) : orders.countTodayOrders());
        m.put("todaySales", scoped ? (warehouseIds.isEmpty() ? java.math.BigDecimal.ZERO : orders.sumTodayAmountInWarehouses(warehouseIds)) : orders.sumTodayAmount());
        m.put("totalProducts", products.count());
        m.put("totalCustomers", customers.count());
        // 各状态订单数
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (MarketOrderStatus s : MarketOrderStatus.values()) {
            statusCounts.put(s.name(), scoped ? (warehouseIds.isEmpty() ? 0L : orders.countByStatusInWarehouses(s, warehouseIds)) : orders.countByStatus(s));
        }
        m.put("statusCounts", statusCounts);
        // 商品销量 Top 10
        List<Object[]> rows = scoped
                ? (warehouseIds.isEmpty() ? List.of() : orders.topProductsInWarehouses(warehouseIds, PageRequest.of(0, 10)))
                : orders.topProducts(PageRequest.of(0, 10));
        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", row[0]); t.put("code", row[1]);
            t.put("quantity", row[2]); t.put("amount", row[3]);
            top.add(t);
        }
        m.put("topProducts", top);
        return ApiResponse.ok(m);
    }

    // ==================== View helpers ====================
    private static int pNum(Pageable p) { return p.getPageNumber() + 1; }

    private static Map<String, Object> pageOf(Page<MarketOrder> p) {
        return MarketController.orderPageOf(p);
    }
}
