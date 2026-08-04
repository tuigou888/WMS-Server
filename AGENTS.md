# AGENTS.md

WMS 仓库进销存系统 — Spring Boot 3.3 / Java 21 / JPA 后端 + Vue 3 / Vite 6 / Ant Design Vue 4 前端。
完整业务/接口契约见 `README.md` 与 `DEVELOPMENT.md`；本文只列必踩坑与速查。

## 仓库结构

- `wms-server/` — Spring Boot 3.3.12 / Java 21 / JPA。**不要删** `package.json`（`recharts` 占位依赖，未启用）。
- `wms-web/` — Vue 3 + Vite 6 + Ant Design Vue 4 + axios + dayjs + echarts。入口 `src/main.js`，API 封装 `src/api/wms.js`，HTTP 拦截 `src/api/client.js`。
- `wms-miniapp/` — uni-app + Vue 3 + Pinia 微信小程序端。入口 `src/main.js`，API 封装 `src/api/request.js`（统一 token/401/解壳），编译产物 `dist/build/mp-weixin`。
- `test-artifacts/` — Python 烟测脚本（依赖 `requests`、`openpyxl`），输出 `api-results-current.json` / `ui-results-current.json`。

## 后端起步 / 验证

```bash
cd wms-server
mvn spring-boot:run        # http://localhost:8088/api/v1
mvn test                   # 4 个测试类：InventoryCostCalculatorTest、AdjustmentIntegrationTest、DocumentNumberServiceTest、AuthControllerWxTest
mvn -DskipTests package    # 构建 jar
```

- 默认 H2 内存库 (`jdbc:h2:mem:wms`)，首次启动由 `DemoDataConfig` 写入 2 个用户 + 3 个物品 + 初始库存。**重启即丢数据、Token 失效**。
- 切 MySQL：`export DB_URL=... DB_USERNAME=... DB_PASSWORD=... DB_DRIVER=com.mysql.cj.jdbc.Driver` 再 `mvn spring-boot:run`。
- `server.servlet.context-path=/api/v1`、H2 控制台 `/h2-console` 仅在默认/无 profile（=`dev`）下放行。
- 健康检查 `GET /api/v1/health`（唯一未鉴权业务接口）。

## 前端起步 / 验证

```bash
cd wms-web
npm install
npm run dev      # vite 5173，/api/v1 已 proxy -> :8088
npm run build    # 产物到 dist/；antd 主包体积警告可忽略
```

- `vite.config.js` 代理：`/api/v1 -> http://localhost:8088`。
- `wms-web/nginx.conf`（Docker 镜像内已嵌入）反向代理 `/api/v1 -> wms-server:8088`，并通过 `try_files` 兜底 SPA 路由。

## 小程序端起步 / 验证

```bash
cd wms-miniapp
npm install
npm run build:mp-weixin   # 产物 dist/build/mp-weixin，用微信开发者工具导入（勾选"不校验合法域名"）
npm run dev:h5            # 或 H5 调试（需后端 CORS 放行，见 DEVELOPMENT.md）
```

- **微信登录须 mock 或配置真实 appid/secret**：`application.yml` 中 `wechat.mock` 默认 `true`（code 直接当 openid 用，方便本地联调：登录传任意 code 如 `test-openid-123`）；生产置 `false` 并从环境变量注入 `WECHAT_APPID/WECHAT_SECRET`。**生产环境（非 dev/test profile）误开 mock 会被拒绝（400）**。
- 登录流程：`POST /auth/wx-login {code}` → 未绑定返回 `{needBind:true, openid}` → `POST /auth/wx-bind {openid,username,password}` → 返回 token（与账号密码登录同壳）；已绑定直接返回 token。绑定关系存在 `user_accounts.openid`。
- **登录/绑定限速**：`LoginRateLimiter` 按 `IP|用户名` 统计，窗口 10 分钟失败 5 次后返回 HTTP `429`（`RateLimitedException`），成功登录或绑定即清零。测试/脚本注意别触发。
- **演示数据仅 dev/test 播种**：`DemoDataConfig` 无 active profile 时视为 dev；生产（如 `prod` profile）不创建 `admin/admin123` 与演示物品，否则被审计判为默认口令风险。
- **权限注意**：`POST /stock/in/scan`、`/stock/out/scan` 仅 ADMIN（`hasRole('ADMIN')`）；WAREHOUSE 扫码出入库页会显示"仅管理员"提示条，实际只能走单据流程与盘点录入。`GET /logs` 需 `log:view`（仅 ADMIN），日志只由 `OperationLogAspect` 写入，无手动写入端点（防审计伪造）；单据取消/反审/红冲仅 ADMIN。
- 小程序页面字段与 API 对齐要点：盘点单 `stocktakeNo/createdAt/lines[].bookQuantity`；单据 `businessDate`、line 用 `unitPrice`、无汇总字段（前端自算）；调拨字段 `sourceWarehouseId/sourceLocationCode/targetLocationCode`；`GET /items` 返回 `{total,records}` 分页对象而非数组。
- 调拨单无 `GET /transfers/{id}` 详情接口，小程序详情页从列表缓存（storage `wms_transfer_detail`）读取。

## 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN |
| `operator` | `operator123` | WAREHOUSE |

登录 `POST /auth/login` → `data.token` 写入 `localStorage('wms_token')`，请求头 `Authorization: Bearer <token>`。Token 仅内存（`TokenService` ConcurrentHashMap），有效期 12h。

## 关键架构约束

- **统一响应壳**：`ApiResponse<T>(code, message, data)`，前端 `client.js` 仅在 `code === 200` 时 `resolve(data)`，其它走 `reject(message)`。
- **权限并非 Spring `@PreAuthorize`**：文档/单据/调拨/盘点/仓库/用户管理在 Service/Controller 内部调用 `ensureAdmin()` 读 `SecurityContextHolder` 强判 `role == "ADMIN"`。WAREHOUSE 角色只能创建草稿/完成已审核单据。
- **审计/时间戳**：`AuditableEntity` 的 `createdAt`/`updatedAt` 由 `@PrePersist`/`@PreUpdate` 写入。
- **库存加权成本**：`InventoryCostCalculator`（`#COST_SCALE=4`，金额 `2`，HALF_UP）。单测断言直接对照 4 位小数。
- **状态机**（草稿/审核/执行/取消）：
  - `Document` IN/OUT：`DRAFT → APPROVED → COMPLETED`，可 `REJECTED`/`CANCELLED`（已执行不可取消）。
  - `TransferOrder`：同文档流；调出/调入仓库必须不同。
  - `StocktakeOrder`：`DRAFT` 录入实盘 → `APPROVED` → `COMPLETED`（系统按差异生成 `adjust_in/out` 流水并调库存）。
- **单据号**：`DocumentNumberService` 基于持久化 `document_sequences` 表 + 行锁取号（此前为 `AtomicLong` 重启重号）；`next(prefix)` 会落库递增，重启不重号。
- **唯一库位**：`Inventory` 实体的 `(item, warehouse, location, batchNo)` 唯一约束；`InventoryRepository.findForUpdate` 用 `PESSIMISTIC_WRITE` 锁行。
- **库存锁与无库位**：扫码入库会按需自动 `Location`；扫码出库/调拨出库时库位不存在会抛 `BusinessException("该库位没有库存")`。
- **往来单位**：IN 单必须 `SUPPLIER` 或 `BOTH`，OUT 必须 `CUSTOMER` 或 `BOTH`（`DocumentService.partner` 强制）。RETURN_IN 用客户、RETURN_OUT 用供应商。
- **批次号**：空串会被 `normalizeBatch` 归一为 `null`，库存键包含 `batchNo`。
- **操作日志**：`aspect/OperationLogAspect` 用 AOP 包裹 Controller 写 `operation_logs`；`OperationLogService.record` 用 `REQUIRES_NEW` 嵌套事务，业务回滚也不丢日志。

## 状态机补充（新增单据）

- `AdjustmentOrder`（报损/报溢）：`DRAFT → APPROVED → COMPLETED`（`LOSS` 减库存、`GAIN` 加库存，均走 `InventoryService.adjust` → `loss_out`/`gain_in` 流水）。
- `Document` 追加类型：`RETURN_IN`（客户退货入库，前缀 THI）、`RETURN_OUT`（退回供应商出库，前缀 THO）。
- **反审** `POST /documents/{id}/uncomplete`：仅 ADMIN，`COMPLETED → APPROVED`，自动生成 `reverse` 流水冲销。
- **红冲** `POST /documents/{id}/reverse`：仅 ADMIN，生成类型互反、`documentNo+".V"` 的新单据并直接置为 `APPROVED`，原单保留。

## 接口速查（实际前缀 `/api/v1`）

- `auth`：`POST /login`、`GET /me`、`POST /logout`、`GET/POST /users`、`PUT /users/{id}`（除登录全需 token）。
- `documents/transfers/stocktakes`：CRUD + `/review` + `/complete`（盘点还有 `/count`）。
- `stock/in/scan` / `stock/out/scan`：直写库存，不走单据。
- `inventory`：`GET /`、`GET /transactions`、`GET /warehouses`、`GET /{itemId}`。
- `items`：`GET /`（page/pageSize/keyword）、`GET /{id}`、`GET /code/{code}`、`POST/PUT/DELETE`、`GET /categories`。
- `partners`：`GET ?type=SUPPLIER|CUSTOMER`，`POST/PUT/DELETE`。
- `warehouses`：`GET ?includeDisabled=true`、`POST/PUT`（管理员）。
- `qrcodes`：`GET /items/{code}`（返回 base64 PNG）、`GET /items/{code}/png`（图片字节流）。
- `excel`：`GET /items/export`、`POST /items/import`（multipart，列名见 DEVELOPMENT.md）。
- `reports`：`/dashboard`、`/stock-alert`、`/profit`、`/anomalies`、`/inventory-age`（库龄按 0-30/30-60/60-90/>90 分桶）、`/in-out-summary?period=YYYY-MM`（收发存汇总）。
- `adjustments`：`GET/POST /`、`POST /{id}/review`、`POST /{id}/complete`（报损/报溢）。
- `documents` 新增：`POST /{id}/uncomplete`（反审）、`POST /{id}/reverse`（红冲）；`/documents` 支持 `RETURN_IN`/`RETURN_OUT` 类型。
- `logs`：`GET /`（按 username/action/result 过滤查询操作日志）。
- `ocr/recognize`：**当前为 mock**（`OcrController` 注释明示；返回 `source: "mock"`），不要在此接口上构建依赖真实识别的功能。

## 编码规范

- 后端文件**一行业务代码**风格（大量类/方法压成一行），新增逻辑请遵循 `XXService.java` 既有写法，不要把它"展开"重排。
- 货币用 `BigDecimal`，必传 `RoundingMode.HALF_UP`；不使用 double。
- 前端页面位于 `src/pages/*Page.vue`，用 Ant Design Vue 组件；菜单/路由集中在 `src/router/index.js`。
- 后端禁用 Profile 名为 `dev`（默认无 profile 也是 dev），H2 console 与开发放行逻辑依赖这个判断。

## 验证脚本

```bash
# API 回归（默认指向 http://127.0.0.1:18088/api/v1，可作为第一参数覆盖）
python3 test-artifacts/run_api_regression.py http://localhost:8088/api/v1

# UI 回归（用 Chrome DevTools Protocol + /usr/bin/chromium 或 google-chrome，需先把 :5173 跑起来）
python3 test-artifacts/run_ui_regression.py http://localhost:5173/
```

注意：`test-artifacts/api-results.json` 与 `test-artifacts/ui-results.json` 是历史快照，会被 `run_*_regression.py` 覆盖；写报告时优先用 `-current.json`。

## 部署

- `docker-compose.yml` 仅 MySQL + `wms-server` + `wms-web` 三个服务（Web 3000:80，API 8088:8088，MySQL 3306）。**README.md 描述的 Redis/MinIO/Nginx/微信小程序 在仓库里没有对应服务或 pom 依赖**，要按需自行补；不要假设它们已生效。
- `wms-server/Dockerfile` 多阶段：maven 3.9 + temurin-21 构建 → `eclipse-temurin:21-jre` 运行。
- `wms-web/Dockerfile` 多阶段：node 20-alpine 构建 → nginx:alpine，使用 `wms-web/nginx.conf`。
- MySQL root 密码默认 `wms_password`（compose 内），通过 `MYSQL_ROOT_PASSWORD` 环境变量覆盖。

## 改动后建议跑

```bash
cd wms-server && mvn test
cd ../wms-web && npm run build
```

需要实战跑通链路：先 `mvn spring-boot:run`，再 `npm run dev`，浏览器登录 admin 后改菜单 → 复核接口 → 单测。