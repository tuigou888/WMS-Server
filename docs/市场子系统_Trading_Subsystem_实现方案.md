# 商城子系统（Trading Subsystem）实现方案

## 1. 目标与边界

- **目标**：在现有 `WMS-SERVER` 之上扩展一个**对外零售/批发商城**子系统：终端客户（B2C/C 小型批发）通过**独立小程序**选购、结算、查订单；管理员在 **wms-web 后台**维护商品上架、定价、订单履约与对账。
- **范围**：
  - 后端：`wms-server` 新增 `market` 模块（商品列表/上下架、购物车、订单、收货人、退款/取消、订单履约回调 WMS 出库）。
  - 小程序端：**新建子工程 `wms-shopping-miniapp`**（与 `wms-miniapp` 平级、复用同一后端，复用 WMS 库存与物品主数据）。
  - Web 端：`wms-web` 新增商城运营模块（商品管理、订单管理、订单审核/发货、退款审核、报表）。
- **非目标**：不实现支付网关集成（先做"现金/到付/挂账"占位枚举与对账字段，对接支付网关留到下一阶段）；不做物流轨迹接入（预留运单号字段即可）。

## 2. 总体架构

```
┌────────────────────────────┐    ┌──────────────────────────────────┐
│  wms-shopping-miniapp       │    │  wms-web (Vue 3 后台)            │
│  uni-app + Vue 3 + Pinia    │    │  - 商品上架/定价                 │
│  - 商品/分类/购物车/结算    │    │  - 商城订单/审核/发货/退款       │
│  - 订单/收货人/我的         │    │  - 商城报表                      │
└─────────────┬──────────────┘    └────────────┬─────────────────────┘
              │ Bearer Token (JWT)             │ Bearer Token (JWT)
              │ /api/v1/market/*               │ /api/v1/market/*
              ▼                                ▼
       ┌──────────────────────────────────────────────────────────┐
       │          wms-server  (Spring Boot)                       │
       │   market/ 商品  market/ 订单  market/ 购物车  market/客户 │
       │   ↘ 履约回调 InventoryService.stockOut（走库存出库）    │
       └──────────────────────────────────────────────────────────┘
```

- 复用既有 JWT 与 RBAC（`Permissions`/`RolePermissions`/`TokenService`）。
- 复用 `Item` / `Warehouse` / `Inventory` / `InventoryTransaction`，**不破坏**现有 WMS 库存语义。
- 商城商品是 `Item` 的"上架视图"（一个 `Item` 可上架为多个 SKU 视图，当前实现 = 1 个物品 1 个商品）。

## 3. 数据模型（新增表）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `market_product` | id, item_id (FK→items), title, sub_title, main_image, gallery(json), status(SHELF_ON/SHELF_OFF/SOLD_OUT), sales_count, view_count, sort_no | 商品上架主表，1:1 关联 `items` |
| `market_product_sku` | id, product_id, unit, sale_price(decimal 18,4), market_price, stock_buffer(decimal 18,4 默认 0=不限), min_purchase, max_purchase, status | 销售单元（先实现 1 SKU = 1 商品，预留多规格字段） |
| `market_category` | id, name, icon, parent_id, sort_no, status | 商城独立类目（与 WMS 类目解耦） |
| `market_product_category` | product_id, category_id | 多对多关联 |
| `market_cart` | id, customer_id, sku_id, quantity, created_at, updated_at | 购物车（按登录用户聚合） |
| `market_customer` | id, user_account_id(可空=未登录下过单), name, phone, address, default_flag, remark | 收货人/客户档案 |
| `market_order` | id, order_no(唯一), customer_id, total_amount, pay_type(PAY_ONLINE/CASH_ON_DELIVERY/CREDIT), pay_status(UNPAID/PAID/REFUNDED), order_status(PENDING/AUDITED/SHIPPING/COMPLETED/CANCELLED), warehouse_id(FK), remark, paid_at, shipped_at, completed_at, created_at, updated_at | 商城订单 |
| `market_order_item` | id, order_id, sku_id, item_id, item_code, item_name, unit, sale_price, quantity, subtotal | 订单行 |
| `market_order_log` | id, order_id, action, operator, remark, created_at | 订单流转日志 |

> 不引入新的库存表——所有扣减都走 `InventoryService.stockOut`，保证库存与 WMS 实时一致。

## 4. 后端 API 设计（`/api/v1/market/*`）

### 公共（小程序）
| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/market/categories` | 类目树（启用） |
| GET | `/market/products` | 商品列表（分页/类目/关键字） |
| GET | `/market/products/{id}` | 商品详情（含 SKU、当前可售库存） |
| POST | `/market/cart/add` | 加入购物车（需 `market:buy` 权限） |
| GET | `/market/cart` | 当前用户购物车 |
| DELETE | `/market/cart/{id}` | 移除购物车行 |
| PUT | `/market/cart/{id}` | 修改数量 |
| POST | `/market/orders` | 提交订单（按购物车快照扣减库存，事务化） |
| GET | `/market/orders` | 当前用户订单列表 |
| GET | `/market/orders/{id}` | 订单详情 |
| POST | `/market/orders/{id}/cancel` | 用户主动取消（仅 PENDING/UNPAID 状态） |
| POST | `/market/orders/{id}/pay` | 模拟支付（仅 PAY_ONLINE） |
| GET/POST/PUT/DELETE | `/market/customers` | 收货人地址簿 |

### 管理（wms-web）
| Method | Path | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/market/admin/products` | 商品管理列表（包含 SHELF_OFF） | `product:read` |
| POST | `/market/admin/products` | 创建商品（含 SKU） | `product:write` |
| PUT | `/market/admin/products/{id}` | 更新商品 | `product:write` |
| POST | `/market/admin/products/{id}/shelf` | 上/下架 | `product:write` |
| GET | `/market/admin/orders` | 订单管理 | `order:read` |
| POST | `/market/admin/orders/{id}/audit` | 审核通过 | `order:review` |
| POST | `/market/admin/orders/{id}/ship` | 发货（事务化调用 `InventoryService.stockOut`） | `order:execute` |
| POST | `/market/admin/orders/{id}/complete` | 确认完成 | `order:execute` |
| POST | `/market/admin/orders/{id}/cancel` | 强制取消（已发货会回滚库存） | `order:review` |
| GET | `/market/admin/stats` | 销售汇总（按日/类目） | `report:view` |

## 5. 权限（扩展 `Permissions` 与 `RolePermissions`）

新增权限字符串（`Permissions.java`）：
- `MARKET_BUY = "market:buy"` — 小程序下单
- `MARKET_READ = "market:read"` — 小程序读
- `PRODUCT_READ = "product:read"`、`PRODUCT_WRITE = "product:write"` — 后台商品管理
- `ORDER_READ = "order:read"`、`ORDER_REVIEW = "order:review"`、`ORDER_EXECUTE = "order:execute"`

`RolePermissions`：
- `ADMIN` → ALL ∪ {新增全部}
- `WAREHOUSE` → 不包含 `MARKET_BUY`/`MARKET_READ`（运营用户不看零售）但保留 `PRODUCT_READ` 与 `ORDER_READ/EXECUTE`（履约需要出库权限）
- 新增角色 `CUSTOMER` → `{MARKET_BUY, MARKET_READ}`（小程序登录时按 `user_accounts.role='CUSTOMER'` 或 `openid` 自动授予）

## 6. 小程序（`wms-shopping-miniapp`）

目录结构：
```
wms-shopping-miniapp/
├── package.json / vite.config.js / index.html
├── src/
│   ├── App.vue / main.js
│   ├── manifest.json / pages.json
│   ├── api/request.js      # 复用 request 模式，新增 market.* 方法
│   ├── store/{user,cart,product}.js
│   ├── pages/
│   │   ├── login/          # 微信一键登录 + 手机号绑定
│   │   ├── index/          # 首页（轮播+类目入口+推荐）
│   │   ├── category/       # 类目浏览
│   │   ├── product/        # 商品详情 + 加入购物车
│   │   ├── cart/           # 购物车
│   │   ├── checkout/       # 结算（选地址/支付方式）
│   │   ├── orders/         # 订单列表
│   │   ├── order-detail/   # 订单详情
│   │   └── mine/           # 我的（地址簿、退出登录）
│   ├── components/{product-card,empty,loading}.vue
│   └── utils/{format,validator}.js
└── static/...
```

`pages.json` tabBar：首页 / 类目 / 购物车 / 我的。

> 注：复用 `wms-miniapp` 的 `request.js` 拦截器模式（同 `uni.getStorageSync` + Bearer Token + 401 跳登录），但**独立工程**避免仓库与 WMS 内部员工小程序相互干扰。

## 7. Web 后台（`wms-web`）扩展

- `src/api/market.js`：包装 `market/admin/*` 接口
- `src/views/market/product-list.vue`、`product-form.vue` — 商品 + SKU 维护
- `src/views/market/order-list.vue`、`order-detail.vue` — 订单审核/发货/退款
- `src/views/market/customer-list.vue` — 收货人档案
- `src/views/market/dashboard.vue` — 销售汇总卡片
- 路由：`/market/products`、`/market/orders`、`/market/customers`、`/market/dashboard`
- 侧边栏 `Layout.vue` 增加"商城运营"分组（条件渲染：hasPerm 命中新增权限）

## 8. 关键业务规则

1. **下单扣减库存**：在 `MarketOrderService.create` 中，按订单行逐行调用 `InventoryService.stockOut`（带 `TransactionType.OUT` + `referenceNo = orderNo`）。任意一行失败 → 整笔回滚（事务保证）。
2. **取消回滚**：用户在 PENDING 时取消直接改状态；AUDITED 之前都可以无副作用取消；AUDITED 之后取消若已发货需走"强制取消"接口，回滚库存（`stockIn` 反向流水，参考 `DocumentService.uncompleteDocument`）。
3. **超卖防护**：`stockOut` 已是悲观锁（`InventoryRepository.findForUpdate`），无需额外加锁。
4. **价格快照**：订单行保存 `salePrice` 与 `subtotal`，下单后商品改价不影响历史订单。
5. **客户档案**：`market_customer` 与 `user_accounts` 1:N（一小程序用户多个收货人），下单时必填 `customer_id`。

## 9. 实施步骤（分阶段）

**Phase 1：后端实体与 API**
1. 新增 `MarketProduct / Sku / Category / Cart / Customer / Order / OrderItem / OrderLog` 实体（含 `AuditableEntity`）。
2. 新增对应 `Repository`（`MarketProductRepository` 等）。
3. 扩展 `Permissions` + `RolePermissions`（新增 `MARKET_BUY / MARKET_READ / PRODUCT_* / ORDER_*`）。
4. 新增 `dto/MarketDtos.java`（请求/响应 record）。
5. 新增 `MarketProductService / MarketOrderService / MarketCustomerService / MarketCartService`。
6. 新增 `MarketController / MarketAdminController`，`SecurityConfig` 放行 `/market/**` 需 token（不复用 `permitAll`）。

**Phase 2：联调后端**
- `mvn test` 跑现有回归脚本确保不破坏 WMS 行为。
- 使用 `curl`/Postman 跑新增接口 smoke test（下单→审核→发货→库存减少）。

**Phase 3：商城小程序**
- 脚手架 → API → store → 页面 → tabBar → 联调。

**Phase 4：管理后台扩展**
- `wms-web` 新增商品/订单/客户/统计四个视图，挂路由 + 侧边栏。

**Phase 5：构建与文档**
- `npm run build`（两端）通过；README/AGENTS.md 追加商城章节。

## 10. 风险与回滚

- **库存一致性**：所有扣减/回滚都走现有 `InventoryService`，不绕过事务边界，**强一致性**。
- **角色污染**：CUSTOMER 角色与现有 ADMIN/WAREHOUSE 并列，仅用于小程序登录身份；`SecurityConfig` 不需要改路由放行规则。
- **可回滚**：本次新增模块都集中在 `com.wms.market.*` 包、新增表 `market_*`、新增权限字符串；删除包 + DROP 表 + 还原 `Permissions.java` 即可完全回滚。

---

## 11. 实施状态（2026-08-13）

**Phase 4 已完成**：`wms-web` 管理后台扩展。

新增内容：
- `wms-web/src/api/wms.js` 追加 `marketProducts / marketOrders / marketCustomers` CRUD 接口
- `wms-web/src/pages/MallProductsPage.vue`：商品列表、搜索、上架/下架、新建/编辑、删除
- `wms-web/src/pages/MallOrdersPage.vue`：订单列表、审核/拒绝、发货（物流+单号）、确认完成、强制取消、订单详情（含商品明细与操作日志时间线）
- `wms-web/src/pages/MallCustomersPage.vue`：客户列表、新增/编辑/删除
- `wms-web/src/router/index.js` 增加 `/market/products`、`/market/orders`、`/market/customers` 路由
- `wms-web/src/utils/menu.js` 增加"商城管理"分组，按 `product:read / order:read / customer:read` 权限动态显示
- 后端 `MarketAdminController` 补全客户增删改；`MarketService` 兼容 `user=null` 的后台调用；`Permissions`/`RolePermissions` 新增 `customer:write` 并注入 ALL 权限集

验证结果：
- `mvn test`：10 tests pass，BUILD SUCCESS
- `wms-web npm run build`：三页面全部打包成功
- `wms-shopping-miniapp npm run build` / `npx uni build -p mp-weixin`：H5 与微信小程序端均构建完成

注：后端仓库数据在 dev/test profile 下重启即丢（H2 内存），线上请使用 MySQL profile。
