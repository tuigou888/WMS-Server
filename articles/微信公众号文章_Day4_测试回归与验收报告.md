# 【Day 4】测试只有 1 个，但 101 项检查全过：这份"小作坊"式的回归报告，是怎么让仓库系统"够用"的

> Day 3 我们把单据流、Excel 报表、Docker 部署搭起来了。但每次手动点一遍页面、跑一遍接口才能发版，显然扛不住真实迭代。这一篇就来聊聊：**测试、回归、和那份"诚实"的生产评估报告。**

> 📌 本文是「从零搭建仓库进销存系统」系列的 **Day 4**。这一篇会揭开仓库里两个 Python 脚本、两份报告背后的故事——也是这个项目"能交付但还不是生产级"的核心原因。

---

## 一、先说一个残酷的事实

朋友验收那天，我打开后端目录，自信满满地说："我们有完整的测试覆盖。"

`mvn test` 一跑：

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

—— **2 个测试，覆盖了 0 个 Service**。

整个项目后端只有 `InventoryCostCalculatorTest` 一个文件，里面就两个 `@Test`：一个算移动加权平均，一个算调拨到空库存的边界。**没有 Service 层单测，没有 Controller 集成测试，没有状态机测试，没有并发测试。**

朋友翻了一圈 `wms-server/src/test/`，抬头看我："就这？"

我老实答：**就这。**

但我也告诉他：仓库里还有另外一套东西——`test-artifacts/run_api_regression.py` 跑了 70 项 API 全过，`run_ui_regression.py` 跑了 29 项 UI 全过。加上单元测试，**合计 101 项检查全过**。

"那到底算不算稳？"他问。

这个问题我想了一晚上，最终写了 `PRODUCTION_READINESS_REPORT.md` 这份报告。结论是：**45/100，MVP/试点级**，**不建议正式生产上线**。

下面这篇文章，就把这中间的所有纠结讲清楚。

---

## 二、为什么只有 1 个 Java 单测？

### 2.1 不是没想过写，而是成本不匹配

写 JUnit + Mockito 覆盖一个 `InventoryService` 的 `stockIn`，至少要 mock 5 个 Repository，再构造请求和实体。一晚上能稳覆盖 1 个方法。

而 `InventoryService` 一共 4 个核心方法 + N 个边界判断（库存不足、库位不存在、批次号归一、成本精度…），整个 Service 层算下来 30+ 个用例。**对一个一年只改两次的小型仓库系统，性价比极低**。

更关键的是：**真正的回归不是单测能解决的**。

| 维度 | 单元测试 | E2E 回归 |
|------|----------|----------|
| 验证对象 | 一个方法 | 一条业务链路 |
| 例：扫码入库 | averageCost 计算对不对 | 登录 → 扫码 → 库存对 → 流水对 → 利润对 |
| 写一条的成本 | 中 | 低（直接打 HTTP） |
| 跑一次的反馈 | 局部 | 整条链路 |

我决定走一条折中路线：

- **1 个高价值单测**（成本计算——唯一会因浮点/精度翻车的算法）
- **1 套 E2E 回归脚本**（覆盖所有接口、所有状态、所有角色、所有业务链路）
- **1 份验收报告**（老板/客户都能看懂）

### 2.2 那个仅有的单测为什么值得保留

```java
@Test void calculatesMovingWeightedAverage(){
    BigDecimal amount = InventoryCostCalculator.amount(
        new BigDecimal("200"), new BigDecimal("12"));
    assertEquals(new BigDecimal("2400.00"), amount);
    assertEquals(new BigDecimal("11.3333"),
        InventoryCostCalculator.averageCost(
            new BigDecimal("100"), new BigDecimal("1000"),
            new BigDecimal("200"), amount));
}

@Test void transferIntoEmptyInventoryKeepsSourceAverageCost(){
    assertEquals(new BigDecimal("13.5417"),
        InventoryCostCalculator.transferAverageCost(
            BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("4"), new BigDecimal("13.5417")));
}
```

它测的是**最容易翻车的那一类问题**——浮点和精度。**这类问题一旦埋下去，半年后对账才能发现，排查成本极高。** 一个测试 5 秒跑完，省下未来 5 个晚上的排查，值。

> **取舍原则**：单测只覆盖"一旦坏了查起来最痛"的那一两个核心算法，其它全交给 E2E。**别追求覆盖率数字，追求"坏的时候能不能第一时间知道"。**

---

## 三、`run_api_regression.py`：用 200 行 Python 替代 2000 行 JUnit

### 3.1 一个最小可用框架

整个脚本就是一个 `record(module, name, fn)` 函数：跑一个测试用例，记一条 PASS/FAIL，最后汇总写 JSON。**和 JUnit 的本质一模一样，只是用 Python 实现。**

```python
def record(module, name, fn):
    started = time.time()
    try:
        detail = fn() or "符合预期"
        results.append({"module": module, "name": name,
                        "ok": True, "detail": str(detail),
                        "durationMs": round((time.time()-started)*1000)})
        print(f"PASS [{module}] {name}: {detail}")
    except Exception as exc:
        results.append({"module": module, "name": name,
                        "ok": False, "detail": str(exc),
                        "durationMs": round((time.time()-started)*1000)})
        print(f"FAIL [{module}] {name}: {exc}")
```

### 3.2 一次真实的"全链路用例"

最精彩的不是登录、CRUD 这些普通用例，而是**端到端的业务链路**——一个用例走完"扫码入库 → 创建入库单 → 审核 → 执行 → 验证库存/均价"全流程：

```python
def transfer_flow():
    p = {"sourceWarehouseId": state["main_wh"],
         "targetWarehouseId": state["target_wh"],
         "remark": "transfer",
         "lines":[{"itemCode":item_code,
                   "sourceLocationCode":"A-TEST-01",
                   "targetLocationCode":"B-TEST-01",
                   "batchNo":"B1","quantity":4}]}
    d = call(operator,"POST","/transfers",json_body=p)
    state["transfer_id"] = d["id"]
    call(admin,"POST",f"/transfers/{d['id']}/review",
         json_body={"action":"APPROVE"})
    done = call(operator,"POST",f"/transfers/{d['id']}/complete")
    eq(done["status"], "COMPLETED", "status")
    inv = call(admin,"GET",f"/inventory/{state['item_id']}")
    target = next(x for x in inv
                  if x["warehouseId"]==state["target_wh"])
    eq(dec(target["quantity"]), 4.0, "target quantity")
    eq(dec(target["avgCost"]), state["source_avg"], "target avg cost")
record("库存调拨", "库存调拨完整流程及成本继承", transfer_flow)
```

这个用例同时验证了：
- 调拨单状态流转（DRAFT → APPROVED → COMPLETED）
- 操作员/管理员角色权限
- 目标仓的库存数量正确
- **目标仓的均价 = 源仓均价**（成本继承）

—— **一句话里覆盖了 4 类不变量。** 这就是 E2E 测试的复利。

### 3.3 70 项用例怎么来的？

| 模块 | 用例数 | 关注点 |
|------|------:|--------|
| 认证鉴权 | 5 | 401、错密码、Token 失效、角色对 |
| 权限控制 | 2 | 操作员不能进管理员接口 |
| 物品档案 | 7 | CRUD + 重复编码 + 关键词 + 分页 |
| 供应商/客户 | 7 | SUPPLIER/CUSTOMER/BOTH + 类型筛选 |
| 仓库管理 | 4 | CRUD + 编码重复 + includeDisabled |
| 扫码出入库 | 3 | 入库 + 出库 + 利润 + 库存不足拦截 |
| 库存管理 | 4 | 列表/分页/流水/仓库 |
| 入出库单 | 8 | 创建/审核/执行/取消/驳回 + 状态机校验 |
| 库存调拨 | 4 | 全流程 + 驳回 + 同仓拦截 |
| 库存盘点 | 3 | 全流程 + 驳回 |
| 报表中心 | 4 | dashboard/预警/利润/异常 |
| 二维码/Excel | 5 | QR 生成 + PNG + 导出导入 |
| OCR | 2 | mock 识别 + 空文件校验 |
| 用户与权限 | 2 | 创建/停用 |
| **合计** | **70** | |

> 整个脚本 286 行，依赖只有 `requests` 和 `openpyxl`，**没有任何 Java/Spring/Selenium 依赖**。改完代码直接 `mvn spring-boot:run`，再 `python3 run_api_regression.py http://localhost:8088/api/v1` 就能跑。

---

## 四、`run_ui_regression.py`：不用 Selenium 也能跑浏览器

### 4.1 为什么要"另起炉灶"？

本来想用 Selenium 或 Playwright，但回头想想：

- Selenium 要装浏览器驱动（chromedriver 版本还得对得上 chrome 版本）
- Playwright 要装一堆东西
- **我们只是想验证页面能加载、菜单能切、没有 JS 报错而已**

于是我用了 Chrome 自己的 **DevTools Protocol (CDP)**，加上 Python 标准库的 `socket` 手写了一个最小 WebSocket 客户端。**100 行代码，没有任何第三方浏览器依赖。**

### 4.2 真实跑起来什么样？

```python
chrome = subprocess.Popen([
    "google-chrome","--headless=new","--no-sandbox","--disable-gpu",
    "--remote-debugging-port=" + str(port),
    "--remote-allow-origins=*",
    f"--user-data-dir={profile}", "about:blank"],
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

# 找到 page target，建立 WebSocket
cdp = CDP(page["webSocketDebuggerUrl"])
cdp.command("Page.navigate", {"url": BASE_URL})
```

接下来就是用 JS 表达式驱动页面。比如模拟管理员登录：

```python
cdp.eval(f"""
(async () => {{
  await waitFor(() => [...document.querySelectorAll('button')]
    .find(x => text(x) === '登录系统'), 10000, 'login page');
  const inputs = [...document.querySelectorAll('input')];
  setValue(inputs.find(x => x.type !== 'password'), 'admin');
  setValue(inputs.find(x => x.type === 'password'), 'admin123');
  [...document.querySelectorAll('button')]
    .find(x => text(x) === '登录系统').click();
  return {{token: !!localStorage.getItem('wms_token'),
           menus: [...document.querySelectorAll('.ant-menu-title-content')]
                   .map(text)}};
}})()
""")
```

拿到结果后做断言：

```python
assertion("管理员登录成功", admin.get("token"), True)
assertion("管理员菜单完整", admin.get("menus"),
          ["仪表盘","物品档案","供应商 / 客户","扫码入库",
           "扫码出库","入库 / 出库单","库存调拨","库存盘点",
           "库存管理","报表中心","二维码与 Excel","用户与权限"])
```

**29 项 UI 检查，包含 12 个菜单的页面切换、操作员登录后菜单隐藏、调拨批次字段可见性、JS 异常 / 控制台错误 / HTTP 5xx 全 0。**

### 4.3 它跑出来后长什么样？

执行完会在 `test-artifacts/` 留下三个东西：

```
ui-results-current.json     # 29 项结果明细
ui-admin-dashboard-current.png  # 仪表盘截图
login-page.png              # 登录页截图
```

**老板打开截图就能看到仪表盘长什么样**，不用再问"这个系统好不好看"。

> **取舍原则**：UI 自动化不追求"能点按钮"，追求"页面别崩、菜单别缺、角色别乱、截图能截"。**这是对交付最有价值的部分，性价比远高于搞复杂的 E2E。**

---

## 五、101 项全过 ≠ 系统稳定

### 5.1 看看"全过"的报告长什么样

`FUNCTIONAL_TEST_REPORT_CURRENT.md` 是这样组织的：

```markdown
## 1. 测试结论

| 测试类别      | 通过 | 失败 | 结果 |
|-------------|----:|----:|------|
| 后端单元测试   |   2 |   0 | 通过 |
| API 业务回归 |  70 |   0 | 通过 |
| 浏览器 UI 回归|  29 |   0 | 通过 |
| **合计**     | **101** | **0** | **全部通过** |

## 2. 功能覆盖（按模块拆）

### 2.1 登录、退出与权限（...5 条用例...）
### 2.2 基础资料（...7 条...）
### 2.3 库存与扫码业务（...3 条...）
### 2.4 入库单与出库单（...8 条...）
...
```

这种结构对老板/客户特别友好——他们不用懂技术细节，**看到 101 项通过 + 模块拆分，心里就有底了**。

但我接下来要写的那份报告，结论是反过来的。

### 5.2 `PRODUCTION_READINESS_REPORT.md`：45/100，MVP 级

这份报告分成了三类问题，每一类都列了证据 + 影响 + 上线要求：

**P0-01：默认开发模式启动**
> `application.yml` 默认 H2 内存库 + `ddl-auto: update` + 始终启用 H2 Console。生产环境如果忘了设 `SPRING_PROFILES_ACTIVE=prod`，数据库会"凭空启动"在内存里，重启即丢数据。

**P0-02：单号生成在重启和多实例下不安全**
> `DocumentNumberService` 用的是进程内 `AtomicLong`。**重启一次可能重复单号；上 K8s 多副本会直接撞唯一约束。**

**P0-03：单据执行缺少幂等和单据级并发控制**
> 执行入库单时，先读 `status` → 逐行改库存 → 改 `status=COMPLETED`。两个请求并发跑，可能都读到 `APPROVED`，结果库存被扣两次。
>
> 库存行锁只能串行化"改这一行"，**管不到"同一张单据只能执行一次"**。

**P0-07：自动化测试不足**
> 这是这一篇的核心。101 项检查全过，覆盖的是：
> - ✅ 正常路径（登录、CRUD、状态机正向）
> - ✅ 一些反向路径（错密码、重复编码、库存不足、同仓调拨）
>
> 但**以下这些都没覆盖**：
> - MySQL 真实集成（一直在 H2 上跑）
> - 单据重复执行的并发场景
> - 并发首次建库存（@PESSIMISTIC_WRITE 之外的竞争窗口）
> - 调拨死锁和失败回滚
> - 盘点期间发生收发货
> - Excel 异常文件 / 超大文件
> - 备份恢复 / 升级迁移

**结论是**：能交付 ≠ 能上线。能演示 ≠ 能跑生产。

### 5.3 报告的诚实，是工程师最重要的能力

写这份报告那天我整了一下午。**没有人爱看自己项目的负面清单**，但反过来想：

> **如果交付方不主动暴露这些风险，使用方一定会在生产事故里替你发现。**
> **而生产事故的代价，比承认不足的代价，大一个数量级。**

所以报告里我写得很直白：

> 当前系统可以用于功能演示，也具备小范围、受控试点的基础，但**不能按现状认定为可直接上线并正式交付的生产级仓库库存管理系统**。
>
> 综合成熟度建议评为 **45/100（MVP/试点级）**。

朋友看完沉默了一会儿，说：**"那你把 P0 那些事帮我列个时间表，下个月我们慢慢改。"**

这就对了。**报告不是用来证明"我们没问题"的，是用来证明"我们知道我们哪里有问题"的。**

---

## 六、一份给老板看的"验收报告"，应该长什么样？

这一节是写给所有做小项目的同学的。结合两份报告的实际写法，我总结了几个原则：

### 6.1 先结论，再细节

第一屏必须能让人 3 秒看懂：
- **能不能用**（结论）
- **测了什么**（覆盖范围）
- **没测什么**（诚实声明）

### 6.2 数据说话，不要形容词

不要说"系统运行稳定"，要说"`mvn test` 通过 2/2，`run_api_regression.py` 通过 70/70，"chrome headless" UI 回归通过 29/29，无 HTTP 5xx，无 JS 异常"。

### 6.3 截图比文字直观

`ui-admin-dashboard-current.png` 这种截图，胜过 100 句"页面美观大方"。

### 6.4 把"不知道"也写出来

OCR 当前是 mock、没压测、没多浏览器兼容、没渗透测试……**写出来，反而是加分项。** 客户看到的不是"我们不行"，而是"我们清醒"。

### 6.5 给整改路线

P0 / P1 / P2 三档分级 + 时间表，让对方知道"什么时候能升级到什么程度"。**有路线图的不完美，比没路线图的"完美"更可信。**

---

## 七、Day 4 的几点总结

**1. 测试不在多，在于坏的时候能第一时间知道。**

101 项全过不叫稳，叫"目前观察到的范围内没问题"。**真正的稳，是把所有可能坏的姿势都试过一遍。** 我们试了正向、试了一些反向，但 P0-03 描述的并发重复执行、跨实体的并发竞争、MySQL 兼容性，**还没来得及试**。

**2. 选最便宜的工具，不选最"工程化"的工具。**

JUnit / Mockito / Selenium / Playwright 都是好东西，但**对一年改两次的小型仓库系统，1 个 JUnit + 1 个 Python + 1 个 CDP 反而是性价比最高的选择**。别为了简历上多一行技能栈，把项目拖到交付不了。

**3. 报告的诚实度，决定项目的天花板。**

`PRODUCTION_READINESS_REPORT.md` 那句"45/100，不建议正式生产上线"写出来时，朋友明显愣了一下。但第二天他主动加了一笔预算让我做 P0 整改。**主动暴露问题的人，会拿到主动解决问题的资源；藏着掖着的人，会被生产事故一次性掀翻。**

**4. 别忘了交付的下半场。**

Day 1-3 我们盖了房子、装修、入住。Day 4 之后还有：
- 数据库迁移脚本（Flyway/Liquibase）
- 监控告警（Grafana + Prometheus，或更轻的方案）
- 灾备演练
- 真实 OCR 接入（替换 mock）
- 多实例部署下的 Token 共享（Redis）

这些是 Day 5-7 的故事。**一个项目的真正交付，从代码完成那天才开始。**

---

> **项目地址**：GitHub（私信获取）
> **技术栈**：Spring Boot 3 + JPA + React + Ant Design + MySQL + Python + Chrome DevTools Protocol
> **本篇方法**：用 1 个 JUnit + 200 行 Python + 100 行 CDP 替代完整的 Java 测试体系；用两份"诚实"的报告替代漂亮的覆盖率数字

---

## 📮 预告：Day 5 讲什么？

到今天为止，「账 / 权 / 流 / 部署 / 测 / 报告」我们都聊过了。但还有一个真实问题没正面回答：

- **朋友的下一步是真实的 MySQL 上线，不是 demo。** 那生产环境的 `SPRING_PROFILES_ACTIVE` 怎么切？数据库迁移脚本怎么写？H2 跑得好好的，为什么到了 MySQL 就出问题？
- **生产可交付报告里那一堆 P0**：单号持久化、单据幂等、Token 跨实例、权限矩阵……**哪些是真正必须先做的，哪些可以排到 P1？**
- **我做了那么多"为了能跑"的取巧**（进程内单号、内存 Token、H2 默认），**把它们一个个替换成生产级实现，要多久？**

Day 5 我们就动手做这件事：**把"能跑"一步一步改成"能生产"。** 不是大改架构，是一个个 P0 一个个 P0 地啃。

---

*如果这四篇对你有帮助，欢迎点赞、转发、在看。评论区聊聊：你做过的项目里，写过的最诚实的一份报告长什么样？*