# 【Day 6】仓里有货，不如直接卖：给 WMS 长出一个商城子系统

> Day 5 收尾时我信誓旦旦预告过——Day 6 讲"多实例和真上线"，换 Redis、上 Flyway、跑两台服务器。结果这一周打开电脑，朋友的消息已经躺在那儿了：**"我仓库里有货，能不能直接在系统里卖？别再让我老婆挂朋友圈了。"**

> 📌 本文是「从零搭建仓库进销存系统」系列的 **Day 6**。计划里的 Redis 和 Flyway 都没动——因为这一周发生了一件更现实的事：**有货的仓，迟早要卖货。** 这一篇讲的是，怎么在已有 WMS 的地基上，一周长出一个商城子系统：商品、购物车、订单、库存扣减、四端打通，外加一份写完又被自己推翻过一次的审计清单。

---

## 一、计划为什么变了：业务永远比架构更急

Day 5 结束那天，我的 TODO 清单上是这样的：

- ❌ Token 换 Redis
- ❌ 建表脚本上 Flyway
- ❌ OCR 接真实识别
- ❌ 小程序填真 appid

每一项都"技术上正确、优先级合理"。但朋友的一句话把这张清单全推到了下周：

> "我现在手上有三十几样货堆在仓里，每天有人问价、有人要发货。你那个系统能不能让我**直接挂上去卖**，客户自己下单，我这边仓库收到就出库？"

这是真实开发里每天都在发生的事——**"计划好的架构优化"和"突然来的业务需求"，永远后者先赢。** 不是因为 Redis 不重要，而是因为 Redis 解决的是"多实例部署时的会话一致性"，而朋友现在**连单实例都还没跑起来做生意**。先有生意，再谈规模；先能卖货，再谈抗并发。

所以这一周的清单变成了：

- ✅ 商品上架（关联 WMS 物品）
- ✅ 购物车 + 收货人档案
- ✅ 下单 → 支付 → 审核 → 发货 → 收货 状态机
- ✅ 订单审核时 FIFO 扣减库存，取消时回滚
- ✅ 微信小程序（C 端）+ Web 管理后台（B 端）+ WMS 库存（底座）三端打通
- ✅ RBAC 权限矩阵扩展商城权限
- ✅ 一份商城模块的审计报告，自己审自己

**架构工作不是不做，是让位给"能不能先把生意跑起来"。** 这不是妥协，是优先级。

---

## 二、一个最该先想清楚的问题：商城要不要"另起炉灶"？

如果是从零做电商，商城和 WMS 是两套系统、两张库存表、两套单号。但我们的起点不一样：**WMS 里已经有物品、有库存、有仓库、有加权成本。** 所以第一个决策是——

> **商城的商品，必须是 WMS 物品的"投影"，而不是另一份独立的货。**

### 2.1 商品 = 物品 + 销售属性

`MarketProduct` 不存库存、不存编码，它只存"怎么卖"：

```java
public class MarketProduct extends AuditableEntity {
    @ManyToOne(optional = false) private Item item;     // 关联 WMS 物品（唯一）
    private String title, subTitle, mainImage, gallery;
    private BigDecimal salePrice, marketPrice;
    private Long salesCount = 0L, viewCount = 0L;
    private String status = "DRAFT";                     // DRAFT / SHELF_ON / SHELF_OFF
    ...
}
```

**关键约束**：一个 `Item` 只能上架一个 `MarketProduct`（`existsByItemId` 唯一校验）。这样**货永远是那一批货**，仓里出库了，商城库存自然少；商城卖出去了，仓里自然扣。不存在"商城说有 10 个、仓库说有 8 个"的鬼故事。

### 2.2 库存"读"和"扣"分两套口径

这是整个商城设计里最容易被坑、也最值得讲清楚的一点。

**读库存（给客户看的）**——用 `availableQty`，一个纯 SUM 查询：

```java
@Query("select coalesce(sum(i.quantity),0) from Inventory i "
     + "where i.item.id = :itemId and i.warehouse.id = :warehouseId")
BigDecimal availableQty(Long itemId, Long warehouseId);
```

商品详情页显示"现货 X 件"，就走它。**只读、不加锁、不阻塞**，客户刷新十次也没事。

**扣库存（下单履约用的）**——用 `findFifoForOut`，带 `PESSIMISTIC_WRITE` 行锁，按 `updatedAt` 升序 FIFO 扣减：

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from Inventory i where i.item.id = :itemId "
     + "and i.warehouse.id = :warehouseId and i.quantity > 0 order by i.updatedAt asc")
List<Inventory> findFifoForOut(Long itemId, Long warehouseId);
```

两个查询，一个**快但不算数**（给客户看），一个**慢但权威**（真扣库存）。为什么必须分开？因为如果下单走 `availableQty` 读出来的数来扣，**两个客户同时下单，都读到"还有 5 个"，都扣 5 个，结果超卖了。** 而带行锁的 `findFifoForOut` 会把两个扣减串行化——第二个进来时锁释放，读到的是扣减后的真实库存。

> **洞察**：电商系统里"显示的库存"和"真正扣的库存"永远是两个口径。显示要快、要允许轻微不准；扣减要准、必须加锁。把它们混用一个查询，要么慢死、要么错死。

### 2.3 FIFO 扣减：把 Day 5 的库龄思路用在了出库上

`deductStock` 是商城下单的核心。它的逻辑和 Day 5 讲过的库龄 FIFO 分层是同一个思路——**先进先出**：

```java
private void deductStock(MarketOrder order, String remark) {
    Warehouse warehouse = order.getWarehouse();
    for (MarketOrderItem oi : order.getItems()) {
        BigDecimal remaining = oi.getQuantity();
        List<Inventory> lots = inventories.findFifoForOut(oi.getItem().getId(), warehouse.getId());
        // 先校验总量够不够，不够直接抛异常，整单回滚
        BigDecimal sum = lots.stream().map(Inventory::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(remaining) < 0)
            throw new BusinessException("库存不足：" + oi.getItemName() + "（可用 " + sum + "）");
        // 逐层扣：最早入库的那批先消耗掉
        for (Inventory inv : lots) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = inv.getQuantity().min(remaining);
            inv.setQuantity(inv.getQuantity().subtract(take));
            // ... 写 OUT 流水，记 salePrice / saleAmount / profit
            remaining = remaining.subtract(take);
        }
    }
}
```

这里有一个**复用 Day 1 地基**的红利：扣库存时顺手把 `salePrice`、`saleAmount`、`profit` 写进了 `InventoryTransaction`。因为 WMS 的流水表本来就有这些字段（Day 5 利润报表口径修正时就靠它们）。**商城的每一笔销售出库，自动变成 WMS 的一条带利润的流水**——不需要再做任何对账，利润报表里自然就多了一笔。

**库存不足直接抛异常、整单回滚**，是这里最关键的一句。宁可让整个下单失败重来，也不留"扣了一半"的脏状态。

---

## 三、订单状态机：电商的骨架，也是最容易写错的地方

### 3.1 五个状态，两条出路

```
PENDING（待付款）─pay─→ AUDITED（已审核/待发货）─ship─→ SHIPPED ─receive─→ COMPLETED
   │                        │
   └─cancel─→ CANCELLED     └─forceCancel─→ CANCELLED（需回滚库存）
```

每个状态流转都走 `findForUpdateById`——**Day 5 那把"单据行锁"的红利，商城又吃了一次**。两个请求并发支付同一单？第二个进来时状态已经变了，直接抛"当前订单状态不可支付"。

### 3.2 库存什么时候扣：三个流派，选了一个

这是电商系统最经典的设计争议：**库存什么时候扣？** 有三个流派：

| 流派 | 扣库存时机 | 优点 | 缺点 |
|------|-----------|------|------|
| 下单扣 | `createOrder` 时 | 不超卖 | 下单不付，库存被占死（需超时释放） |
| 支付扣 | `pay` 时 | 不占库存 | 下单到支付间可能超卖 |
| 审核扣 | `audit` 时 | 人工兜底 | 审核前库存显示与实际不符 |

我们选的是**"支付即扣"**（`pay` 里调 `deductStock`），管理员审核（`audit`）通过时也扣一次（防重复扣由状态机保证）：

```java
public MarketOrder pay(UserAccount user, Long orderId, String operator) {
    MarketOrder order = orders.findForUpdateById(orderId).orElseThrow(...);
    if (!"PENDING".equals(order.getOrderStatus())) throw new BusinessException("当前订单状态不可支付");
    deductStock(order, "支付扣库存");        // ← 扣库存在这
    order.setOrderStatus("AUDITED");
    order.setPayStatus("PAID");
    ...
}
```

**为什么选支付扣而不是下单扣？** 因为朋友做的是小本生意，**不想搞"下单锁库存 15 分钟超时释放"那套复杂机制**。下单只是建个单，付了钱才动真格——逻辑简单，超卖风险用支付时的行锁兜底。如果将来量大到"支付窗口内超卖"成真问题，再迁到"下单预占 + 支付确认"两段式。

> **架构选型没有对错，只有"当前规模下哪个最简单且不出事"。** 朋友一个月几百单，搞两段式库存就是杀鸡用牛刀。

### 3.3 取消订单：扣了的库存必须回滚

`forceCancel` 里有一段关键的分支判断——**只有已经扣过库存的订单，取消时才回滚**：

```java
String prevStatus = order.getOrderStatus();
if ("AUDITED".equals(prevStatus) || "SHIPPED".equals(prevStatus)) {
    rollbackStock(order, "取消回滚库存");   // 写 IN 流水，把库存加回去
}
order.setOrderStatus("CANCELLED");
```

`rollbackStock` 写的是 `TransactionType.IN` 流水——**和入库流水同一个口径**。这样账上永远是平的：卖出去扣 OUT，取消加 IN，一进一出有据可查。**回滚不是"删掉那条扣减流水"**（那样审计时看不出发生过取消），而是**新写一条反向流水**——这和 Day 5 讲红冲时的逻辑完全一致：**错单要留痕，冲销要有迹。**

---

## 四、价格快照：电商系统里最容易被忽略的"账"

如果有人问我"这一周哪个设计最让你后怕"，是这一条——**购物车里的价格，必须快照**。

### 4.1 不快照会怎样

假设不加 `snapshotPrice`，购物车只存 `productId + quantity`，价格实时从 `MarketProduct` 读。场景：

1. 客户把 100 块的商品加购物车
2. 管理员后台改价成 120 块
3. 客户回来下单，付了 120 块——**他没同意过这个价**

反过来更糟：客户加购物车时 120 块，管理员降价成 100 块，客户付了 100——**朋友少收了 20，还说不清是谁的锅**。

### 4.2 加车即定格

`MarketCart` 里存了 `snapshotPrice`，加购物车那一刻定格：

```java
public MarketCart addCart(UserAccount user, Long productId, Integer quantity) {
    MarketProduct product = products.findById(productId).orElseThrow(...);
    return carts.findByUserIdAndProductId(user.getId(), productId)
        .map(c -> { c.setQuantity(c.getQuantity() + qty); return carts.save(c); })
        .orElseGet(() -> carts.save(new MarketCart(user, product, qty, product.getSalePrice())));
        //                                                                              ↑ 快照
}
```

但**下单时的最终价格，取的是商品的当前售价**（`createOrder` 里 `oi.setSalePrice(price)`），不是购物车快照——因为下单才是"成交时刻"。购物车快照只用于**展示"你加车时是这个价"**，成交价以下单瞬间为准。这是电商的通行规则：**加车不锁价，下单才锁价。** 两层快照，各管各的时机。

> **洞察**：任何涉及"价格"的系统，都要问一句——**这个价格是在哪个时刻确定的？** 没有快照，价格就是一笔糊涂账。这个教训不只适用于电商，进销存的采购单、销售单、调拨单，只要金额会变，都需要快照。WMS 里 `StockDocumentLine` 的 `unitPrice` 也是同一个道理。

---

## 五、权限：商城不是 WMS 的附庸，是第四种角色

### 5.1 RBAC 矩阵扩展：8 个新权限

Day 5 把权限做成了矩阵，这次扩展得异常顺手——`Permissions.java` 加 8 个常量，`RolePermissions` 里给 ADMIN 分配，**一行不用改业务代码**就接上了：

```java
// —— 商城子系统（market）——
public static final String MARKET_BUY   = "market:buy";     // 小程序：浏览/购物车/下单
public static final String MARKET_READ  = "market:read";    // 读商品/订单
public static final String PRODUCT_READ = "product:read";   // 后台：商品读
public static final String PRODUCT_WRITE= "product:write";  // 后台：商品写（上下架/改价）
public static final String ORDER_READ   = "order:read";     // 后台：订单读
public static final String ORDER_REVIEW = "order:review";   // 后台：审核/强制取消
public static final String ORDER_EXECUTE= "order:execute";  // 后台：发货/确认完成
public static final String CUSTOMER_READ= "customer:read";  // 后台：客户档案读
public static final String CUSTOMER_WRITE="customer:write";// 后台：客户档案写
```

### 5.2 用户端和管理端：两条路径，两种校验

商城的接口天然分两层，Controller 也分两个：

- **`MarketController`（`/market/*`）**——C 端，小程序用。下单、购物车、收藏这类操作强制 `market:buy`；读商品、读自己订单用 `market:read`。
- **`MarketAdminController`（`/admin/market/*`）**——B 端，Web 后台用。审核、发货、商品管理各自挂 `order:review` / `order:execute` / `product:write`。

**为什么要分两个 Controller？** 因为 C 端和 B 端的"谁能干什么"完全不同：客户能下单但不能审核，管理员能审核但不能替客户下单（代下单是另一回事）。把两套接口混在一个类里，权限校验会乱成一锅粥。分两个类、两条路径，**权限边界天然清晰**——这正是 Day 5 那套"矩阵 + 双保险"设计在商城场景下的第一次实战检验。

> **设计的价值，不在它第一次用时多优雅，而在它第三次扩展时还顺不顺。** Day 5 写权限矩阵时我还不确定这套设计扛不扛得住新业务，这次商城一加，8 个权限、2 个 Controller、零业务代码改动——设计过关了。

---

## 六、四端打通：一个货，四双眼睛

这一周真正的工作量不在后端，而在**把四端对齐**。后端商城逻辑 376 行 Service + 356 行用户 Controller + 199 行管理 Controller 就写完了，但让四端看到一致的货、一致的价、一致的库存，才是最磨人的。

### 6.1 四端各看什么

| 端 | 技术 | 关心什么 | 关键页 |
|----|------|---------|--------|
| **WMS 后端** | Spring Boot | 货、库存、流水、成本 | Inventory / Transaction |
| **Web 管理后台** | Vue 3 + Ant Design Vue | 商品上下架、订单审核发货、客户管理、销售看板 | MallProducts / MallOrders / MallCustomers / MallDashboard |
| **微信小程序（C 端）** | uni-app + Vue 3 | 逛商品、加购物车、下单支付、看订单 | index / product / cart / checkout / orders |
| **微信小程序（仓管端）** | 同一个小程序，登录角色不同 | 扫码出入库、盘点 | （复用 wms-miniapp 的扫码页） |

### 6.2 那个差点翻车的状态名

打通四端最容易翻车的，是**状态名不一致**。商城订单状态机里写的是 `AUDITED`（已审核），但 Web 管理后台一开始用的是 `APPROVED`——结果审核通过的订单，发货按钮永远不显示，因为前端判断 `r.orderStatus === 'APPROVED'` 永远不成立。

这种 bug 不会报错、不会 500，就是**按钮不出现**。后端说"我返回 AUDITED 了啊"，前端说"我判断 APPROVED 了啊"，两边都对，合起来就是错的。

还有支付方式：后端枚举是 `PAY_ONLINE` / `CASH_ON_DELIVERY` / `CREDIT`，前端一开始映射的是 `WECHAT` / `ALIPAY` / `CASH`——**显示出来全是乱码**。

这些都在审计报告里列成了 P0（`MARKET_AUDIT_REPORT.md` 第 2 节，9 条已修复 bug，大部分是这种"两端各说各话"）。修法很朴素：**后端定义什么，前端原样用，不搞"翻译"**。状态名、枚举值、字段名，三端必须一字不差。

> **打通多端的本质，是消除"翻译层"。** 每多一层翻译，就多一个出错点。后端是唯一的真相源（single source of truth），前端不"翻译"，只"渲染"。

### 6.3 小程序的登录：复用 wms-miniapp 那套

商城小程序（`wms-shopping-miniapp`）是新建的独立工程，但登录流程**完全复用了仓管小程序（`wms-miniapp`）打通的那套微信登录**：

```
uni.login() → POST /auth/wx-login {code}
  ├─ 已绑定  → 返回 token
  └─ 未绑定  → {needBind:true} → POST /auth/wx-bind {openid,username,password}
```

后端 `WechatService` 一行没改——**同一个 `/auth/wx-login`，既给仓管员用，也给客户用**。区分他们的不是登录接口，而是登录后拿到的权限：客户只有 `market:buy` / `market:read`，仓管员有 `inventory:*` / `document:*`。**一个登录入口，两套权限，自然分流**——这是 Day 5 权限矩阵的又一次红利。

商城小程序的 `request.js` 也和仓管小程序同构：token 存 `uni.storage`，401 自动跳登录，响应解壳（`code===200` 才 resolve）。**两端连代码都几乎一样**，迁移成本趋近于零。

---

## 七、销售看板：给"做生意"一个数字

Web 后台新增了 `MallDashboardPage.vue`——这是朋友第一次能在系统里看到"今天卖了多少"：

- **累计销售额**（已完成订单合计）
- **今日订单数 / 今日销售额**
- **在售商品数 / 客户数**
- **订单状态分布**（待付款 / 待发货 / 已发货 / 已完成 / 已取消 / 已驳回）
- **热销商品 Top 10**（按已完成订单销量）

后端 `/admin/market/stats` 一个接口返回全部，前端一次渲染。**这里有个小心机**：累计销售额只统计 `COMPLETED` 状态的订单，不包括"已发货未确认收货"的——因为朋友做的是款到发货，但"确认收货"才算真正落袋。**数字要保守，不能把还没到客户手里的钱算成已赚。** 这和 Day 5 利润报表"只统计销售出库"的口径修正一脉相承：**报表里的数字，宁少勿多。**

热销 Top 10 直接用了一条聚合 SQL：

```java
@Query("select i.itemName, i.itemCode, sum(i.quantity), sum(i.subtotal) "
     + "from MarketOrderItem i where i.order.orderStatus='COMPLETED' "
     + "group by i.item.id order by sum(i.quantity) desc")
List<Object[]> topProducts(Pageable pageable);
```

**`group by` 出来的就是现成的销量榜**——JPA 聚合查询在这种场景下比写 Java 循环省事一百倍。

---

## 八、自己审自己：一份 9 条 bug 的审计清单

写完商城，我照例做了一次审计，写了 `MARKET_AUDIT_REPORT.md`。**这次审出 9 个 P0 bug，全是我自己写出来的**——没有任何外力评审。列几条最典型的：

| # | bug | 根因 | 修复 |
|---|-----|------|------|
| 1 | 商品页用 `product.salePrice` 当库存显示 | 字段名混淆 | 改用 `availableStock` |
| 3 | `doAdd()` 调 `useUserStore().setUserInfo({cartId})` | 错用 API，污染用户状态 | 改为正确调用 |
| 5 | 结算页 `onShow` 未回填地址选择结果 | 生命周期钩子漏接 | 补 `onShow` 回填 |
| 6 | `DELETE /market/cart` 不支持空 body 清空 | 后端强求 body | `required=false` 兜底 |
| 8 | 状态名 `APPROVED` vs `AUDITED` 不一致 | 两端各说各话 | 前端对齐后端枚举 |
| 9 | 支付方式 `WECHAT` vs `PAY_ONLINE` 不一致 | 同上 | 同上 |

**第 8、9 条是"多端打通"的典型病**——后端定义了一套枚举，前端"自作主张"翻译了一套，合起来就错。**第 6 条是"REST 语义"的典型病**——`DELETE` 本不该强求 body，但小程序清空购物车时不传 body，后端就该 `required=false` 兜底。

这些 bug 没有一个是"架构性问题"，全是"细节没对齐"。但**正是这些细节，决定了系统"能用"还是"不能用"**。朋友不会因为"你用了 RBAC 矩阵"而觉得系统好，但他会因为"点发货按钮没反应"而觉得系统烂。

> **审计的价值，不在发现大问题，而在把"差不多就行"的细节一个个钉死。** 9 条 bug，每一条单独看都很小，合起来就是一个"能不能交付"的鸿沟。

---

## 九、诚实的"还没做"清单（更新版）

Day 5 那张清单，这周又长了几项：

| 事项 | 现状 | 影响 |
|------|------|------|
| Token 存储 | 仍是进程内 `ConcurrentHashMap`，12h TTL | 单实例够用，多实例必须换 Redis（Day 5 遗留） |
| 数据库迁移 | 仍是 `ddl-auto: update` | 无版本化、无法回滚，建议上 Flyway（Day 5 遗留） |
| 真实 OCR | 仍是 mock | 不能依赖真实识别（Day 5 遗留） |
| **真实微信支付** | `pay` 接口仅模拟成功，未接微信支付 | **不能真收款，生产前必须接 JSAPI/小程序支付** |
| **库存预占** | 下单不锁库存，支付才扣 | 支付窗口内可能超卖，量大需改两段式 |
| **订单超时取消** | 无定时任务释放未支付订单 | 待付款订单会一直占着，需加定时清理 |
| **退款流程** | 仅支持取消回滚，无独立退款单 | 退款需走 WMS 退货入库流程，未独立建模 |
| **物流对接** | 物流公司/单号纯手工填 | 未接快递鸟/快递 100 等查询 |
| 数据权限 G5 | 未做 | WAREHOUSE 仍能看到所有仓库（Day 5 遗留） |
| 批次效期 G9 | 未做 | 无 FEFO 先效期先出（Day 5 遗留） |

**这次最该说清楚的是"微信支付"**。现在的 `pay` 接口只是把订单状态改成 `PAID`、扣了库存，**没有真收一分钱**。朋友要真做生意，这一步必须接——好在微信小程序支付的接入路径是标准的（`JSAPI` 下单 → 调起支付 → 支付结果回调），后端加一个 `WxPayService` 就行。这会是 Day 7 的第一个任务。

**库存预占**和**订单超时取消**是一对孪生问题：如果将来改成"下单锁库存"，就必须配"超时未支付自动取消释放库存"的定时任务。现在选"支付扣"规避了这对复杂性，但代价是支付窗口内的超卖风险。**这是一个明确的、被接受的技术债**——不是不知道，是知道且判断当前规模下值得。

---

## 十、Day 6 的几点总结

**1. 计划赶不上业务，不是借口，是优先级。**

Day 5 预告的 Redis/Flyway 没做，不是忘了，是被"朋友要卖货"这件事挤掉了。**架构优化的价值是"让系统能扛更大规模"，而业务需求的价值是"让系统现在就能赚钱"。** 先赚钱，再扛规模。这不是短视，是务实。

**2. 商城是 WMS 的"投影"，不是"另一套系统"。**

商品关联物品、库存共用一张表、流水共用一个口径、成本共用加权平均——**商城没有新建任何"货"的概念**。这带来的最大好处是：账永远是一套账。仓里出库了，商城库存自然少；商城卖出去了，WMS 流水自然多一条带利润的 OUT。**一体化比"两个系统对接"省掉的对账成本，是难以估量的。**

**3. "显示库存"和"扣减库存"必须分两套口径。**

一个只读 SUM（快、允许轻微不准），一个带行锁 FIFO（慢但权威）。混用的后果只有两个：要么慢死、要么超卖。这不是优化，是设计。

**4. 多端打通的本质是"消除翻译层"。**

后端定义 `AUDITED`，前端就别用 `APPROVED`；后端定义 `PAY_ONLINE`，前端就别用 `WECHAT`。每多一层翻译，就多一个"按钮不出现"的玄学 bug。**单一真相源，前端只渲染不翻译。**

**5. 价格快照，是电商系统的"账本"。**

加车快照、下单快照，两层各管各的时机。没有快照，价格就是一笔糊涂账——这个道理同样适用于进销存的采购单、销售单。**任何会变的金额，都要问一句"这个数是哪个时刻定的"。**

**6. 权限矩阵的第三次扩展，零改动接上。**

Day 5 写 RBAC 矩阵时还忐忑它扛不扛得住，商城一加 8 个权限、2 个 Controller、业务代码一行没动——**设计过关了。好的设计，第三次用的时候最值钱。**

**7. 自己审自己，9 条 bug 都是细节。**

没有架构性问题，全是字段名混淆、状态名不一致、生命周期钩子漏接。但正是这些细节决定"能不能交付"。**审计不挑大问题，挑"差不多就行"的钉子。**

---

> **项目地址**：GitHub（私信获取）
> **技术栈**：Spring Boot 3 + JPA + Vue 3 / Ant Design Vue + uni-app 商城小程序 + uni-app 仓管小程序 + MySQL / H2 + AOP + RBAC 权限矩阵 + Python 回归
> **本篇功能**：商城子系统（商品/购物车/收货人/订单/收藏）、订单状态机（PENDING→AUDITED→SHIPPED→COMPLETED）、支付扣库存 + FIFO 扣减 + 取消回滚、价格快照机制、RBAC 权限矩阵扩展 8 项、Web 商城管理后台（商品/订单/客户/看板）、微信小程序商城端、商城模块审计报告（9 条 P0 修复）

---

## 📮 预告：Day 7 讲什么？

这一周把商城跑起来了，但有个最扎心的事实——**`pay` 接口没真收过一分钱**。朋友能不能真的在系统里收到客户的微信付款？这是 Day 7 的第一个任务：

- **微信支付接入**：小程序 `JSAPI` 下单 → 调起支付 → 支付结果回调，后端 `WxPayService` 怎么写、怎么验签、怎么处理"支付成功但回调超时"。
- **订单超时取消**：待付款订单怎么定时清理、清理时怎么保证库存没被扣（因为我们是支付才扣）。
- **退款流程**：独立退款单，还是走 WMS 的退货入库？两套怎么对账。

Day 7 我们处理"让商城能真收款"这一摊：**把模拟支付换成真微信支付，让朋友的生意能真正闭环。**

---

*如果这六篇对你有帮助，欢迎点赞、转发、在看。评论区聊聊：你做过的项目里，"计划好的架构优化"和"突然来的业务需求"，最后谁赢了？*
