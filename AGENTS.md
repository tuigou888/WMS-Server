# AGENTS.md

WMS 仓库进销存系统 — Spring Boot 3.3 / Java 21 / JPA 后端 + Vue 3 / Vite 6 / Ant Design Vue 4 前端。
完整业务/接口契约见 `README.md` 与 `DEVELOPMENT.md`；本文只列必踩坑与速查。

## 仓库结构

- `wms-server/` — Spring Boot 3.3.12 / Java 21 / JPA。**不要删** `package.json`（`recharts` 占位依赖，未启用）。
- `wms-web/` — Vue 3 + Vite 6 + Ant Design Vue 4 + axios + dayjs + echarts。入口 `src/main.js`，API 封装 `src/api/wms.js`，HTTP 拦截 `src/api/client.js`。
- `wms-miniapp/` — uni-app + Vue 3 + Pinia，**仓储作业端**（入库/出库/盘点/调拨/扫码，面向 ADMIN 与 WAREHOUSE）。入口 `src/main.js`，API 封装 `src/api/request.js`（统一 token/401/解壳），编译产物 `dist/build/mp-weixin`。
- `wms-shopping-miniapp/` — uni-app + Vue 3 + Pinia，**买家商城端**（商品/购物车/下单/微信支付/收货，账号由 `POST /auth/wx-register` 开成 `CUSTOMER`）。入口 `src/main.js`，API 封装 `src/api/request.js` + `src/api/market.js`，构建脚本用 `build:wechat`（与作业端的 `build:mp-weixin` 不同），产物 `dist/build/mp-weixin`。
- `test-artifacts/` — Python 烟测脚本（依赖 `requests`、`openpyxl`），输出 `api-results-current.json` / `ui-results-current.json`。

## 后端起步 / 验证

```bash
cd wms-server
mvn spring-boot:run        # http://localhost:8088/api/v1
mvn test                   # 15 个测试类（177 用例）：MarketControllerTest、MarketServiceIntegrationTest、WechatPayServiceTest、WechatPayNotifyControllerTest、AdjustmentIntegrationTest、AuthControllerWxTest、AuditArchiveServiceTest、DocumentNumberServiceTest、HealthControllerTest、InventoryCostCalculatorTest、LocalCorsIntegrationTest、OcrControllerTest、ProductionBootstrapConfigTest、ReportControllerIntegrationTest、RolePermissionsTest（`Harness` 是共享工具类，不是测试）
mvn -DskipTests package    # 构建 jar
```

- 默认 H2 内存库 (`jdbc:h2:mem:wms`)，首次启动由 `DemoDataConfig` 写入 2 个用户 + 3 个物品 + 初始库存。**重启即丢数据**；认证态也已改为落库（`auth_sessions`/`auth_login_attempts`/`wechat_bind_tickets`），所以默认 H2 下 Token 随库一起消失，但切到 MySQL 后 **Token 跨重启仍然有效**。
- **索引只能靠迁移脚本改**：`application-prod.yml` 是 `ddl-auto: validate`，实体上的 `@Index` 在生产**不会**建索引（dev/test 才靠 ddl-auto 建表）。加索引要写 `db/migration/Vn__*.sql`，且必须同时在 MySQL 8 与 H2 `MODE=MySQL` 下可执行（`create index` 直接写，别用 `using btree` 之类单方言语法）；测试 profile 关掉了 Flyway，所以 `mvn test` **不会**验证新脚本，要靠启动默认 profile 过一遍。
- 切 MySQL：`export DB_URL=... DB_USERNAME=... DB_PASSWORD=... DB_DRIVER=com.mysql.cj.jdbc.Driver` 再 `mvn spring-boot:run`。
- `server.servlet.context-path=/api/v1`、H2 控制台 `/h2-console` 仅在默认/无 profile（=`dev`）下放行。
- 健康检查 `GET /api/v1/health`（免鉴权；但**不是唯一**免鉴权接口，完整 permitAll 清单见「关键架构约束」）。`GET /api/v1/health/ready` 会真连库（`DataSource#isValid(2)`）。
- **指标/健康端点走独立 management 端口，不在 8088 上**：`management.server.port` 默认 `9089`（`MANAGEMENT_PORT` 覆盖），因此绕开 `server.servlet.context-path=/api/v1`。但**独立端口不会自动绕过 Spring Security**——实测无 token 请求 `/actuator/health`、`/actuator/prometheus` 都是 401；放行由 `SecurityConfig.managementPortChain` 按 `localPort` 精确匹配完成（只在配了独立管理端口且它与业务端口不同时生效，测试用随机端口时匹配器恒 false）。业务端口上的 `/api/v1/actuator/**` 仍是 401 且无 handler。只暴露 `health,info,prometheus` 且 `show-details: never`；compose 默认绑 `127.0.0.1:9089`（`WMS_MGMT_BIND`），**不要公网发布**。
- **日志已落盘并带 traceId**：`logback-spring.xml` 写 `${WMS_LOG_DIR:-logs}/wms-server.log`（50MB 单文件 / 30 天 / 2GB 上限），每行含 `[trace:%X{traceId}]`；`TraceIdFilter` 优先沿用请求头 `X-Trace-Id`（仅接受 `[A-Za-z0-9._-]{1,64}`，防日志伪造），否则自生成 16 位 hex 并回写同名响应头。可调项：`SLOW_SQL_THRESHOLD_MS`（默认 200，Hibernate 慢 SQL 日志；慢语句由 `org.hibernate.SQL_SLOW` 打印，**Hibernate 6 用的是 INFO 级**，`LOG_LEVEL_SLOW_SQL` 设成 WARN 会把慢日志全吃掉）、`LOG_LEVEL_APP`、`DB_POOL_MAX_SIZE`/`DB_POOL_MIN_IDLE`/`DB_CONNECTION_TIMEOUT_MS`/`DB_POOL_MAX_LIFETIME_MS`/`DB_LEAK_DETECTION_MS`（prod profile；`max-lifetime` 必须小于 MySQL `wait_timeout`）。

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

两端都是 uni-app + Vue 3 + Pinia，命令名不同，别混用。

```bash
# 仓储作业端
cd wms-miniapp
npm install
npm run build:mp-weixin   # 产物 dist/build/mp-weixin，用微信开发者工具导入（勾选"不校验合法域名"）
npm run dev:h5            # 或 H5 调试（需后端 CORS 放行，见 DEVELOPMENT.md）

# 买家商城端
cd ../wms-shopping-miniapp
npm install
npm run build:wechat      # 产物同样是 dist/build/mp-weixin；npm run build 是 H5 构建
```

- **两个小程序的 appid 目前是同一个**：`wms-miniapp/src/manifest.json` 与 `wms-shopping-miniapp/src/manifest.json` 的 `mp-weixin.appid` 都写成了同一个值。微信后台一个 appid 只对应一个小程序，两端上线前必须各自申请并分别填写，否则后上传的会覆盖前一个。发布前跑各端的 `npm run check:release`（要求 appid 非空且 `VITE_API_BASE` 为 HTTPS）。
- **买家端所有 `/market/**` 接口都需要 token**（`SecurityConfig` 未放行，见「关键架构约束」）。免鉴权的只有登录/注册/绑定和两个支付回调；前端未登录时先走 `/auth/wx-login`，未绑定则 `/auth/wx-register` 开 `CUSTOMER` 账号。
- 买家端的 `mockPay`（`POST /market/orders/{id}/mock-pay`）只在 `wechat.pay.mock=true` 时可用，prod profile 强制 false，别把它当正式支付流程。
- **微信登录须显式开 mock 或配置真实 appid/secret**：`application.yml` 中 `wechat.mock: ${WECHAT_MOCK:false}`，**默认已改为 `false`**（`WechatService` 的 `@Value` 兜底同为 false）。本地联调要 `export WECHAT_MOCK=true`，此时 code 直接当 openid 用（登录传任意 code 如 `test-openid-123`）；未开 mock 且缺 `WECHAT_APPID/WECHAT_SECRET` 会直接报"微信小程序未配置"。**非 dev/test profile 误开 mock 会被 `WechatService` 抛 `BusinessException` → HTTP 400**。注意 `wechat.pay.mock` 是另一个开关，默认 true、prod profile 强制 false。
- 登录流程：`POST /auth/wx-login {code}` → 已绑定直接返回 token（与账号密码登录同壳）；未绑定返回 `{needBind:true, bindTicket, expiresIn:300}`，**不再下发 openid**。凭 ticket 走 `POST /auth/wx-bind {bindTicket,username,password}` 绑定已有账号，或 `POST /auth/wx-register {bindTicket,username,password,displayName}` 新开 `CUSTOMER` 角色买家账号。票据存 `wechat_bind_tickets`（只存 SHA-256 摘要、5 分钟 TTL、`PESSIMISTIC_WRITE` 一次性消费），绑定关系存在 `user_accounts.openid`（唯一约束，并发绑同一 openid 由 `DataIntegrityViolationException` 兜底成 400）。
- **登录/绑定限速**：`LoginRateLimiter` 按 `IP|小写用户名` 统计，窗口 10 分钟（`MAX_ATTEMPTS=5`）失败 5 次后返回 HTTP `429`（`RateLimitedException`），成功登录/绑定/注册即清零。计数落在 `auth_login_attempts` 表，重启与多实例共享同一窗口。测试/脚本注意别触发。
- **演示数据仅 dev/test 播种**：`DemoDataConfig` 无 active profile 时视为 dev；生产（如 `prod` profile）不创建 `admin/admin123` 与演示物品，否则被审计判为默认口令风险。生产空库由 `ProductionBootstrapConfig`（`@Profile("prod")`）引导首个管理员：需临时设置 `WMS_BOOTSTRAP_ADMIN_USERNAME` 与 **至少 12 位**的 `WMS_BOOTSTRAP_ADMIN_PASSWORD`，缺失则启动直接失败；建号成功后应立即移除该引导密码。
- **权限注意**：`POST /stock/in/scan`、`/stock/out/scan` 要求 `inventory:scan`（`hasAuthority`，不再是 `hasRole('ADMIN')`；该权限目前只在 ADMIN 权限集里，故行为仍是仅管理员）；WAREHOUSE 扫码出入库页会显示"仅管理员"提示条，实际只能走单据流程与盘点录入。`GET /logs` 需 `log:view`（ADMIN 与 AUDITOR 有），日志只由 `OperationLogAspect` 写入，无手动写入端点（防审计伪造）；单据取消/反审/红冲需对应的 `*:review` 权限（ADMIN 与 AUDITOR）。
- 小程序页面字段与 API 对齐要点：盘点单 `stocktakeNo/createdAt/lines[].bookQuantity`；单据 `businessDate`、line 用 `unitPrice`、无汇总字段（前端自算）；调拨字段 `sourceWarehouseId/sourceLocationCode/targetLocationCode`；`GET /items` 返回 `{total,records}` 分页对象而非数组。
- 调拨单无 `GET /transfers/{id}` 详情接口，小程序详情页从列表缓存（storage `wms_transfer_detail`）读取。

## 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN |
| `operator` | `operator123` | WAREHOUSE |

登录 `POST /auth/login` → `data.token` 写入 `localStorage('wms_token')`，请求头 `Authorization: Bearer <token>`；登录响应还带 `role`、`permissions`、`expiresIn:43200`。

- **Token 已持久化，不再是内存 Map**：`TokenService` 把会话写入 `auth_sessions` 表，库里只存 token 的 **SHA-256 摘要**（明文 token 只在响应里出现一次），有效期 12h，取号时顺带清理过期行。`POST /auth/logout` 撤销当前会话；`PUT /auth/users/{id}` 保存后会 `revokeByUsername` 踢掉该用户**全部**会话；被禁用（`enabled=false`）的用户下次解析即失效。
- 演示只播种 `ADMIN`/`WAREHOUSE` 两个账号，但 `RolePermissions` 实际定义了 **7 个角色**：`ADMIN`、`WAREHOUSE`、`PROCUREMENT`、`AUDITOR`、`FINANCE`、`CUSTOMER_SERVICE`、`CUSTOMER`（小程序买家由 `wx-register` 开户）。`GET /auth/permissions` 可查完整矩阵。

## 关键架构约束

- **统一响应壳**：`ApiResponse<T>(code, message, data)`，前端 `client.js` 仅在 `code === 200` 时 `resolve(data)`，其它走 `reject(message)`。
- **鉴权是 RBAC 权限字符串 + 双保险**：`SecurityConfig` 带 `@EnableMethodSecurity`，`TokenAuthenticationFilter` 把 `ROLE_<角色>` 和 `RolePermissions.forRole()` 展开的权限（`document:review`、`inventory:scan`、`user:manage` 这种 `资源:动作`）一起作为 authorities 写进 `SecurityContext`；Controller 侧 `@PreAuthorize("hasAuthority('x:y')")`，Service 侧再 `SecurityUtils.require(Permissions.X)` 兜底。常量表在 `security/Permissions.java`、角色矩阵在 `security/RolePermissions.java`，**加接口时两边必须对齐**。
- **不要按 `role == "ADMIN"` 判权限**：`SecurityUtils.require` 抛 `AccessDeniedException` → HTTP 403，未带/无效 token → 401。`ensureAdmin()` 只剩三处私有辅助（`WarehouseController`→`warehouse:manage`、`OperationLogController`→`log:view`、`AuthController`→`user:manage`），命名是历史遗留，实际校验的是权限；服务层已不存在 `"ADMIN"` 字面量比较。
- WAREHOUSE 有各业务的 `*:read/write/execute` 但**没有** `*:review`，因此可建草稿、执行已审核单据，不能审核/取消/反审/红冲，也没有 `inventory:scan`、`user:manage`、`warehouse:manage`、`log:view`。
- **免鉴权路径只有这些**（`SecurityConfig.filterChain`）：`/auth/login`、`/auth/wx-login`、`/auth/wx-bind`、`/auth/wx-register`、`/health`、`/health/**`、`/market/pay/notify`、`/market/pay/refund-notify`，dev 下再加 `/h2-console/**`；其余 `anyRequest().authenticated()`。另有第二条链 `managementPortChain`（最高优先级）只在 `localPort == management.server.port` 时放行，即**仅管理端口的 actuator**，未配置独立管理端口时其匹配器恒 false、等于不存在。**商城浏览类 `/market/**` 同样需要 token**（`Permissions` 里"未登录浏览走 permitAll"的注释与代码不一致，以 `SecurityConfig` 为准）。两个支付回调无鉴权，靠 APIv3 密钥验签/解密，改动时别顺手加业务校验以外的信任。
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
- **审计日志保留与归档**（`service/AuditArchiveService`）：`audit.archive.after-days`（默认 365）是保留期，每日 `audit.archive.cron`（默认 `0 30 3 * * *`）把 `operationAt` 早于保留期、主键大于水位的行按批（`batch-size`）导出成 `${audit.archive.dir:-logs/archive}/operation_logs-<时间戳>.csv`，水位写在同目录 `.operation_logs.watermark`。**默认只归档不删**；删除要显式 `audit.archive.delete-enabled=true`（`AUDIT_ARCHIVE_DELETE_ENABLED`），且只删本轮已 fsync 落盘的批次。扫描按主键游标而不是 offset 翻页。多实例部署前只在一个实例开 `audit.archive.enabled`（A7 调度锁未做）；CSV 对 `= + - @` 开头的字段做了防公式注入处理。测试 profile 关掉自动归档，用例直接调 `runOnce()`。

## 状态机补充（新增单据）

- `AdjustmentOrder`（报损/报溢）：`DRAFT → APPROVED → COMPLETED`（`LOSS` 减库存、`GAIN` 加库存，均走 `InventoryService.adjust` → `loss_out`/`gain_in` 流水）。
- `Document` 追加类型：`RETURN_IN`（客户退货入库，前缀 THI）、`RETURN_OUT`（退回供应商出库，前缀 THO）。
- **反审** `POST /documents/{id}/uncomplete`：需 `document:review`（ADMIN、AUDITOR），`COMPLETED → APPROVED`，自动生成 `reverse` 流水冲销。
- **红冲** `POST /documents/{id}/reverse`：需 `document:review`（ADMIN、AUDITOR），生成类型互反、`documentNo+".V"` 的新单据并直接置为 `APPROVED`，原单保留。

## 接口速查（实际前缀 `/api/v1`）

- `auth`：免鉴权 `POST /login`、`POST /wx-login`、`POST /wx-bind`、`POST /wx-register`；需 token `GET /me`、`GET /permissions`（角色矩阵）、`POST /logout`；需 `user:manage` `GET /users`、`POST /users`、`PUT /users/{id}`（改完会踢掉该用户所有会话）。
- `documents/transfers/stocktakes`：CRUD + `/review` + `/complete`（盘点还有 `/count`）。
- `stock/in/scan` / `stock/out/scan`：直写库存，不走单据；需 `inventory:scan`（仅 ADMIN 权限集）。
- `inventory`：`GET /`、`GET /transactions`、`GET /warehouses`、`GET /{itemId}`。
- `items`：`GET /`（page/pageSize/keyword）、`GET /{id}`、`GET /code/{code}`、`POST/PUT/DELETE`、`GET /categories`。
- `partners`：`GET ?type=SUPPLIER|CUSTOMER`，`POST/PUT/DELETE`。
- `warehouses`：`GET ?includeDisabled=true`、`POST/PUT`（管理员）。
- `qrcodes`：`GET /items/{code}`（返回 base64 PNG）、`GET /items/{code}/png`（图片字节流）。
- `excel`：`GET /items/export`、`POST /items/import`（multipart，列名见 DEVELOPMENT.md）。
- `reports`：`/dashboard`、`/stock-alert`、`/profit`、`/anomalies`、`/inventory-age`（库龄按 0-30/30-60/60-90/>90 分桶）、`/in-out-summary?period=YYYY-MM`（收发存汇总）。
- `adjustments`：`GET/POST /`、`POST /{id}/review`、`POST /{id}/complete`（报损/报溢）。
- `documents` 新增：`POST /{id}/uncomplete`（反审）、`POST /{id}/reverse`（红冲）；`/documents` 支持 `RETURN_IN`/`RETURN_OUT` 类型。
- `logs`：`GET /`（需 `log:view`，按 username/action/result/from/to 过滤）。**返回服务端分页对象 `{records,total,page,pageSize}` 而非数组**，`page` 从 **1** 起算、`pageSize` 钳制在 1..200；只有 `OperationLogAspect` 写日志，无手动写入端点（防审计伪造）。
- `ocr/recognize`：由 `ocr.mock` 开关驱动（`application.yml` 默认 `${OCR_MOCK:true}`，`application-prod.yml` 强制 `false`）。开 mock 时返回 `source: "mock"` 的假识别结果，关 mock 时直接抛 `BusinessException` → HTTP 400（"OCR 服务尚未配置"）。需 `ocr:use`（ADMIN、WAREHOUSE），**不要在此接口上构建依赖真实识别的功能**。

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

- `docker-compose.yml` 仅 MySQL + `wms-server` + `wms-web` 三个服务：Web `3000`、API `8088`、management `9089`、MySQL `3306`，**默认全部只绑 `127.0.0.1`**（`WMS_WEB_BIND`/`WMS_API_BIND`/`WMS_MGMT_BIND`/`WMS_MYSQL_BIND` 覆盖），公网访问须由 HTTPS 反向代理转发。服务端日志落命名卷 `wms-logs`（容器内 `/app/logs`），审计归档 CSV 也写在其中的 `archive/`（compose 显式设 `AUDIT_ARCHIVE_DIR=/app/logs/archive`），备份策略要一并覆盖，否则"打开删除"等于永久丢数据。**README.md 描述的 Redis/MinIO/Nginx 在仓库里没有对应服务或 pom 依赖**，要按需自行补；两个小程序端是仓库内的源码工程（`wms-miniapp/`、`wms-shopping-miniapp/`），不在 compose 里跑，需自行用微信开发者工具构建上传。
- 商户私钥/平台公钥走 docker secrets（`WECHAT_PAY_PRIVATE_KEY_FILE`/`WECHAT_PAY_PUBLIC_KEY_FILE` 指向 `./secrets/*.pem`），**不要提交 `.env` 与 `secrets/` 下的 PEM**。
- `wms-server/Dockerfile` 多阶段：maven 3.9 + temurin-21 构建 → `eclipse-temurin:21-jre` 运行。
- `wms-web/Dockerfile` 多阶段：node 20-alpine 构建 → nginx:alpine，使用 `wms-web/nginx.conf`。
- MySQL 凭据**没有默认值**：`MYSQL_ROOT_PASSWORD`/`MYSQL_USER`/`MYSQL_PASSWORD` 均为 `${VAR:?...}`，必须写在仓库根 `.env`（模板 `.env.example`），缺失时 `docker compose` 直接报错拒绝启动。不存在"root 密码默认 `wms_password`"这回事。

## 改动后建议跑

```bash
cd wms-server && mvn test                                        # 15 个测试类；动过 entity 或迁移脚本还要启动一次默认 profile
cd ../wms-web && npm run build
cd ../wms-miniapp && npm run build:mp-weixin
cd ../wms-shopping-miniapp && npm run build:wechat
python3 test-artifacts/run_api_regression.py http://localhost:8088/api/v1   # 后端跑起来后打真实链路
```

- **判断 `mvn test` 成败不要用管道**：`mvn test 2>&1 | tail -60` 的退出码是 `tail` 的，测试全红也会返回 0。写成 `mvn -o test > /tmp/t.log 2>&1; echo EXIT=$?` 再看日志。
- **后端返回形状变更（如 `GET /logs` 从数组改成 `{records,total,page,pageSize}`）三个前端都要跟**：`wms-web` 的页面/`api/wms.js`、两个小程序的调用点。Vue 侧不会在构建期报错，只有打开页面才暴露，别只跑 `wms-web` 的 build 就当改完了。

需要实战跑通链路：先 `mvn spring-boot:run`，再 `npm run dev`，浏览器登录 admin 后改菜单 → 复核接口 → 单测。