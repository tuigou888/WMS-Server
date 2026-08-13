# 【Day 5】从"45 分"到"敢谈上线"：差距审计、凭证闭环、权限矩阵，一次生产化整改实录

> Day 4 我们给系统打了 45 分，还列了一堆 P0。朋友看完沉默了一会儿，说："那把这些排个优先级，下个月开始改。" 这篇要讲的是——**他真的催我改的那一周，我到底改了什么、没改什么、为什么。**

> 📌 本文是「从零搭建仓库进销存系统」系列的 **Day 5**。这一篇是 Day 4 那份诚实报告的"整改篇"：先做功能差距审计，再补"账的闭环"，然后一个个 P0 啃下去。全程不换架构、不上新技术，只做一件事——**把"账会错"的地方一个个堵上。**

---

## 一、整改前第一件事：不是改代码，是"审计"

### 1.1 一张 21 行的差距表

朋友走后，我第一反应是去改 P0：单号持久化、单据幂等、Token 跨实例……但改了两天我停下来问自己：**我凭什么确定这些就是最该改的？**

判断不了，就去找参照物。我把系统对着几套成熟的开源/商业进销存和 WMS 系统过了一遍——jshERP（华夏ERP）、RuoYi-WMS、jeewms、管家婆、旺店通——然后写了一份 `AUDIT_REPORT.md`，把差距一条条列出来，编号 G1~G21，按 P0/P1/P2 分级：

| 编号 | 缺失项 | 缺失后果 | 优先级 |
|------|--------|----------|:--:|
| **G1** | 操作日志 / 审计追踪 | 谁改了库存、谁审核了单据，无法追溯；合规审计直接失败 | P0 |
| **G2** | 退货入库 / 退货出库单 | 客户退货、退供应商无标准流程，只能硬塞进出库单 | P0 |
| **G3** | 报损 / 报溢单 | 货物破损、丢失、过期无凭据入账，账永远对不平 | P0 |
| **G4** | 单据反审 / 红冲机制 | 已执行的单据录错了没法撤回，只能手工改库 | P0 |
| **G5** | 数据权限（仓库范围） | WAREHOUSE 角色能看到所有仓库的库存和单据 | P0 |
| **G6** | 单据号落库序列 | 重启 `AtomicLong` 重置 → 单据号重复 | P0 |
| **G7** | 库龄分析报表 | 呆滞料无法识别，货放多久没人知道 | P1 |
| **G8** | 收发存汇总表 | 期末对账缺汇总数据，会计少一张基础表 | P1 |
| … | … | … | … |

> 完整矩阵在 `AUDIT_REPORT.md`，这里只摘了几行。它最大的价值不是那 21 个编号，而是**第一次用"业务能不能闭环"的视角，替代"代码能不能跑"的视角去审视系统。**

### 1.2 审计推翻了我自己的判断

这是 Day 5 最打脸也最重要的一刻。

Day 4 报告里，我脑子里全是工程师视角的担忧：并发重复执行、MySQL 兼容性、`@Transactional` 是不是漏了。但审计把我拉回业务视角之后发现，**真正卡着朋友上线的根本不是并发，而是一堆"凭证"缺失**：

- 客户退货了，账上只能做一张"出库单"——**退的是客户的东西，你做出库？**
- 货压坏了，账上只能"卖掉"——**既没有供应商买单，也没有损耗记录。**
- 已执行的单据录错了，只能**手工改数据库**——我听了都想报警。
- 谁登录、谁审核、谁改了库存——**无据可查。**

这些问题任何一个，都比"Token 是不是内存"更先杀死这个系统。因为朋友干的是贸易，**他对账时需要的不是并发正确性，而是每一笔库存变动都能在账上找到一张"为什么"的凭据。**

所以我把优先级定了下来，八项落地 + 分页覆盖三处列表：

> **G1 操作日志 + G6 单据号落库 + G2 退货 + G3 报损报溢 + G4 反审红冲 + G7 库龄 + G8 收发存汇总 + G11 盘点过滤**，外加分页（G12）做到日志、库存、物品三处。

G5（数据权限）和 G9（批次效期）涉及面大，明确放第二批；G12 里单据/调拨/盘点等列表、G14（BOM 组装拆卸）、G20（Flyway）价值大但工作量大，留 TODO。

---

## 二、先补"账的闭环"：凭证类功能

### 2.1 报损报溢单：给"账不平"一个合法的出口

报损报溢的本质是：**实物和账面的差额，终于有单据可写了。**

后端新增了 `AdjustmentOrder`，走和出入库单一样的三段式状态机（`DRAFT → APPROVED → COMPLETED`），单号前缀 `BSS`（报损）/ `BSY`（报溢）。执行时按方向调 `InventoryService.adjust`，自动生成 `loss_out` / `gain_in` 流水：

```java
@Transactional public Map<String,Object> complete(Long id){
  AdjustmentOrder a=adjustmentForUpdate(id);
  if(!"APPROVED".equals(a.getStatus())) throw new BusinessException("只有已审核报损报溢单可执行");
  for(AdjustmentLine l:a.getLines()){
    if("LOSS".equals(a.getAction()))
      inventory.adjust(a.getAdjustmentNo(), l.getItem().getCode(), a.getWarehouse().getId(),
        l.getLocationCode(), l.getBatchNo(),
        inventoryCurrent(a,l).subtract(l.getQuantity()), a.getReason());   // 报损：减库存
    else
      inventory.adjust(a.getAdjustmentNo(), l.getItem().getCode(), a.getWarehouse().getId(),
        l.getLocationCode(), l.getBatchNo(),
        inventoryCurrent(a,l).add(l.getQuantity()), a.getReason());        // 报溢：加库存
  }
  a.setStatus("COMPLETED"); return view(a);
}
```

**注意这里复用的是 Day 1 的 `InventoryService.adjust`**——加锁、流水、均价更新全部继承，报损报溢单只是在它前面加了一道审核门。这就是 Day 3 讲过的"动作与流程分离"设计，第三次吃到红利。

### 2.2 退货单：客户退货，终于走"退货入库"了

`StockDocument` 追加了 `RETURN_IN` / `RETURN_OUT` 两种类型，单号前缀 `THI` / `THO`：

```java
String prefix = switch(r.type()){
  case "IN" -> "RKD";
  case "OUT" -> "CKD";
  case "RETURN_IN" -> "THI";     // 客户退货 → 退货入库
  case "RETURN_OUT" -> "THO";    // 退给供应商 → 退货出库
  default -> throw new BusinessException("不支持的单据类型");
};
```

细节在于往来单位校验也一起改了：**RETURN_IN 强制选客户、RETURN_OUT 强制选供应商**（或 `BOTH`），不能再像以前那样把"退货"硬塞成"入库单选供应商"。

### 2.3 反审与红冲：录错了，不用改数据库了

这是审计里最让我后怕的一个。以前执行完的单据发现录错，唯一的办法是**手工改库**。现在给了两个标准动作，都仅 ADMIN 可操作：

**反审（`/documents/{id}/uncomplete`）**——`COMPLETED → APPROVED`，并自动生成反向流水冲销库存影响：

```java
private void reverseInventory(StockDocument d){
  String t=d.getType();
  for(StockDocumentLine l:d.getLines()){
    boolean wasIn=...; String ref="REV-"+d.getDocumentNo();
    if(wasIn) inventory.stockOut(..., ref, TransactionType.REVERSE);  // 原来是入库 → 反审做出库
    else      inventory.stockIn (..., ref, TransactionType.REVERSE);  // 原来是出库 → 反审做入库
  }
}
```

**红冲（`/documents/{id}/reverse`）**——原单保留不动，生成一张类型互反、单号加 `.V` 后缀的新单据，直接置为 `APPROVED`：

```java
String t=switch(src.getType()){ case "IN"->"OUT"; case "OUT"->"IN";
  case "RETURN_IN"->"RETURN_OUT"; case "RETURN_OUT"->"RETURN_IN"; default->... };
String baseNo=numbers.next(switch(t){ ... });
String revNo=baseNo+".V";
```

**红冲而不是删除**，是财务系统的铁律：错单要留痕，冲销要有迹。删掉一张错单，审计时谁也说不清那笔库存去哪了。

### 2.4 操作日志：AOP 一刀切，REQUIRES_NEW 保证不丢

`OperationLogAspect` 用一个 `@Around` 切面包住所有 Controller：

```java
@Around("execution(* com.wms.controller..*(..)) && !within(com.wms.controller.OperationLogController)")
public Object logController(ProceedingJoinPoint pjp) throws Throwable {
  ... // 解析 token 拿到 username，proceed() 后按 SUCCESS/ERROR 落一条日志
}
```

两个细节非常关键：

- **`OperationLogService.record` 用了 `REQUIRES_NEW`**——嵌套新事务写日志。这样就算业务事务回滚了，日志也还在。审计日志跟着业务一起回滚，等于白记。
- **系统没有手动写日志的接口**。日志只能由 AOP 切面产生，谁都没法"造"一条日志——这从根上断了伪造审计记录的可能。

> **洞察**：仓库系统的"账"，不止是那一张张流水，更是一张张**凭证**。流水回答"库存变多少"，凭证回答"为什么变"。Day 1-3 我们把流水表设计得再扎实，没有凭证类单据和审计日志，账也是死的。

---

## 三、P0 逐个啃：Day 4 报告的兑现

账的闭环补完后，才轮到 Day 4 报告里那几项 P0。出乎意料的是：**没有一项需要大改。**

### 3.1 单据号：从 `AtomicLong` 到落库 + 行锁（P0-02）

Day 4 报告的原文是"单号生成在重启和多实例下不安全，重启一次可能重复单号"。修法是加一张 `document_sequences` 表，两段式取号：

```java
@Transactional
public String next(String prefix) {
  // ① 原子插入序号行：并发首次取号也只有一个成功
  sequences.insertIfAbsent(prefix);
  // ② 行锁取号：SELECT ... FOR UPDATE，同一前缀串行递增
  DocumentSequence seq = sequences.findForUpdate(prefix).orElseThrow(...);
  seq.setCounter(seq.getCounter() + 1);
  sequences.save(seq);
  return prefix + "-" + LocalDate.now().format(BASIC_ISO_DATE) + "-" + String.format("%04d", seq.getCounter());
}
```

Repository 里两个注解是关键：

```java
@Modifying
@Query(value = "insert into document_sequences (prefix, counter, ...) "
        + "values (:prefix, 0, now(), now()) on duplicate key update prefix = prefix", nativeQuery = true)
void insertIfAbsent(@Param("prefix") String prefix);       // 原子插入，谁先抢到算谁的

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select ds from DocumentSequence ds where ds.prefix = :prefix")
Optional<DocumentSequence> findForUpdate(@Param("prefix") String prefix);  // 行锁取号
```

`insertIfAbsent` 用 `on duplicate key update prefix = prefix` 这个"空操作"保证并发首次取号时只有一个线程建行，配合唯一约束，在 H2（MODE=MySQL）和 MySQL 上行为一致；`findForUpdate` 加 `PESSIMISTIC_WRITE` 让同一前缀的取号串行化。**现在重启不重号，将来多实例也不会撞号。**

### 3.2 三把行锁：把并发竞态一个个堵上

审计和评审把"哪里有并发竞争"全挖了出来，最后收敛成三把 `PESSIMISTIC_WRITE` 行锁：

| 锁点 | 防的是什么 | 代码 |
|------|-----------|------|
| 序号行 | 并发取单号重号 | `DocumentSequenceRepository.findForUpdate` |
| 库存行 | 并发出入库超卖 / 成本算错 | `InventoryRepository.findForUpdate` |
| 单据行 | 同一张单据被并发执行两次 | `StockDocumentRepository.findForUpdateById` |

第三把锁特别值得讲。Day 4 报告里的 P0-03 说"执行入库单时，两个请求并发可能都读到 `APPROVED`，库存被扣两次"。修法就是给单据读加锁——`findForUpdateById` 用 `@Lock(PESSIMISTIC_WRITE)`，两个并发 `complete` 会串行：第二个进来时状态已经是 `COMPLETED`，直接抛"只有已审核单据可执行"。

还有一个隐蔽的竞态，是**库位的原子插入**：

```java
@Modifying
@Query(value = "insert into locations (warehouse_id, code, status, ...) "
        + "values (:warehouseId, :code, 1, now(), now()) on duplicate key update id = id", nativeQuery = true)
void insertIfAbsent(@Param("warehouseId") Long warehouseId, @Param("code") String code);
```

以前两个扫码枪同时扫一个新的库位号，会各自 `select` 不到 → 各自 `insert` → 一个撞唯一约束 500。现在先 `insertIfAbsent` 兜底再查，**谁建都行，反正只有一行。**

> **P0 的修复成本，比想象中低得多；不修的代价，比想象中高得多。** 三把 `PESSIMISTIC_WRITE` 行锁 + 两处 `on duplicate key` 原子插入，没有一个需要换架构。

### 3.3 演示数据保护：生产环境不许出现 `admin/admin123`（P0-01）

Day 4 报告的 P0-01 是"默认 H2 内存库 + 重启丢数据"。完全改掉它要靠显式切 profile，但有一半是代码能兜的：**演示数据必须在生产环境闭嘴。**

`DemoDataConfig` 现在先判断环境再播种：

```java
String[] profiles=env.getActiveProfiles();
boolean isDev=profiles.length==0 || Arrays.stream(profiles).anyMatch(p->p.equals("dev")||p.equals("test"));
if(!isDev) return;   // 生产环境不注入演示账号/物品，避免默认口令进入真实库
```

同时 `SecurityConfig` 里 H2 Console 只在 dev 下放行。**以后就算有人忘了设 `SPRING_PROFILES_ACTIVE=prod` 直接启动，也不会把 `admin/admin123` 种进生产库。** 默认口令是审计里最容易被一票否决的项，必须从源头掐死。

### 3.4 登录限速：10 分钟 5 次失败，直接 429

评审又发现一个没写进 Day 4 报告的新问题：**登录接口没有防爆破。** 修法是 `LoginRateLimiter`，纯内存计数：

```java
public static final int MAX_ATTEMPTS = 5;
public static final Duration WINDOW = Duration.ofMinutes(10);

public boolean isBlocked(String key) { ... }     // 窗口内失败 ≥ 5 次 → 锁
public void recordFailure(String key) { ... }    // 每次失败 +1，窗口滑动
public void reset(String key) { ... }            // 成功登录/绑定即清零
```

限速键用的是 `IP|用户名`：

```java
private String rateKey(HttpServletRequest http,String username){
  String ip=...; // 优先取 X-Forwarded-For
  return ip+"|"+username;
}
```

这个组合有个小心机：**带上用户名，恶意攻击者就不能靠刷同一 IP 把别人的账号锁死。** 触顶后 `GlobalExceptionHandler` 返回 HTTP `429`，而不是 400——语义更准确，前端也方便区分"参数错了"和"被限流了"。

### 3.5 Excel 导入事务化：要么全进，要么全不进

Day 3 的导入是逐行 `save`，没包事务。后果是：**前 100 行进了，第 101 行格式错了——你说用户是重传整个文件，还是手动补那 100 行？**

这次给导入加了 `@Transactional` 整单回滚，并且把报错精确到行号：

```java
@PostMapping(value="/items/import",consumes=MULTIPART_FORM_DATA_VALUE)
@Transactional public ApiResponse<Map<String,Object>> importItems(...) {
  ... // 逐行解析，任一非法即抛异常，整单回滚
}

private BigDecimal decimal(String value,int rowNum){
  ...
  catch(NumberFormatException e){
    throw new BusinessException("第 "+(rowNum+1)+" 行数字格式不正确: \""+value+"\"");
  }
}
```

**"部分成功"是最恶心的数据状态**——比全失败难处理十倍。宁可让用户改好第 101 行重新传一次，也不留下一半脏数据。这是批量操作的铁律，Day 3 的"幂等"和这次"事务"合起来才完整。

### 3.6 报表口径修正：数字要经得起对账

报表之前是"能出数"，这次审计发现口径有多处会误导。改了三个最要命的：

**① 利润只统计销售出库，其它一律不算。**

```java
for (InventoryTransaction t : txns) {
  // 利润只应来自销售类出库：采购入库(amount>0)/调拨/报损报溢均无利润，混入会污染趋势
  if (!"out".equals(t.getTransactionType())) continue;
  ...
}
```

以前把调拨、报损的金额混进利润趋势，老板看到"这个月利润暴涨"其实是调了批货——**数字错了比没数字更危险。**

**② 库龄按 FIFO 分层计算，不再"拍脑袋"。**

`/reports/inventory-age` 用入库流水按时间排序，逐层消耗当前库存，剩下的库存来自哪一层、按量加权平均算天数：

```java
private long fifoAgeDays(Inventory inv, Map<...> inboundByStock, LocalDate today) {
  BigDecimal remaining = inv.getQuantity();
  for (InventoryTransaction t : inboundByStock.getOrDefault(key, List.of())) {
    if (remaining.signum() <= 0) break;
    BigDecimal q = t.getQuantity().min(remaining);      // 最早的一层先被消耗掉
    long days = ChronoUnit.DAYS.between(t.getTransactionAt().toLocalDate(), today);
    weightedDays = weightedDays.add(BigDecimal.valueOf(days).multiply(q));
    remaining = remaining.subtract(q);
  }
  ...
}
```

然后按 `0-30 / 30-60 / 60-90 / >90` 分桶输出。**呆滞料终于能一眼看出来了。**

**③ 今日出入库改全口径。**

仪表盘今天的入库/出库金额，从只算 `in` / `out` 改成涵盖退货、报损报溢、调拨、反审的完整集合——不然你今天退了一批货，账上"出库金额"却是 0，对不上。

---

## 四、权限：从"角色硬编码"到"权限矩阵"

### 4.1 `ensureAdmin()` 的尽头

Day 1-3 的权限是"两个角色 + `if (role == ADMIN)` 强判"。它能跑，但有个天花板：**新增一个"采购员"角色，就得改代码重新编译。** 而且 `ensureAdmin()` 散落在各个 Service 里，谁漏加一行，谁就裸奔。

这次把权限做成了"矩阵"：`Permissions.java` 定义权限常量，`RolePermissions.java` 定义角色 → 权限集合的映射。

```java
public final class RolePermissions {
  private static final Set<String> WAREHOUSE = Set.of(
    INVENTORY_READ, INVENTORY_WRITE,
    DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_EXECUTE,     // 没有 DOCUMENT_REVIEW
    TRANSFER_READ, TRANSFER_WRITE, TRANSFER_EXECUTE,     // 没有 TRANSFER_REVIEW
    STOCKTAKE_READ, STOCKTAKE_WRITE, STOCKTAKE_EXECUTE,
    ADJUSTMENT_READ, ADJUSTMENT_WRITE, ADJUSTMENT_EXECUTE,
    PURCHASE_READ, PURCHASE_WRITE,
    ITEM_READ, ITEM_WRITE, PARTNER_READ, PARTNER_WRITE,
    REPORT_VIEW, QRCODE_READ, EXCEL_READ, EXCEL_WRITE, OCR_USE, LOCATION_READ);
  private static final Map<String, Set<String>> MATRIX = Map.of(
    "ADMIN", ALL, "WAREHOUSE", WAREHOUSE);
}
```

**看这张表就能读懂角色边界**：操作员能建单、能执行，但审核（`*_REVIEW`）一个都不给；ADMIN 全量。权限随 Token 下发给前端，菜单级显隐也是它。

### 4.2 双保险：Controller 一道闸，Service 一道闸

权限校验做了两层，而不是只放一层：

- **Controller 层**用 Spring 的 `@PreAuthorize`，拦截非法请求：
  ```java
  @PreAuthorize("hasAuthority('report:view')")
  public ApiResponse<...> dashboard() { ... }
  ```
- **Service 层**用 `SecurityUtils.require(permission)`，防"绕道"：
  ```java
  public static void require(String permission) {
    if (!hasPermission(permission))
      throw new AccessDeniedException("无权限执行该操作（需要权限：" + permission + "）");
  }
  ```

**为什么双保险？** 因为 Service 方法可能被多个 Controller 复用，也可能被定时任务、内部调用直接触发。只在 Controller 加一层，等于"门卫很严、侧门敞开"；在真正动数据库的地方再校验一次，才是兜底。代价是每个方法多一行，值。

### 4.3 顺带收紧：扫码出入库只给 ADMIN

借这次整改，把 `stock/in/scan` 和 `stock/out/scan` 也收紧了——它俩直写库存、无审核，是"权限最松"的接口：

```java
@PostMapping("/in/scan")
@PreAuthorize("hasAuthority('inventory:write') and hasRole('ADMIN')")
```

WAREHOUSE 操作员从此只能走单据流程和盘点录入，扫码直写被关掉。**配合权限矩阵，这一次还顺带加了采购申请模块（`PurchaseRequest`），并且新增、停用用户、查看日志这些敏感操作全部显式挂权限。**

---

## 五、三端：前端换 Vue 3，外加一个微信小程序

整改期间业务没停，前端还经历了两件大事：

**① Web 前端从 React 换成 Vue 3 + Ant Design Vue 4 + Vite 6。** 理由很务实：Vue 单文件组件在中小项目里维护成本更低，`v-model` 处理表单比 React 的受控组件省一半样板代码。功能原样平移，`src/pages/*Page.vue` 每个页面对应一套 API。

**② 新增微信小程序端（uni-app + Vue 3 + Pinia）。** 仓管员扫码干活，手机上比电脑方便。登录走微信流程：

```
uni.login() → POST /auth/wx-login {code}
  ├─ 已绑定  → 返回 token（与账号密码登录同壳）
  └─ 未绑定  → 返回 {needBind:true, openid} → POST /auth/wx-bind {openid,username,password}
```

小程序端的 `login.vue` 里就是"微信授权登录 → 没绑定就弹账号密码绑定 → 绑定即登录"三步。后端 `WechatService` 有个贴心的 mock：

```java
if (mock) {
  String[] profiles = environment.getActiveProfiles();
  boolean isDev = profiles.length == 0 || anyMatch(dev, test);
  if (!isDev) throw new BusinessException("生产环境禁止启用 wechat.mock ...");
  return code;   // 开发时 code 直接当 openid，随便传 test-openid-123 就能登录
}
```

**开发联调零成本：传任意 code 当 openid 用；生产误开 mock 直接被拒绝。** 三端共用同一套 `/api/v1`，后端一行没改就多了个终端。

---

## 六、测试：从 2 个到 10 个

Day 4 我们说过测试的取舍。这周测试数变了，但取舍原则没变：

| 测试类 | 数量 | 覆盖 |
|--------|:--:|------|
| `InventoryCostCalculatorTest` | 2 | 移动加权平均、调拨成本继承（纯算法） |
| `AdjustmentIntegrationTest` | 4 | 报损减库存、退货入库、反审冲销、非执行态反审报错 |
| `DocumentNumberServiceTest` | 1 | 序号落库递增、格式正确 |
| `AuthControllerWxTest` | 3 | 微信登录全流程、预绑定直登、停用账号拒绝绑定 |
| **合计** | **10** | **4 个测试类全部通过** |

关键变化是：**从"纯算法单测"升级到了 Spring Boot 集成测试。** `AdjustmentIntegrationTest` 直接注入真实 Service 跑完整业务链路，靠 `Harness` 往 `SecurityContextHolder` 里塞一个带全部权限的 ADMIN：

```java
public static <T> T asAdmin(Supplier<T> action) {
  TokenService.Principal principal = new TokenService.Principal("admin","ADMIN","管理员",RolePermissions.forRole("ADMIN"));
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, ...));
  try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
}
```

**为什么需要它？** 因为这次权限校验下沉到了 Service 层（`SecurityUtils.require`），集成测试绕不开 SecurityContext，那就干脆造一个带权限的上下文再跑。这也是"双保险"设计的一个红利——**测试可以精确地扮演任意角色。**

测试数没奔着 50 去，还是那个原则：**单测覆盖"坏起来最痛 + 回归最频繁"的，其余交给 Day 4 那套 Python 回归**（回归脚本也顺手更新了，对齐了扫码仅 ADMIN 的新权限）。

---

## 七、诚实的"还没做"清单

这周修掉了 Day 4 报告的几项 P0，但也要说清楚**哪些还躺着**，免得读者以为系统已经生产就绪：

| 事项 | 现状 | 影响 |
|------|------|------|
| Token 存储 | 仍是进程内 `ConcurrentHashMap`，12h TTL | **单实例够用，多实例部署必须换 Redis** |
| 数据库迁移 | 仍是 `ddl-auto: update` | 无法版本化、无法回滚，正式库建议上 Flyway（G20） |
| 真实 OCR | 仍是 mock（`source:"mock"`） | 不能依赖它做真实识别 |
| 数据权限 G5 | 未做 | WAREHOUSE 仍能看到所有仓库数据 |
| 批次效期 G9 | 未做 | 带保质期的物料无法按效期先出（FEFO） |
| 调拨在途 / BOM / 应收应付 | 未做 | 业务范围未达，明确排期外 |

**从 45 分到 60 分，不是靠换了任何一项技术，而是靠把"账会错"的地方一个个堵上。** 60 分的系统和 45 分的系统，差的不是 Redis、不是 Flyway，而是**"用户操作到账本之间，还有没有漏洞"**。

---

## 八、Day 5 的几点总结

**1. 报告不是终点，是整改的起点。**

Day 4 那份 45 分的报告，价值不在那个数字，而在它逼我回答了"然后呢"。这一周的所有改动，几乎都是 Day 4 列出的问题清单驱动的——**先诚实承认不足，才有机会去补。**

**2. 凭证闭环，是"账"的地基。**

流水回答"库存变多少"，凭证回答"为什么变"。报损报溢、退货、反审红冲、操作日志——这些不是锦上添花的功能，是**对账时救命的东西**。没有它们，流水再准，账也是死账。

**3. P0 修复大多不贵：加个判断、加把锁、加张表。**

单据号落库 = 一张表 + 一个 `on duplicate key`；并发竞态 = 三处 `PESSIMISTIC_WRITE` 注解；演示数据保护 = 一个 `if(isDev)`；登录限速 = 一个内存计数器。**真正贵的不是修复，是发现。** 而发现靠的是审计和评审，不是运气。

**4. 权限要用"矩阵"而不是"硬编码角色"。**

角色会越来越多，代码不能跟着改。`Permissions` + `RolePermissions` + `@PreAuthorize` + Service 层 `require` 双保险，加角色只改一张表（静态矩阵），漏掉校验也只影响一个方法而不是整个类。

**5. 生产化的本质，是"知道没做什么" + "优先级正确"。**

七节那张"还没做"清单，比任何一行代码都能说明这个项目的状态。**系统能不能上线，不取决于"都有"，而取决于"知道哪些没有"——以及先做哪几个。**

---

> **项目地址**：GitHub（私信获取）
> **技术栈**：Spring Boot 3 + JPA + Vue 3 / Ant Design Vue + uni-app 小程序 + MySQL / H2 + AOP + Python 回归
> **本篇功能**：功能差距审计（AUDIT_REPORT.md）、报损报溢 / 退货 / 反审红冲 / 操作日志、单据号落库与并发行锁、演示数据生产保护、登录限速、Excel 导入事务化、报表口径修正、RBAC 权限矩阵、前端 Vue 3 重构、微信小程序端

---

## 📮 预告：Day 6 讲什么？

这一周把"账"和"权限"补齐了，但 Day 4 报告里那几样大件还躺在那儿：

- **Token 还在进程内存里**——上两台服务器做负载均衡，登录状态就会互相"失忆"。Redis 怎么接？退出登录怎么做到"踢掉所有端"？
- **还是 `ddl-auto: update`**——正式库改了表结构没法回滚，Flyway 迁移脚本怎么写、怎么在部署时自动执行？
- **真实 OCR 还是 mock**——扫码枪扫的是二维码，但"拍照识别单据"这个需求，真实 OCR 接入要过哪些坑？
- **三端跑起来了，小程序要真机预览、要填真 appid/secret**——上线前的最后一公里。

Day 6 我们处理"多实例和真上线"这一摊：**把内存 Token 换掉、把建表脚本管起来、让系统敢跑两台。**

---

*如果这五篇对你有帮助，欢迎点赞、转发、在看。评论区聊聊：你做过的项目里，"账的闭环"是补在出事之前，还是出事之后？*
