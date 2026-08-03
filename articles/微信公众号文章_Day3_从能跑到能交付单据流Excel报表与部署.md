# 【Day 3】从「能跑」到「能交付」：扫码之外，为什么我多写了一套单据流？

> Day 1 搞定成本核算，Day 2 搞定调拨、盘点、预警和权限。这两篇聊的是「账」和「权」。今天这篇，我想聊聊把它们串起来的那条主线：**单据**，以及把它从一台电脑搬到一台服务器的全过程。

> 📌 本文是「从零搭建仓库进销存系统」系列的 **Day 3**。如果你刚打开这个系列，建议先翻 Day 1 / Day 2，再来看这篇会顺很多。

---

## 一、朋友又来了，这次的需求很「甲方」

Demo 上线那天，朋友眉头又皱起来了：

- "我们仓管员文化程度不高，**没审核就敢出库**，这事得有人签字。"
- "我老婆嫌每次都靠 Excel 管物品档案，**能不能把表格直接倒进系统？**"
- "月底我要看一堆数，**别让我自己点进报表一项项算**，最好打开就有。"
- "你那台电脑不能 7×24 小时开着吧？**放到我自己的服务器上行不行？**"

四个问题，对应到代码里其实就是四块：

1. **单据流**（草稿/审核/执行/取消）
2. **Excel 批量导入导出**
3. **仪表盘和报表**
4. **Docker Compose 部署**

前两个我没在 Day 1/2 写过，但其实是这个项目「能不能交付」的分水岭。今天一篇讲完。

---

## 二、单据 vs 扫码：同一个动作，为什么要做两套？

### 2.1 先回到现实

Day 1 演示的时候，我用的是「扫码入库 / 扫码出库」——一个 HTTP 接口，一把扫码枪，一条流水就进去了。

朋友一上手就说不对：

> "我不可能让仓管员自己扫码就出库啊。万一他扫错了呢？万一货根本不该出呢？**总得有人审一下。**"

这就是现实和 Demo 的差距：

| 场景 | 扫码接口 | 单据流 |
|------|----------|--------|
| 小公司、夫妻店、对账松 | ✅ 够用 | 太重 |
| 有仓管员、要分权、要留痕 | ⚠️ 太松 | ✅ 必备 |

所以同一个动作，我做了两套入口：

```
POST /api/v1/stock/in/scan        # 扫码直接入库（无审核）
POST /api/v1/documents            # 创建入库单（草稿）
POST /api/v1/documents/{id}/review    # 管理员审核
POST /api/v1/documents/{id}/complete  # 执行入库（这才动库存）
```

**核心区别只有一个：扫码直接写库存；单据要先过审核，审核通过了才动库存。**

### 2.2 单据的状态机

单据流转其实就是几个状态来回切。我画了个图：

```
        ┌─────────┐
        │  DRAFT  │  草稿（操作员录入）
        └────┬────┘
             │ 管理员 /review  APPROVE
             ▼
        ┌─────────┐
        │APPROVED │  已审核（待执行）
        └────┬────┘
             │ /complete
             ▼
        ┌─────────┐
        │COMPLETED│  已执行（已写库存 + 已写流水）

   DRAFT ──/review REJECT──▶ REJECTED
   DRAFT ──/cancel  ────────▶ CANCELLED
   APPROVED ──/cancel ──────▶ CANCELLED
   COMPLETED ──/cancel       ✘ 禁止
```

代码里就是这么干的（`DocumentService.java`）：

```java
@Transactional
public Map<String, Object> completeDocument(Long id) {
    StockDocument d = document(id);
    if (!"APPROVED".equals(d.getStatus()))
        throw new BusinessException("只有已审核单据可执行");
    for (StockDocumentLine l : d.getLines()) {
        if ("IN".equals(d.getType()))
            inventory.stockIn(..., d.getDocumentNo(), TransactionType.IN);
        else
            inventory.stockOut(..., d.getDocumentNo(), TransactionType.OUT);
    }
    d.setStatus("COMPLETED");
    return documentView(d);
}
```

**注意 execute 这一段**：它最终调的还是 Day 1 那套 `InventoryService.stockIn / stockOut` —— **库存怎么动、加锁怎么加、流水怎么写，Day 1 已经处理完了**，单据流只是在它前面加了两道门。

> **设计上的复利**：扫码和单据流在「写库存」这一步共用同一套服务。这意味着**单据流的成本算法、锁、流水全部继承 Day 1 的成果，零额外维护成本**。这就是分层带来的好处。

### 2.3 往来单位的小心机

入库单要选供应商，出库单要选客户。这个看起来废话的校验，后端是这么强制的：

```java
String expected = "IN".equals(documentType) ? "SUPPLIER" : "CUSTOMER";
if (!expected.equals(partner.getType()) && !"BOTH".equals(partner.getType()))
    throw new BusinessException("入库单只能选择供应商");
```

`BOTH` 这种「既是供应商也是客户」的双向往来单位，在贸易公司里其实非常常见——同一厂子既进货又退货，所以专门留了一个口子。**业务上的「废话」落到代码里，往往就是这种一个分支的判断。**

---

## 三、Excel 导入导出：操作员的最爱

### 3.1 为什么一定要做这个？

朋友第二次来吐槽的时候带了一摞打印的 Excel：

> "我这有 200 多个型号要录进去，让我一条条点新增我得录到明年。"

我看了一眼他那个 Excel，列还挺规整的——物品编码、名称、分类、单位、规格、安全库存、备注。**导出模板，填完再导入**，是这类系统最自然的路径。

### 3.2 导出

后端用的是 Apache POI：

```java
String[] heads = {"物品编码","物品名称","分类","单位",
                  "规格型号","安全库存","最小库存","最大库存","备注"};
// ... 写表头、写数据行、autoSizeColumn
return ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename*=UTF-8''物品档案.xlsx")
    .contentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    .body(out.toByteArray());
```

### 3.3 导入（这才是有意思的部分）

导入的代码看着是「读 Excel → 存数据库」，但里面藏了三个细节：

**① 第一个坑：编码不能为空、不能重复**

```java
String code = text(row, 0), name = text(row, 1);
if (code.isBlank() || name.isBlank()) { skipped++; continue; }
```

空行直接跳过，返回值告诉你「创建 X 条 / 更新 Y 条 / 跳过 Z 条」。**不要在导入里抛异常让用户从第 3 行重传**，这是对运营人员最大的友好。

**② 第二个坑：分类不存在时自动创建**

```java
item.setCategory(categoryName.isBlank() ? null
    : categories.findByName(categoryName)
        .orElseGet(() -> categories.save(new Category(categoryName))));
```

不然 200 行导入，10 个未登记的分类就会让 50 行失败。**让数据"柔顺地"流入系统**，比"严格地"拒绝它更友好。

**③ 第三个坑：以物品编码为唯一键，存在的更新、不存在的新建**

```java
Item item = items.findByCode(code).orElseGet(Item::new);
boolean exists = item.getId() != null;
...
if (exists) updated++; else created++;
```

这样用户每次导入都是「增量同步」，不用担心覆盖已有数据。**幂等性，是批量操作的灵魂。**

---

## 四、仪表盘：一张图告诉你今天发生了什么

### 4.1 数据哪来的？

仪表盘的指标看起来很多：

```json
{
  "stockItemCount": 3,        // 有库存的物品种数
  "totalQuantity": 160,       // 库存总数量
  "totalAmount": 4180,        // 库存总金额
  "todayInboundAmount": 0,    // 今日入库金额
  "todayOutboundAmount": 0,   // 今日出库金额
  "alertCount": 0,            // 预警数
  "alerts": [...],            // 预警明细
  "recentTransactions": [...],// 最近流水
  "categoryDistribution": [...], // 分类分布
  "valueByCategory": [...],   // 分类金额
  "dailyTrend": [...],        // 14 天趋势
  "monthlyProfit": [...],     // 6 个月利润
  "topItemsByValue": [...]    // TOP 8 价值物品
}
```

但你仔细看，所有指标的源头只有两张表：**`inventory`（当前快照）+ `inventory_transactions`（流水）**。

### 4.2 月度利润这一段很典型

```java
// 近6个月：每月聚合 [成本金额, 销售金额, 利润]
Map<String, BigDecimal[]> monthly = new LinkedHashMap<>();
for (int i = 0; i < 6; i++) {
    String key = start.plusMonths(i).format(fmt);
    monthly.put(key, new BigDecimal[]{ZERO, ZERO, ZERO});
}
for (InventoryTransaction t : txns) {
    String key = t.getTransactionAt().format(fmt);
    monthly.get(key)[0].add(t.getTotalCostAmount());   // 成本
    monthly.get(key)[1].add(t.getSaleAmount());        // 销售
    monthly.get(key)[2].add(t.getProfit());             // 利润
}
```

我**没存任何"月报表"**——它就是按时间窗口对流水做一次 sum。**前期把流水表设计扎实，后期的分析功能几乎是白送的。** Day 2 那句话今天又应验了一次。

### 4.3 前端怎么画的？

前端用了 `recharts`：

```jsx
<ResponsiveContainer>
  <LineChart data={dailyTrend}>
    <CartesianGrid strokeDasharray="3 3" />
    <XAxis dataKey="date" />
    <YAxis />
    <Tooltip />
    <Line type="monotone" dataKey="inbound" stroke="#1677ff" />
    <Line type="monotone" dataKey="outbound" stroke="#f5222d" />
  </LineChart>
</ResponsiveContainer>
```

蓝线入库、红线出库、底下再叠一个 6 个月的利润柱状图——老板打开浏览器就能一眼看明白今天和这个月的状态。

---

## 五、部署：怎么从「我电脑能跑」变成「服务器能跑」

### 5.1 启动方式一：开发期，H2 内存库

```bash
cd wms-server
mvn spring-boot:run
```

`application.yml` 里默认就是 H2 内存库，**无需任何环境变量**，第一次启动自动写入 demo 数据（`DemoDataConfig`）：

- `admin / admin123`（管理员）
- `operator / operator123`（操作员）
- 3 个物品 + 100/40/20 件初始库存

这是开发体验的关键：**clone 下来就能跑**，不用先装 MySQL、初始化脚本、改连接串。

### 5.2 启动方式二：连真实 MySQL

切到 MySQL 只用四个环境变量：

```bash
export DB_URL='jdbc:mysql://localhost:3306/wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME=root
export DB_PASSWORD=xxx
export DB_DRIVER=com.mysql.cj.jdbc.Driver
mvn spring-boot:run
```

代码里读得很优雅：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:h2:mem:wms;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:}
    driver-class-name: ${DB_DRIVER:org.h2.Driver}
```

**默认值是 H2，给真实环境留了 4 个 env 覆盖的口子。** 这种「开发用最小依赖、运行再切真实 DB」的模式，省掉了新人入职 80% 的卡壳时间。

### 5.3 启动方式三：Docker Compose，一键拉起

仓库根目录的 `docker-compose.yml` 长这样：

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-wms_password}
    volumes: ["mysql-data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      retries: 10
  wms-server:
    build: ./wms-server
    environment:
      DB_URL: jdbc:mysql://mysql:3306/wms?...
      DB_PASSWORD: ${MYSQL_ROOT_PASSWORD:-wms_password}
    depends_on:
      mysql: { condition: service_healthy }
  wms-web:
    build: ./wms-web
    ports: ["3000:80"]
    depends_on: [wms-server]
```

执行 `docker compose up --build` 就能拉起三个服务，端口分配是：

| 服务 | 端口 | 角色 |
|------|------|------|
| MySQL | 3306 | 数据 |
| wms-server | 8088 | API |
| wms-web | 3000 | 前端（nginx 反代 /api/v1 到 8088） |

### 5.4 前端的反代是另一个细节

`wms-web/nginx.conf` 是个只有 4 行的极简配置，但承担了前端最关键的两个职责：

```nginx
location /api/v1/ {
    proxy_pass http://wms-server:8088/api/v1/;
    ...
}
location / {
    try_files $uri $uri/ /index.html;   # SPA 路由兜底
}
```

**第二行非常重要**：`try_files` 让所有找不到的路径都回到 `index.html`。这是 React/Vue 这种 SPA 的标配，否则刷新页面会 404。**别在 SPA 项目里把这行忘了，不然每次刷新都报 404。**

### 5.5 注意！README 写的不一定都对

> ⚠️ README 里画了一大堆 Redis、MinIO、Nginx、SSL，**但实际仓库里一个都没有**。`docker-compose.yml` 只有 MySQL + 后端 + 前端三个服务，`pom.xml` 也没有 Redis / MinIO 的依赖。

我后来想明白了：**README 是规划，代码是落地**。两者不一致时，以代码为准。所以这次的 compose 就老老实实三个服务，Redis 想加后面再说。

---

## 六、Day 3 的几点收获

做完这一轮，最想说三件事：

**1. 把"流程"和"动作"分开，是单据流的核心。**

扫码 = 动作，单据 = 流程。两者在「写库存」这一步合流，复用同一套服务。这种「同一动作两套入口」的设计，既能满足夫妻店的轻量场景，也能满足多人分权的企业场景，**一套代码，两种业务模型**。

**2. 数据的复利，比算法的复利更可怕。**

Excel 导入、仪表盘、月度利润、异常检测——这一篇涉及的几乎所有功能，**底层都只读了两张表**（inventory + inventory_transactions）。Day 1 那张流水表设计对了，后面的功能都是「白送」的。**好的数据模型，会自己长出功能来。**

**3. 「能跑」到「能交付」，差的不是技术，是细节。**

Docker Compose 的 `healthcheck`、`try_files` 的 SPA 兜底、Excel 导入的幂等性、`BOTH` 往来单位的兼容位……这些都不是什么高深技术，但少了任何一个，运营都会在某个深夜打电话给你。

---

> **项目地址**：GitHub（私信获取）
> **技术栈**：Spring Boot 3 + JPA + React + Ant Design + MySQL + ZXing + POI + Docker
> **本篇功能**：入库/出库单据流（草稿/审核/执行/取消）、Excel 批量导入导出、仪表盘报表、Docker Compose 部署

---

## 📮 预告：Day 4 讲什么？

到今天为止，「账」和「权」和「流程」和「部署」都聊过了。但还有一类问题没正面回答过：

- **测试怎么写？** 整个仓库只有一个 `InventoryCostCalculatorTest`，那 Service 层的并发、状态机、异常分支怎么覆盖？
- **回归怎么做？** 每次手动跑一遍三十个接口？仓库里的 `run_api_regression.py` 又是什么？
- **怎么证明系统是稳的？** 一份给老板看的「系统验收报告」应该长什么样？

Day 4 我们就把这一摊子事讲清楚：**一个没有任何自动化测试的小项目，怎么靠一点点 Python 脚本 + 一份诚实的报告，活过交付验收。**

---

*如果这三篇对你有帮助，欢迎点赞、转发、在看。评论区聊聊：你做过的项目里，「从能跑到能交付」这一步踩过最深的坑是什么？*
