# 【Day 2】把仓库系统做「能交付」：调拨、盘点、智能预警和权限，我踩了哪些坑

> Day 1 我们搞定了移动加权平均成本，账能算对了。但一个能算账的 Demo，离「能交付的系统」还差得远。

> 📌 本文是「从零搭建仓库进销存系统」系列的 **Day 2**。如果你还没看 Day 1（成本核算篇），建议先补一下——这一篇的很多逻辑都建立在「移动加权平均成本」之上。

---

## 一、Day 1 之后，朋友又来找我了

上次把成本核算做完，朋友高兴了没几天，又发来一连串问题：

- "我在城东和城西各有一个仓库，货经常来回调，**调过去之后那批货算多少钱一个？**"
- "月底盘库，**账上写着 300 个，实际数出来 297 个，那 3 个怎么办？**"
- "能不能库存快没了的时候**主动提醒我**，别每次都等断货了才发现？"
- "我请了两个操作员，他们扫码干活可以，但**利润这种东西不能给他们看**吧？"

我一条条看下来，发现了一个规律：

**Day 1 解决的是「怎么把账记对」，Day 2 要解决的是「记完账之后，这套账怎么在真实业务里跑起来」。**

前者是算法题，后者是工程题。而工程题往往更磨人。

---

## 二、调拨：成本价会「跟着货走」吗？

### 2.1 一个反直觉的问题

调拨看起来最简单：A 仓减 100 个，B 仓加 100 个，不就完了？

但加进 B 仓的这 100 个，**单价算多少？**

- 如果按 B 仓原来的平均成本算 → 那 A 仓的成本信息就凭空丢了
- 如果按 A 仓的成本价带过去 → B 仓的平均成本要重新算

正确答案是后者：**成本价要跟着货一起「搬家」**。这本质上是一次「B 仓的入库」，只不过入库单价来自 A 仓的当前平均成本。

### 2.2 核心逻辑：一个事务，两边库存

调拨的难点在于——**它同时动了两个仓库的库存，必须在同一个事务里完成**，任何一步失败都要整体回滚。

```java
@Transactional
public void transfer(...) {
    // ① 源仓库出库：按源仓当前平均成本扣减数量和金额
    BigDecimal unit = sourceInv.getAvgCost();
    sourceInv.setQuantity(sourceInv.getQuantity().subtract(quantity));
    sourceInv.setTotalAmount(...);           // 按平均成本扣金额
    // 写 TRANSFER_OUT 流水

    // ② 目标仓库入库：用「源仓成本价」做移动加权平均
    BigDecimal targetAvg = InventoryCostCalculator.transferAverageCost(
        targetInv.getQuantity(), targetInv.getTotalAmount(), quantity, unit);
    targetInv.setQuantity(targetInv.getQuantity().add(quantity));
    targetInv.setAvgCost(targetAvg);         // 目标仓平均成本重算
    // 写 TRANSFER_IN 流水
}
```

平均成本的合并逻辑复用了 Day 1 那套公式，只加了一个边界判断：

```java
// 如果目标仓库原本没有这个物品 → 直接用调出成本价
// 否则 → 按移动加权平均合并
return oldQuantity.signum() == 0
    ? incomingUnitCost.setScale(4, RoundingMode.HALF_UP)
    : averageCost(oldQuantity, oldAmount, incomingQuantity,
                  amount(incomingQuantity, incomingUnitCost));
```

> **一个小校验，省掉大麻烦**：创建调拨单时，一定要拦住「调出仓 = 调入仓」的操作。看起来是废话，但真实系统里操作员手滑选错仓库太常见了，不拦就会产生一条自己调给自己的诡异流水。
> ```java
> if (source.equals(target))
>     throw new BusinessException("调出与调入仓库不能相同");
> ```

调拨在流水表里会同时留下 `TRANSFER_OUT` 和 `TRANSFER_IN` 两条记录，一进一出，账面永远平衡。这也是 Day 1 强调的那句话的延续——**库存表是结果，流水表才是真相。**

---

## 三、盘点：账实不符的那几个，去哪了？

### 3.1 盘点的本质是「让账去迁就实物」

再精密的系统也架不住现实：货损、错发、被顺手拿走……于是账面数量和实际数量总会有差。

盘点要做的，就是**以实物为准，把账修正过来，并且留下一条可追溯的痕迹**。

我把盘点单设计成一个状态机：

```
DRAFT（草稿） → APPROVED / REJECTED（审核） → COMPLETED（执行完成）
```

### 3.2 三步走

**第一步：发起盘点，从当前库存生成账面数**

发起盘点时，系统自动把当前库存「拍个快照」，作为每一行的「账面数量」：

```java
StocktakeLine l = new StocktakeLine();
l.setItem(x.getItem());
l.setLocationCode(...);
l.setBatchNo(...);
l.setBookQuantity(x.getQuantity());  // 账面数量 = 当前库存
```

**第二步：录入实盘数，实时算差异**

```java
l.setActualQuantity(actual);
l.setDifferenceQuantity(actual.subtract(l.getBookQuantity()));  // 差异 = 实盘 - 账面
```

前端做了个小体验优化：差异为正（盘盈）显示绿色，为负（盘亏）显示红色，一眼就能看出哪里出了问题。

**第三步：执行调整，写进流水**

这是关键——差异不能直接改库存数字了事，而要走一次正式的「库存调整」，产生 `ADJUST_IN`（盘盈）或 `ADJUST_OUT`（盘亏）流水：

```java
for (StocktakeLine l : order.getLines()) {
    if (l.getActualQuantity() == null)
        throw new BusinessException("请先录入所有实盘数量");
    inventory.adjust(order.getStocktakeNo(), l.getItem().getCode(),
        warehouseId, l.getLocationCode(), l.getBatchNo(),
        l.getActualQuantity(), remark);  // 以实盘数为准做调整
}
```

调整时有个细节值得强调：**盘盈盘亏只改数量和金额，不改平均成本**。因为盘点差异是"数错了/丢了"，不是"重新进了货"，成本单价不应该被污染。

> **踩坑提醒**：盘点行是用 `物品编码 @ 库位 @ 批次` 拼出来的唯一 key。批次号可能为 null，直接拼接会导致 `"A@LOC@null"` 和 `"A@LOC@"` 被当成两行，账就对不上了。一定要先做 null 归一化：`Objects.toString(normalizeBatch(batchNo), "")`。

---

## 四、智能预警：别等断货了才发现

### 4.1 从「被动查」到「主动报」

Day 1 的系统只能"你去查，它才告诉你有多少库存"。但老板要的是"库存快没了，你主动来告诉我"。

我给每个物品加了一个 `safetyStock`（安全库存）字段，然后写了一段预警算法。它的聪明之处不只是"低于安全线就报警"，而是**结合近期出库速度，算出该补多少货**：

```java
// 1. 汇总该物品在所有仓库的当前库存
BigDecimal current = 各仓库存之和;
if (current >= item.getSafetyStock()) continue;   // 够用，跳过

// 2. 统计近 7 天出库量，算日均消耗
BigDecimal dailyAvg = 近7天出库量.divide(7, 2, HALF_UP);

// 3. 建议补货量 = 补到安全线 + 3 天缓冲
BigDecimal suggestedOrder = item.getSafetyStock()
    .subtract(current)
    .add(dailyAvg.multiply(3))
    .max(ZERO);

// 4. 分级：低于安全线一半 = 高危
String priority = current < safetyStock * 0.5 ? "HIGH"
                : current < safetyStock       ? "MEDIUM" : "LOW";
```

这样出来的预警不是干巴巴的"XX 缺货"，而是**"XX 缺 30 个，建议补 45 个，优先级：高"**——直接能拿去下采购单。

### 4.2 顺手做了个异常检测

既然流水表里什么都有，我又加了几条简单的异常规则，帮老板发现"看起来不太对劲"的情况：

| 异常类型 | 检测逻辑 | 级别 |
|---------|---------|------|
| 持续下降 | 同一物品连续 3 天以上出库 | 中 |
| 信息缺失 | 出库流水缺库位/批次 | 低 |
| 异常出库 | 单日出库量 > 安全库存 × 50% | 高 |

**这里想说的是**：所有预警和异常检测的数据源头，全都是 Day 1 建好的那张流水表。**前期把流水表设计扎实，后期的分析功能几乎是白送的。** 这就是数据设计的复利。

---

## 五、权限：老板看利润，操作员只扫码

### 5.1 别一上来就上 JWT

一提权限，很多人条件反射就是"上 JWT、上 OAuth2、上 Redis 存 Token"。

但对一个两三个用户、内网使用的小仓库系统，我选择了最轻的方案：**UUID Token + 内存存储**。

```java
public String issue(UserAccount user) {
    String token = UUID.randomUUID() + UUID.randomUUID();  // 拼两段，够随机
    sessions.put(token, new Session(
        new Principal(user.getUsername(), user.getRole(), user.getDisplayName()),
        Instant.now().plus(Duration.ofHours(12))));        // 12 小时过期
    return token;
}
```

- 无签名、无 refresh token、无 Redis
- 用一个 `ConcurrentHashMap` 存会话，重启即失效（对内网系统完全够用）
- 校验时顺手清理过期 Token

> 这和 Day 1 的选型哲学一脉相承：**架构复杂度要匹配业务复杂度。** 等真有几百个用户、需要多端登录了，再换 JWT 也不迟——那时你的系统早就跑起来了。

### 5.2 角色边界怎么落地

系统只有两种角色：`ADMIN`（老板/管理员）和 `WAREHOUSE`（操作员）。

**后端**用 Spring Security 的 URL 级拦截兜底，把角色塞进权限标识：

```java
new SimpleGrantedAuthority("ROLE_" + principal.role());
```

敏感操作（审核盘点单、管理用户等）再加一道 `ensureAdmin()` 校验：

```java
void ensureAdmin() {
    if (!"ADMIN".equals(当前用户角色))
        throw new AccessDeniedException("需要管理员权限");
}
```

**前端**则根据角色隐藏菜单和按钮，让操作员根本看不到"利润报表""用户管理"这些入口：

```javascript
{user.role === 'ADMIN' && <Menu.Item>用户与权限</Menu.Item>}
```

> **安全常识**：前端隐藏只是"体验",后端校验才是"防线"。前端藏起来的按钮，懂行的人照样能直接调接口——所以**后端那道 `ensureAdmin()` 一个都不能省**。

前端的 Token 传递也很简单，Axios 拦截器自动挂上：

```javascript
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('wms_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

---

## 六、扫码：二维码里到底存什么？

朋友最初以为二维码是个很玄乎的东西。其实拆开看特别朴素——**二维码里存的就是物品编码本身。**

后端用 Google ZXing 库把物品编码生成一张 PNG：

```java
BitMatrix matrix = new MultiFormatWriter().encode(
    item.getCode(), BarcodeFormat.QR_CODE, 280, 280,
    Map.of(EncodeHintType.MARGIN, 1));
```

扫码流程也就顺理成章：

```
扫码枪/小程序扫码 → 识别出物品编码 → 拿编码调 /stock/in/scan 或 /stock/out/scan
→ 走 Day 1 那套「加锁 → 算成本 → 更新库存 → 写流水」→ 返回结果
```

你看，扫码这一层只是个"输入方式"，真正干活的还是 Day 1 打好的地基。**好的架构，就是让新功能都能踩在旧地基上。**

---

## 七、Day 2 的三点总结

做完这一轮，我最大的感受是：

**1. 难的不是功能，是「一致性」。**
调拨要两仓一个事务，盘点差异要走正式流水，成本价要跟着货走——所有这些的核心，都是"任何时候账都不能乱"。这比多写几个 CRUD 页面难得多。

**2. 前期的数据设计，是后期的复利。**
预警、异常检测、各种报表，几乎没写新的存储逻辑，全靠 Day 1 那张流水表撑起来。**表结构设计对了，功能是会「长」出来的。**

**3. 复杂度要匹配业务，而不是匹配你的技术炫技欲。**
没上微服务，没上 JWT，没上消息队列。不是不会，是**这个业务体量还不需要**。能用最简单的方案交付、并且跑得稳，本身就是一种能力。

Day 1 讲"怎么把账算对"，Day 2 讲"怎么让这套账在真实业务里跑起来"。到这里，一套麻雀虽小五脏俱全的进销存系统，才算真正"能交付"了。

---

> **项目地址**：GitHub（私信获取）
> **技术栈**：Spring Boot 3 + JPA + React + Ant Design + MySQL + ZXing
> **本篇功能**：跨仓调拨、盘点调整、智能补货预警、异常检测、轻量权限、扫码出入库

---

*如果这两篇对你有帮助，欢迎点赞、转发、在看。评论区聊聊：你做过的系统里，最难搞的「一致性」问题是什么？*
