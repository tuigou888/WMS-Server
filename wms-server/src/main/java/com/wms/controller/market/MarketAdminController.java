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

    public MarketAdminController(MarketService service,
                                 MarketProductRepository products,
                                 MarketOrderRepository orders,
                                 MarketCustomerRepository customers,
                                 MarketOrderLogRepository orderLogs,
                                 com.wms.repository.UserAccountRepository users) {
        this.service = service; this.products = products;
        this.orders = orders; this.customers = customers;
        this.orderLogs = orderLogs; this.users = users;
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
        Page<MarketProduct> p = products.searchAll(keyword, pageable);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarketProduct item : p.getContent()) {
            Map<String, Object> v = MarketController.view(item);
            if (status != null && !status.isBlank()
                    && !status.equals(item.getStatus())) continue;
            rows.add(v);
        }
        return ApiResponse.ok(Map.of("records", rows, "total", p.getTotalElements(),
                "page", p.getNumber() + 1, "pageSize", p.getSize()));
    }

    @PostMapping("/products")
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

    @PostMapping("/products/{id}/shelf")
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
        Page<MarketOrder> p = orders.searchAdmin(keyword, status, pageable);
        return ApiResponse.ok(pageOf(p));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        SecurityUtils.require(Permissions.ORDER_READ);
        MarketOrder o = orders.findDetailedById(id).orElseThrow(() -> new BusinessException("订单不存在"));
        Map<String, Object> view = MarketController.view(o);
        view.put("logs", orderLogs.findByOrderIdOrderByIdAsc(id).stream()
                .map(MarketController::view).toList());
        return ApiResponse.ok(view);
    }

    /** 审核：approve=true 通过，approve=false 拒绝并置为 CANCELLED/REJECTED。 */
    @PostMapping("/orders/{id}/audit")
    public ApiResponse<Map<String, Object>> audit(@PathVariable Long id,
                                                   @Valid @RequestBody MarketOrderAuditRequest req) {
        SecurityUtils.require(Permissions.ORDER_REVIEW);
        MarketOrder o = service.audit(id, req.approve(), req.remark(), SecurityUtils.username());
        return ApiResponse.ok("审核完成", MarketController.view(o));
    }

    /** 发货：填写物流公司/单号后触发库存扣减。 */
    @PostMapping("/orders/{id}/ship")
    public ApiResponse<Map<String, Object>> ship(@PathVariable Long id,
                                                  @Valid @RequestBody MarketOrderShipRequest req) {
        SecurityUtils.require(Permissions.ORDER_EXECUTE);
        MarketOrder o = service.ship(id, req.logisticsCompany(), req.logisticsNumber(), SecurityUtils.username());
        return ApiResponse.ok("已发货", MarketController.view(o));
    }

    /** 确认收货（与用户端 receive 一致，管理员也可代点）。 */
    @PostMapping("/orders/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id) {
        SecurityUtils.require(Permissions.ORDER_EXECUTE);
        MarketOrder o = service.complete(id, SecurityUtils.username());
        return ApiResponse.ok("已完成", MarketController.view(o));
    }

    /** 强制取消（任意可取消状态），已发货时仅记录说明不反扣库存。 */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, String> body) {
        SecurityUtils.require(Permissions.ORDER_REVIEW);
        MarketOrder o = service.forceCancel(id,
                body == null ? null : body.getOrDefault("reason", "管理员取消"),
                SecurityUtils.username());
        return ApiResponse.ok("已取消", MarketController.view(o));
    }

    // ==================== 客户/收货人 ====================
    @GetMapping("/customers")
    public ApiResponse<Map<String, Object>> customers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        SecurityUtils.require(Permissions.CUSTOMER_READ);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, pageSize)));
        Page<MarketCustomer> p = customers.findAll(pageable);
        return ApiResponse.ok(Map.of("records", p.getContent().stream().map(MarketController::view).toList(),
                "total", p.getTotalElements(), "page", pNum(pageable), "pageSize", pageable.getPageSize()));
    }

    // ==================== 客户管理 ====================
    @PostMapping("/customers")
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

    // ==================== View helpers ====================
    private static int pNum(Pageable p) { return p.getPageNumber() + 1; }

    private static Map<String, Object> pageOf(Page<MarketOrder> p) {
        return MarketController.orderPageOf(p);
    }
}
