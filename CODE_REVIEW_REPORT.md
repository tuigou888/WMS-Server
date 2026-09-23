# WMS 商城子系统 — 全面测试与代码评审报告

**评审日期：2026-08-14**
**评审范围：商城模块（后端 + 管理后台 + 小程序 + 微信支付集成）**

---

## 一、测试与构建结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 后端单元测试 | `mvn test` | ✅ 通过（exit 0），4 个测试类全部通过 |
| 前端管理后台构建 | `npm run build` | ✅ `built in 8.49s`，BUILD SUCCESS |
| 新增依赖 | `wechatpay-java:0.2.14` | ✅ 官方 APIv3 SDK |

> ⚠️ **测试覆盖盲区**：4 个测试类均为原有 WMS 模块（InventoryCostCalculator、AdjustmentIntegration、DocumentNumberService、AuthControllerWx），**商城模块和微信支付模块零测试覆盖**。`MARKET_AUDIT_REPORT.md` 第 13 条已提及此问题但未修复。

---

## 二、严重问题（P0 — 必须修复）

### 🔴 P0-1 在线支付订单与人工审核存在重复扣库存竞态

**位置**：`MarketService.java:282-308`（`markPaid`）与 `MarketService.java:358-376`（`audit`）

**问题**：`markPaid()` 和 `audit()` 都在 `orderStatus == "PENDING"` 时执行 `deductStock()`，且都会将状态置为 `AUDITED`。两者都用了 `findForUpdateById` 悲观锁串行化，但逻辑上没有互斥保护：

```
在线支付流程（预期）：createOrder(PENDING) → prepay → 微信回调 markPaid(PENDING→AUDITED, 扣库存) → ship
人工审核流程：       createOrder(PENDING) → audit(PENDING→AUDITED, 扣库存) → ship
```

**冲突场景**：在线支付订单（`payType=PAY_ONLINE`）处于 PENDING 等待用户付款时，管理员在后台对它点了「审核通过」：
1. `audit()` 执行 `deductStock`（第一次扣库存）→ 状态变 AUDITED
2. 用户随后完成微信支付 → 回调 `markPaid`，但此时 `orderStatus` 已是 `AUDITED` 而非 `PENDING`，`markPaid` 走 `log.warn` 直接返回，**transactionId 未回填、paidAt 未设置、PAY 日志未写**

**影响**：库存被扣一次但支付状态混乱（订单已 AUDITED 但 payStatus 仍 UNPAID），后续 `ship()` 会因 `payStatus != "PAID"` 被阻断，订单卡死。

**建议**：在 `audit()` 入口增加支付方式判断——`PAY_ONLINE` 类型订单不应走人工审核扣库存，或在线支付的 audit 只改审核标记不扣库存。

### 🔴 P0-2 `rollbackStock` 回滚库存未加悲观锁，与并发出库存在竞态

**位置**：`InventoryRepository.java:39-40`（`findByItemAndWarehouse`）与 `MarketService.java:471-496`（`rollbackStock`）

**问题**：扣库存路径 `deductStock` 用的 `findFifoForOut` 带了 `@Lock(PESSIMISTIC_WRITE)`，但回滚路径 `rollbackStock` 用的 `findByItemAndWarehouse` **没有加锁**：

```java
// 扣库存 — 有锁 ✅
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Inventory> findFifoForOut(...)

// 回滚库存 — 无锁 ❌
@Query("select i from Inventory i where ... order by i.updatedAt asc")  // 无 @Lock
List<Inventory> findByItemAndWarehouse(...)
```

**影响**：退款回滚 / 取消回滚与另一笔订单的出库扣减并发时，`rollbackStock` 读取的 `inv.getQuantity()` 和写入之间存在 TOCTOU 窗口，可能导致库存数量错乱（丢失更新）。

**建议**：给 `findByItemAndWarehouse` 加 `@Lock(LockModeType.PESSIMISTIC_WRITE)`。

### 🔴 P0-3 `rollbackStock` 使用 `avgCost` 回滚导致成本失真

**位置**：`MarketService.java:479-485`

**问题**：回滚时用当前库存记录的 `avgCost` 计算回填成本，而非扣减时记录的实际成本：

```java
Inventory inv = lots.get(0);  // 取 updatedAt 最旧的一条
BigDecimal addCost = inv.getAvgCost().multiply(addQty);  // 用当前均价而非原出库成本
```

如果扣减后该库存批次的价格发生过变动（如期间有新入库调整了均价），回滚金额与原出库金额不一致，导致库存总金额与实际不符。此外 `lots.get(0)` 取最旧记录，如果该记录 `quantity=0`（已售罄的批次），其 `avgCost` 可能已无意义。

**建议**：从订单明细或出库流水（`InventoryTransaction`）回查原始成本，或至少过滤 `quantity > 0` 的记录。

---

## 三、重要问题（P1 — 建议尽快修复）

### 🟠 P1-1 微信支付回调未做金额校验

**位置**：`WechatPayNotifyController.java:72-75`

**问题**：回调验签解密后直接 `markPaid`，**未校验回调金额与订单 `totalAmount` 是否一致**。虽然微信侧会校验，但作为防御性编程，应核对 `tx.getAmount().getTotal()` 与 `order.getTotalAmount()`（转分）是否相等，防止订单被篡改后以错误金额支付成功。

### 🟠 P1-2 `prepay` 使用 `@Transactional` 长事务持有外部 HTTP 调用

**位置**：`MarketService.java:257-264`

**问题**：`prepay()` 标注了 `@Transactional`，内部调用 `wechatPay.prepay()` 发起对微信的 HTTP 请求。这会导致**数据库事务在整个 HTTP 调用期间保持打开**（包括 `findForUpdateById` 的悲观锁），如果微信接口响应慢，锁持有时间会很长，影响其他订单操作。

**建议**：将 `prepay` 改为只读查询订单（不加事务或 `readOnly=true`），支付参数生成在事务外完成。

### 🟠 P1-3 `refund` 退款金额未校验上限

**位置**：`MarketService.java:334`

**问题**：`order.setRefundAmount(order.getTotalAmount())` 直接取订单总额退款，未校验订单是否已部分退款。虽然当前是全额退款场景，但如果未来支持部分退款，这里会覆盖。且退款未校验 `refundAmount > 0`（订单总额为 0 时退款会发起到微信侧）。

### 🟠 P1-4 `MarketAdminController.createCustomer` 传 `null` user 导致 NPE 风险

**位置**：`MarketAdminController.java:186` 与 `MarketService.java:160-170`

**问题**：管理员创建客户时 `service.saveCustomer(null, req)`，而 `saveCustomer` 内部：
```java
boolean def = Boolean.TRUE.equals(req.defaultFlag()) || (user != null && customers.countByUserId(user.getId()) == 0);
```
虽然有 `user != null` 守卫，但后续 `customers.findFirstByUserIdAndDefaultFlagTrue(user.getId())` 在 `def` 为 true 时会因 `user.getId()` 抛 NPE。实际上当 `req.defaultFlag()` 为 true 时就会走到这里。

**建议**：管理员端创建客户不应设置 defaultFlag，或在 `saveCustomer` 开头增加 `user == null` 的早返回。

### 🟠 P1-5 订单实体注释与代码状态不一致

**位置**：`MarketOrder.java:15, 49`

**问题**：实体注释写的是 `SHIPPING`（已发货），但所有代码（Service、Controller、Repository、前端）统一使用 `SHIPPED`。纯注释问题不影响运行，但会误导开发者。

### 🟠 P1-6 `favorites` 分页 total 返回错误

**位置**：`MarketController.java:283`

**问题**：
```java
result.put("total", (long) rows.size());  // ❌ 返回过滤后的行数
```
`rows.size()` 是过滤掉已删除商品后的行数，而非数据库实际分页总数 `p.getTotalElements()`。这会导致前端分页器总条数错误（当有已删除商品的收藏项时）。

---

## 四、一般问题（P2 — 改进建议）

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| P2-1 | `MarketService.java:234` | 下单时用 `product.getSalePrice()` 而非购物车快照价 `cart.getSnapshotPrice()`，购物车快照机制形同虚设 | 改用 `cart.getSnapshotPrice()` |
| P2-2 | `MarketService.java:398` | `ship()` 末尾清空购物车 `carts.deleteByUserId`，但发货发生在下单很久之后，购物车早已在别处清空或变化，逻辑不合理 | 移到 `createOrder` 后清空 |
| P2-3 | `MarketService.java:421` | `forceCancel` 对 SHIPPED 状态回滚库存后订单仍能被标记 CANCELLED，但已发货商品可能已签收，回滚库存语义不对 | SHIPPED 状态不应允许直接取消 |
| P2-4 | `MarketOrder.java:49` | `orderStatus` 用 String 硬编码，全靠字符串匹配，无编译期校验 | 引入枚举（`MARKET_AUDIT_REPORT.md` 建议项#1，仍未实现） |
| P2-5 | `WechatPayService.java:165-170` | `queryByOutTradeNo` mock 模式返回 null，但真实模式返回 Transaction，调用方无法区分"mock跳过"和"查单失败" | 用 Optional 或抛异常区分 |
| P2-6 | `WechatPayNotifyController.java:86-88` | `ok()` 返回 `ResponseEntity.ok().build()` 无 body，但方法签名声明返回 `Map<String,Object>`，虽然 Spring 允许但语义不清 | 统一返回空 Map 或改返回类型 |
| P2-7 | `MarketAdminController.java:60-68` | 商品列表先查全量再内存过滤 status，分页 total 不准 | 在 Repository 查询中加 status 条件 |
| P2-8 | `MallOrdersPage.vue:141,146` | `statusColor`/`statusLabel` 含 `RETURNED`/`REFUSED` 等后端不存在的状态 | 清理无用映射 |
| P2-9 | `product.vue:111` | `doBuy` 确认弹窗显示 `¥${this.product.salePrice}` 未格式化，可能显示 `¥12.5` 而非 `¥12.50` | 用 `formatPrice()` |
| P2-10 | `checkout.vue:77` | 支付方式有 `CREDIT`（挂账）选项在后端定义，但前端只暴露微信支付和货到付款 | 确认是否需要挂账入口 |

---

## 五、安全评审

| 项 | 结论 |
|---|---|
| **回调路径放行** | ✅ `/market/pay/notify` 在 `SecurityConfig` 中 `permitAll()`，微信服务器不带 token 可访问，正确 |
| **回调验签** | ✅ 使用官方 SDK `NotificationParser`，基于微信支付公钥验签 + AES-GCM 解密，防伪造 |
| **回调幂等** | ✅ `markPaid` 内部检查 `payStatus == "PAID"` 直接返回，防重复扣库存 |
| **生产 mock 防护** | ✅ `WechatPayService.isMock()` 和 `WechatPayConfig.validate()` 双重校验生产环境禁开 mock |
| **越权防护** | ✅ 购物车/订单/收货人操作均校验 `user.getId()` 归属 |
| **CSRF** | ✅ 全局 `csrf.disable()`，配合 STATELESS + JWT 合理 |
| **金额单位** | ✅ `toFen()` 用 BigDecimal + HALF_UP，无浮点误差 |
| **⚠️ 金额校验缺失** | 见 P1-1，回调未校验金额 |
| **⚠️ 敏感日志** | `WechatPayService` 日志未打印完整回调 body（合理），`MarketService.markPaid` 日志打印了 transactionId（可接受） |

---

## 六、架构亮点

1. **库存快照设计**（部分实现）：`MarketCart.snapshotPrice` 保存加购时价格，`MarketOrderItem` 保存下单时商品快照，价格变动不影响历史订单。（但 P2-1 指出下单未真正使用快照价）
2. **悲观锁防超卖**：`findFifoForOut` + `findForUpdateById` 双重 `PESSIMISTIC_WRITE` 锁，扣库存路径并发安全。
3. **支付双模式**：mock/真实模式清晰隔离，`@ConditionalOnProperty` 按 profile 激活 SDK Bean，开发联调无需真实商户参数。
4. **操作日志全程记录**：`MarketOrderLog` 覆盖 CREATE/PAY/AUDIT/SHIP/COMPLETE/CANCEL/REFUND 全状态流转。
5. **收藏并发安全**：`toggleFavorite` 用 `existsByUserIdAndProductId` 预检 + catch `DataIntegrityViolationException` 兜底，避免并发唯一约束冲突。

---

## 七、优先修复建议

| 优先级 | 问题 | 工作量 |
|---|---|---|
| **P0** | 在线支付订单与人工审核的扣库存互斥（P0-1） | 中 — `audit()` 加支付方式判断 |
| **P0** | `rollbackStock` 加悲观锁（P0-2） | 小 — 加 `@Lock` 注解 |
| **P0** | 回滚成本取实际出库成本（P0-3） | 中 — 查 `InventoryTransaction` |
| **P1** | 回调金额校验（P1-1） | 小 |
| **P1** | `prepay` 事务边界（P1-2） | 小 — 去掉 `@Transactional` |
| **P1** | `createCustomer(null)` NPE（P1-4） | 小 |
| **P1** | 收藏分页 total 修复（P1-6） | 小 |
| **P2** | 下单使用快照价（P2-1） | 小 |

---

## 八、总结

本次商城子系统变更规模较大（后端 20 个 Java 文件 + 管理后台 4 页面 + 小程序 12 页面 + 微信支付全链路），整体架构清晰、分层合理，安全防护（回调验签、mock 防护、越权校验）到位。后端测试与前端构建均通过。

**核心风险**集中在**库存扣减的状态机互斥**（P0-1）和**回滚路径的并发安全**（P0-2/P0-3），这两类问题在生产并发场景下会导致库存数据不一致。建议在上线前优先修复 P0 级别问题，并补充商城模块的集成测试覆盖（当前为零）。

---

*本报告由 ZCode 自动生成，覆盖后端 market 模块全链路审计。*
