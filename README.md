# WMS 仓库进销存管理系统

一套前后端分离的仓库进销存（WMS）系统：覆盖物品档案、采购入库、销售出库、库存调拨、盘点、报损报溢、退货、反审/红冲、移动加权平均成本核算、报表看板、操作审计，以及面向买家的小程序商城（含微信支付）。

- **后端**：Spring Boot 3.3 / Java 21 / Spring Data JPA / Spring Security + Flyway，默认 H2 内存库，可切换 MySQL
- **Web 管理后台**：Vue 3 + Vite 6 + Ant Design Vue 4 + Pinia + ECharts
- **仓储作业小程序**（`wms-miniapp`）：uni-app + Vue 3 + Pinia，扫码出入库、盘点、调拨、报表
- **买家商城小程序**（`wms-shopping-miniapp`）：uni-app + Vue 3，商品浏览、购物车、下单、微信支付、收货

> 配套文档：[DEVELOPMENT.md](DEVELOPMENT.md)（模块与接口细节）、[AGENTS.md](AGENTS.md)（架构约束与踩坑速查）、[PRODUCTION_READINESS.md](PRODUCTION_READINESS.md)（生产化整改清单）。

## 功能总览

| 模块 | 说明 |
| --- | --- |
| 认证与权限 | 账号密码登录、微信登录/绑定/开户；DB 持久化 Bearer Token（12h）；RBAC 7 角色权限矩阵（`资源:动作` 字符串） |
| 基础数据 | 物品档案（二维码、Excel 导入导出）、分类、往来单位（供应商/客户/双向）、仓库与库位 |
| 出入库单据 | 草稿 → 审核 → 执行状态机；入库限供应商、出库限客户；支持 `RETURN_IN`/`RETURN_OUT` 退货单 |
| 扫码作业 | 小程序扫码直接出入库（`inventory:scan` 权限，仅 ADMIN）；库位、批次、实时成本与利润 |
| 调拨 | 跨仓库调拨（调出/调入必须不同仓库），写入成对 `transfer_out`/`transfer_in` 流水 |
| 盘点 | 账面快照 → 录入实盘 → 差异生成 `adjust_in`/`adjust_out` 流水并调整库存；支持按物品/库位部分盘点 |
| 报损报溢 | 独立 `AdjustmentOrder`（`LOSS` 减库存 / `GAIN` 加库存） |
| 反审与红冲 | `COMPLETED` 单据可反审（生成反向流水）或红冲（生成 `.V` 互反单据） |
| 库存预占 | 商城下单预占库存（`InventoryReservationGuard`），冲销追溯由 Flyway V2+ 迁移支撑 |
| 成本核算 | 移动加权平均成本（金额 2 位、成本 4 位小数，`HALF_UP`）；出库按当前均价结转 |
| 商城（Market） | 小程序端商品/购物车/订单/收藏/收货；管理端商品上架、订单审核/发货/退款/统计；微信支付 APIv3 + 回调验签 + 幂等控制 |
| 报表 | 仪表盘、库存预警、销售利润、库龄分析、收发存汇总 |
| 审计运维 | AOP 操作日志（`REQUIRES_NEW` 落库）、日志归档 CSV + 水位、Prometheus 指标（独立管理端口）、traceId 日志链路 |

## 仓库结构

```
WMS-SERVER/
├── wms-server/              # Spring Boot 后端（API 前缀 /api/v1，端口 8088）
├── wms-web/                 # Vue 3 管理后台（dev 5173）
├── wms-miniapp/             # 仓储作业端小程序（uni-app）
├── wms-shopping-miniapp/    # 买家商城端小程序（uni-app）
├── test-artifacts/          # Python API/UI 回归脚本
├── ops/                     # 备份/恢复/对账/发布检查脚本
├── docs/                    # 架构与商城子系统设计文档
├── docker-compose.yml       # MySQL + wms-server + wms-web
└── .env.example             # Compose 必填变量模板
```

### 后端分层

```
wms-server/src/main/java/com/wms/
├── controller/    # REST 控制器（auth/items/documents/transfers/stocktakes/
│                  #   stock/inventory/warehouses/partners/reports/adjustments/
│                  #   qrcodes/excel/logs/ocr/locations/purchase-requests/market）
├── service/       # 业务逻辑与状态机（InventoryService、InventoryCostCalculator、
│                  #   DocumentNumberService、AuditArchiveService、market/ 商城与微信支付）
├── model/entity/  # JPA 实体（Inventory 唯一键 item+warehouse+location+batchNo）
├── repository/    # Spring Data JPA（悲观锁 findForUpdate 等）
├── dto/           # 请求/响应 DTO
├── security/      # SecurityConfig、TokenService、Permissions/RolePermissions、幂等切面
├── aspect/        # OperationLogAspect 操作日志
└── config/        # 演示数据、生产引导、TraceId 等配置
```

## 快速开始

### 后端（默认 H2，开箱即用）

```bash
cd wms-server
mvn spring-boot:run        # http://localhost:8088/api/v1
```

- 首次启动由 `DemoDataConfig` 自动播种演示用户、物品与初始库存；**H2 为内存库，重启即重置**（Token 一并失效）。
- 健康检查：`GET /api/v1/health`（免鉴权）；`GET /api/v1/health/ready` 会真实探活数据库。
- 切换 MySQL：

```bash
export DB_URL='jdbc:mysql://localhost:3306/wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME=root DB_PASSWORD=your_password DB_DRIVER=com.mysql.cj.jdbc.Driver
cd wms-server && mvn spring-boot:run
```

> 生产（prod profile）由 Flyway 管理 schema（`db/migration/V1..V9`），`ddl-auto=validate`；索引变更只能通过迁移脚本，且需同时兼容 MySQL 8 与 H2 `MODE=MySQL`。

### Web 管理后台

```bash
cd wms-web
npm install
npm run dev        # http://localhost:5173，/api/v1 已代理到 :8088
npm run build      # 产物 dist/
```

### 小程序端

两个小程序均为 uni-app 工程，**appid 当前相同，上线前必须分别申请并填写**。

```bash
# 仓储作业端
cd wms-miniapp
npm install
npm run build:mp-weixin          # 产物 dist/build/mp-weixin

# 买家商城端（注意脚本名不同）
cd ../wms-shopping-miniapp
npm install
npm run build:wechat             # 产物同样是 dist/build/mp-weixin
```

用微信开发者工具导入对应 `dist/build/mp-weixin`（本地 HTTP 联调需勾选"不校验合法域名"）。微信登录本地联调需 `export WECHAT_MOCK=true`（此时任意 code 可当 openid 用）。

发布前运行各端 `npm run check:release`（校验 appid 非空且 `VITE_API_BASE` 为 HTTPS）。

### Docker Compose 部署（生产）

```bash
cp .env.example .env             # MySQL 口令与微信支付参数全部必填（${VAR:?} 缺失即拒绝启动）
mkdir -p secrets                 # 放入 secrets/apiclient_key.pem、secrets/wechatpay_public_key.pem
docker compose --env-file .env up --build -d
```

- 服务与端口：Web `3000`、API `8088`、Actuator `9089`（仅 health/info/prometheus）、MySQL `3306`；**默认全部只绑 127.0.0.1**，公网访问必须经 HTTPS 反向代理。
- `prod` profile 禁用 H2 Console、演示数据、微信登录/支付 mock；空库首次启动需临时设置 `WMS_BOOTSTRAP_ADMIN_USERNAME` 与至少 12 位的 `WMS_BOOTSTRAP_ADMIN_PASSWORD`，启动成功后立即从 `.env` 删除。
- 上线前建议执行 `ops/` 下的 `backup-mysql.sh`、`restore-mysql.sh`、`reconcile-mysql.sh` 完成备份演练与只读对账。

## 演示账号（仅 dev/test 播种）

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN |
| `operator` | `operator123` | WAREHOUSE |

```http
POST /api/v1/auth/login
{"username":"admin","password":"admin123"}

# 后续请求头
Authorization: Bearer <token>
```

完整角色为 7 个：`ADMIN`、`WAREHOUSE`、`PROCUREMENT`、`AUDITOR`、`FINANCE`、`CUSTOMER_SERVICE`、`CUSTOMER`（买家由 `POST /auth/wx-register` 开户）。权限矩阵可通过 `GET /api/v1/auth/permissions` 查看。

## 业务状态机

```
入库/出库单:  DRAFT → APPROVED → COMPLETED        （可 REJECTED / CANCELLED；已执行不可取消）
调拨单:       DRAFT → APPROVED → COMPLETED        （调出/调入仓库必须不同）
盘点单:       DRAFT →(录入实盘)→ APPROVED → COMPLETED（自动生成 adjust_in/out 流水）
报损报溢:     DRAFT → APPROVED → COMPLETED        （LOSS 减库存 / GAIN 加库存）
```

- 执行（`POST /{id}/complete`）是唯一写库存与流水的时机；所有库存变更统一走 `InventoryService`，流水只追加、不修改，冲销通过反向流水/红冲单完成。
- 单据号由 `DocumentNumberService` 基于持久化 `document_sequences` 表 + 行锁生成，重启不重号。
- 反审 `POST /documents/{id}/uncomplete`、红冲 `POST /documents/{id}/reverse` 均需 `document:review`（ADMIN/AUDITOR）。

## 成本与利润

- **移动加权平均**：入库后 `新均价 = (库存原值 + 入库金额) ÷ (原数量 + 入库数量)`；出库按当前均价结转，不改变均价。精度：成本 4 位小数、金额 2 位，`HALF_UP`。
- **利润** = 销售金额（数量 × 售出价）− 出库成本金额（数量 × 当时均价），报表与扫码出库页实时计算。

## 接口速查（实际前缀 `/api/v1`）

| 模块 | 接口 |
| --- | --- |
| 认证 | `POST /auth/login`、`POST /auth/wx-login`、`POST /auth/wx-bind`、`POST /auth/wx-register`、`GET /auth/me`、`GET /auth/permissions`、`POST /auth/logout`、用户管理 `GET/POST/PUT /auth/users` |
| 单据 | `GET/POST /documents`、`POST /documents/{id}/review`、`/complete`、`/cancel`、`/uncomplete`（反审）、`/reverse`（红冲） |
| 调拨 | `GET/POST /transfers`、`/review`、`/complete` |
| 盘点 | `GET/POST /stocktakes`、`/count`（录入实盘）、`/review`、`/complete` |
| 扫码 | `POST /stock/in/scan`、`POST /stock/out/scan`（需 `inventory:scan`） |
| 库存 | `GET /inventory`、`GET /inventory/transactions`、`GET /inventory/warehouses`、`GET /inventory/{itemId}` |
| 报损报溢 | `GET/POST /adjustments`、`/review`、`/complete` |
| 基础数据 | `/items`、`/items/categories`、`/partners?type=SUPPLIER\|CUSTOMER`、`/warehouses`、`/locations` |
| 工具 | `GET /qrcodes/items/{code}`（Base64）、`GET /qrcodes/items/{code}/png`、`GET /excel/items/export`、`POST /excel/items/import` |
| 报表 | `/reports/dashboard`、`/stock-alert`、`/profit`、`/anomalies`、`/inventory-age`、`/in-out-summary?period=YYYY-MM` |
| 审计 | `GET /logs`（需 `log:view`，服务端分页 `{records,total,page,pageSize}`） |
| 商城 | `GET /market/products`、购物车 `/market/cart`、订单 `/market/orders`（`/prepay`、`/mock-pay`、`/cancel`、`/receive`）、收藏 `/market/favorites`；管理端 `/admin/market/**`（商品/订单/客户/统计）；支付回调 `/market/pay/notify`、`/market/pay/refund-notify`（免鉴权，APIv3 验签） |
| 其他 | `GET /health`、`POST /ocr/recognize`（`ocr.mock` 开关驱动）、`GET/POST /purchase-requests`（请购） |

**鉴权约定**：除登录/注册/绑定、`/health/**`、两个支付回调外，所有接口（包括 `/market/**` 浏览类）均需 Token。权限为 RBAC `资源:动作` 字符串（如 `document:review`、`inventory:scan`），Controller 侧 `@PreAuthorize` + Service 侧 `SecurityUtils.require` 双重校验；WAREHOUSE 无 `*:review`、`inventory:scan`、`user:manage` 等权限。

## 测试与验证

```bash
cd wms-server && mvn test            # 15 个测试类 / 177 个用例（含商城、微信支付、调拨、报表集成测试）
cd ../wms-web && npm run build
cd ../wms-miniapp && npm run build:mp-weixin
cd ../wms-shopping-miniapp && npm run build:wechat
sh ops/release-check.sh              # 一键发布检查（脏工作区需 RELEASE_ALLOW_DIRTY=YES）

# 后端启动后的真实链路回归（Python，依赖 requests/openpyxl）
python3 test-artifacts/run_api_regression.py http://localhost:8088/api/v1
python3 test-artifacts/run_ui_regression.py http://localhost:5173/   # 需先启动 wms-web dev
```

## 可观测性与运维

- **日志**：`logback-spring.xml` 落盘 `${WMS_LOG_DIR:-logs}/wms-server.log`（50MB/文件、30 天、2GB 上限），每行含 `[trace:<traceId>]`；`TraceIdFilter` 沿用/生成 `X-Trace-Id`。
- **指标**：Actuator 暴露 `health,info,prometheus` 于独立管理端口 `9089`（compose 绑 127.0.0.1，勿公网发布）。
- **审计归档**：每日定时把超期（默认 365 天）`operation_logs` 导出 CSV（含防公式注入处理），主键水位断点续跑；默认只归档不删，删除需显式 `AUDIT_ARCHIVE_DELETE_ENABLED=true` 且仅删本轮已落盘批次。
- **登录限速**：按 `IP|用户名` 10 分钟窗口失败 5 次返回 HTTP 429，计数持久化于 `auth_login_attempts`。
- **幂等**：商城写操作带 `@Idempotent` + `IdempotencyAspect`（`idempotent_requests` 表），防小程序重试重复下单/扣款。

## 主要技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.3.12、Java 21、Spring Data JPA、Spring Security、Spring AOP、Actuator + Micrometer Prometheus、Flyway |
| 数据库 | H2（默认/开发）、MySQL 8（生产） |
| 后端工具库 | Apache POI 5.3（Excel）、ZXing 3.5（二维码）、wechatpay-java 0.2.14（微信支付 APIv3 SDK） |
| Web 前端 | Vue 3.5、Vite 6、Ant Design Vue 4、Pinia、Vue Router、axios、dayjs、ECharts |
| 小程序 | uni-app + Vue 3 + Pinia（两个独立工程，产物均为微信小程序） |
| 测试/运维 | JUnit 5 + Spring Boot Test、Python 回归脚本、Docker Compose、Shell 运维脚本 |

> 说明：仓库当前**不包含** Redis、MinIO 或独立 Nginx 服务（早期设计文档提及，未落地），如需可自行扩展。

## 贡献指南

1. Fork / 拉取分支，遵循现有代码风格（后端为一行业务代码的紧凑写法，勿随意展开重排；金额一律 `BigDecimal` + `HALF_UP`）。
2. 新增接口时**必须同步**更新 `security/Permissions.java` 与 `security/RolePermissions.java`，不要按 `role == "ADMIN"` 判断权限。
3. 涉及 schema 变更只能新增 `db/migration/Vn__*.sql`（需兼容 MySQL 8 与 H2 `MODE=MySQL`）；`mvn test` 不验证迁移，需以默认 profile 启动一次验证。
4. 提交前运行"测试与验证"一节的命令（`mvn test` + 三个前端构建）；后端返回形状变更需同步 `wms-web` 与两个小程序的调用点。
5. 不要提交 `.env`、`secrets/` 下的 PEM 或任何真实凭据。
6. 提交 Pull Request 时附上验证结果与影响范围说明。

## License

仅供学习与内部使用，未附带开源许可证。如需对外发布请先补充相应 License 条款。
