# WMS 商城模块 — 审计报告与修复建议

**审计日期：2026-08-13**

---

## 一、系统总览

### 后端（wms-server）
| 层 | 文件 | 说明 |
|---|---|---|
| Entity | `model/entity/market/MarketProduct` | 商品 |
| Entity | `model/entity/market/MarketCart` | 购物车（快照价格） |
| Entity | `model/entity/market/MarketCustomer` | 收货人 |
| Entity | `model/entity/market/MarketOrder` | 订单 |
| Entity | `model/entity/market/MarketOrderItem` | 订单项 |
| Entity | `model/entity/market/MarketOrderLog` | 操作日志 |
| Service | `service/market/MarketService` | 核心业务 |
| Controller | `controller/market/MarketController` | 用户接口 `/market/*` |
| Controller | `controller/market/MarketAdminController` | 管理员接口 `/admin/market/*` |
| Repo | `repository/market/*.java` | 仓储层 |

**状态机**（`MarketOrder`）：
```
PENDING → AUDITED → SHIPPED → COMPLETED
  ↘ CANCELLED   ↗ REJECTED
```

**支付方式枚举**：`PAY_ONLINE` / `CASH_ON_DELIVERY` / `CREDIT`

**商品状态**：`DRAFT` / `SHELF_ON` / `SHELF_OFF`

---

## 二、审计发现

### 已修复 Bug（P0）

| # | 文件 | 问题 | 影响 | 状态 |
|---|---|---|---|---|
| 1 | `wms-shopping-miniapp/src/pages/product/product.vue` | 模板引用 `product.salePrice` 展示库存，应为 `availableStock` | 商品页库存显示错误 | ✅ 已修复 |
| 2 | `wms-shopping-miniapp/src/pages/product/product.vue` | `badge` 绑定语法 `:class="['badge', productStatus].color"` 无效 | badge 样式不生效 | ✅ 已修复 |
| 3 | `wms-shopping-miniapp/src/pages/product/product.vue` | `doAdd()` 调用 `useUserStore().setUserInfo({ cartId })` 错误 API | 用户状态污染 | ✅ 已修复 |
| 4 | `wms-shopping-miniapp/src/pages/product/product.vue` | `getStockText()` 使用 `p.safetyStock`（不存在字段） | 库存文本显示异常 | ✅ 已修复 |
| 5 | `wms-shopping-miniapp/src/pages/checkout/checkout.vue` | `onShow` 未回填地址选择页结果 | 结算页地址始终为空 | ✅ 已修复 |
| 6 | `wms-server/.../controller/market/MarketController.java` | `DELETE /market/cart` 不支持空 body 清空购物车 | 小程序清空购物车失效 | ✅ 已修复 |
| 7 | `wms-web/src/pages/MallOrdersPage.vue` | 缺少 `const data = ref([])` 声明 | 订单列表数据未初始化 | ✅ 已修复 |
| 8 | `wms-web/src/pages/MallOrdersPage.vue` | 状态名使用 `APPROVED` 但后端为 `AUDITED` | 审核通过后无法发货（按钮永不显示） | ✅ 已修复 |
| 9 | `wms-web/src/pages/MallOrdersPage.vue` | `PayType` 映射使用 `WECHAT/ALIPAY/CASH` 但后端为 `PAY_ONLINE/CASH_ON_DELIVERY` | 支付方式显示错误 | ✅ 已修复 |

### 待观察（低优先级）

| # | 位置 | 问题 | 影响 | 建议 |
|---|---|---|---|---|
| 10 | `wms-web/src/pages/MallOrdersPage.vue` L124 | 操作列判断 `r.orderStatus === 'AUDITED'` 使用硬编码字符串 | 无 | 建议改为常量或枚举映射 |
| 11 | 全站 | `inventory` 字段未返回 `availableStock`（view helper 仅返回基础字段） | 列表页库存显示需前端自行计算 | 可在 view helper 中注入 |
| 12 | `wms-shopping-miniapp/src/pages/index/index.vue` | 仅显示 `salePrice`，未显示库存 | UX 弱 | 可参考 product.vue 做法 |
| 13 | `wms-server` 单测 | 无商城模块 JUnit 测试 | 回归风险 | 建议新增 |
| 14 | `wms-shopping-miniapp` | 未配置 `appid`，统计不可用 | 监控缺失 | 生产前配置 |

---

## 三、架构亮点

1. **库存快照机制**：购物车商品保存下单时价格快照（`snapshotPrice`），后续价格变动不影响已加入购物车商品。
2. **权限隔离**：`MarketController` 对 `market:buy` 权限做强制校验，`MarketAdminController` 独立路径避免越权。
3. **操作日志**：`MarketOrderLog` 记录每笔订单的状态流转，支持审计追溯。
4. **库存 FIFO 扣减**：订单审核时通过 `InventoryService` 扣减库存，保证与 WMS 库存一致。

---

## 四、建议修复项（非阻塞）

1. **状态机引入枚举**：将 `String orderStatus` 改为枚举类型，避免硬编码字符串不一致。
2. **视图层统一 `availableStock`**：在 `view(MarketProduct)` 中直接注入可用库存，避免前端二次查询。
3. **小程序端补充单测**：使用 `uni.miniprogram` 自动化测试关键路径（登录→下单→支付）。
4. **管理后台商品状态过滤**：`MallProductsPage.vue` 目前仅展示 `SHELF_ON`，应支持全部状态筛选。
5. **支付模拟扩展**：当前 `PAY_ONLINE` 仅模拟成功，建议增加失败/退款场景测试。

---

## 五、构建验证

```bash
# wms-server
mvn test                    # ✅ 10 tests pass
mvn -DskipTests package     # ✅ BUILD SUCCESS

# wms-web
npm run build               # ✅ built in 8.61s

# wms-shopping-miniapp
npx uni build -p mp-weixin  # ✅ Build complete
```

---

*本报告由 Agent 自动生成，覆盖后端 market 模块全链路审计。*
