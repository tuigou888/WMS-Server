# 开发与运行指南

本项目是按 `README.md` 的仓储进销存场景实现的前后端分离系统：后端为 Spring Boot 3 / JPA，前端为 React / Vite / Ant Design。默认使用 H2 内存数据库，以便开箱体验；也支持通过环境变量切换 MySQL。

> 本文不替代 `README.md` 的业务说明；仅记录当前已落地的模块、开发启动方式和接口约定。

## 已实现模块

- **认证与权限**：账号登录、退出、当前用户、用户维护；密码使用 BCrypt，接口使用 `Bearer Token`。
  - `ADMIN`：可维护用户和仓库，审核业务单据、调拨单、盘点单。
  - `WAREHOUSE`：可创建草稿、完成已审核单据、执行已审核调拨和盘点。
- **基础资料**：物品、分类、供应商/客户/双向往来单位、仓库维护。
- **入库/出库单**：草稿、管理员审核（通过/驳回）、执行、取消；入库单仅允许选择供应商，出库单仅允许选择客户，`BOTH` 可用于两种单据。
- **库存业务**：扫码入库/出库、跨仓跨库位调拨、盘点快照与差异调整。
- **成本与报表**：移动加权平均成本、库存流水、库存预警、利润报表与仪表盘、**库龄分析**、**收发存汇总**。
- **报损/报溢**：独立的 `AdjustmentOrder`，支持 `LOSS`（报损）与 `GAIN`（报溢），走草稿→审核→执行流程。
- **退货单**：`RETURN_IN`（客户退货入库）与 `RETURN_OUT`（退回供应商出库），独立单据号前缀 `THI`/`THO`。
- **反审与红冲**：已 `COMPLETED` 单据可反审（`COMPLETED→APPROVED`，自动生成反向库存流水）或红冲（复制一张反向单据，原单据保留执行记录）。
- **单据号持久化**：基于 `document_sequences` 表 + 行锁取号，重启不重置。
- **操作日志**：AOP 切面自动记录所有控制器操作到 `operation_logs` 表，管理员可在 `/logs` 页面查询。
- **盘点过滤**：创建盘点单时可指定 `itemCodes` / `locationCodes` 进行部分盘点。
- **工具**：物品二维码（PNG 或 Base64 Data URL）、物品档案 Excel 导入/导出。

## 本地启动

### 后端

```bash
cd wms-server
mvn spring-boot:run
```

默认 API 地址为 `http://localhost:8088/api/v1`，健康检查为 `GET /api/v1/health`。

开发环境首次启动会自动写入：主仓库、3 个物品、初始库存和演示用户。H2 为内存库，服务重启后会重建演示数据；已登录 Token 也会失效。

### 前端

```bash
cd wms-web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 已将 `/api/v1` 代理至 Spring Boot 服务。

### 使用 MySQL

```bash
export DB_URL='jdbc:mysql://localhost:3306/wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME=root
export DB_PASSWORD=your_password
export DB_DRIVER=com.mysql.cj.jdbc.Driver
cd wms-server && mvn spring-boot:run
```

也可在根目录执行：

```bash
docker compose up --build
```

容器服务端口为 Web `3000`、API `8088`、MySQL `3306`。

## 演示账户与认证

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 管理员 |
| `operator` | `operator123` | 仓库操作员 |

登录：

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"admin","password":"admin123"}
```

响应中的 `data.token` 放入后续请求头：

```http
Authorization: Bearer <token>
```

Token 在当前服务进程内保存，有效期为 12 小时；退出登录或服务重启后失效。主要接口：

- `POST /auth/login`、`GET /auth/me`、`POST /auth/logout`
- `GET/POST/PUT /auth/users`（管理员）

除 `/auth/**`、`/health` 和 H2 Console 外，所有 API 都需要登录。未携带 Token 时返回 HTTP `401`。

## 业务状态流

### 入库单 / 出库单

```text
DRAFT -> APPROVED -> COMPLETED
DRAFT -> REJECTED
DRAFT -> CANCELLED
```

- 新建草稿：`POST /documents`
- 审核：`POST /documents/{id}/review`，请求体为 `{"action":"APPROVE|REJECT","remark":"..."}`
- 执行：`POST /documents/{id}/complete`
- 取消：`POST /documents/{id}/cancel`

执行完成才会写入库存与库存流水，流水 `referenceNo` 为单据号。入库采用移动加权平均成本，出库按当前平均成本结转。

### 调拨

```text
DRAFT -> APPROVED -> COMPLETED
DRAFT -> REJECTED
```

- `GET/POST /transfers`
- `POST /transfers/{id}/review`
- `POST /transfers/{id}/complete`

调拨必须选择不同的调出、调入仓库。执行后会写入一条 `transfer_out` 和一条 `transfer_in` 流水，使用同一调拨单号关联；调入库存继承调出库存的平均成本。前端“库存调拨”页面可由管理员直接新增启用仓库。

### 盘点

```text
DRAFT -> APPROVED -> COMPLETED
DRAFT -> REJECTED
```

- `GET/POST /stocktakes`：创建时按目标仓库生成账面库存快照。
- `POST /stocktakes/{id}/count`：提交所有盘点行的 `itemCode`、`locationCode`、`actualQuantity`。
- `POST /stocktakes/{id}/review`、`POST /stocktakes/{id}/complete`。

完成时系统将库存调整为实盘数量，生成 `adjust_in` 或 `adjust_out` 流水。

## 主要接口速查

| 模块 | 接口 |
| --- | --- |
| 物品与分类 | `GET/POST/PUT/DELETE /items`、`GET /items/categories` |
| 供应商/客户 | `GET/POST/PUT/DELETE /partners`，可用 `?type=SUPPLIER|CUSTOMER` 筛选 |
| 仓库 | `GET /warehouses`、`POST /warehouses`、`PUT /warehouses/{id}`；`includeDisabled=true` 可查看停用仓库 |
| 扫码出入库 | `POST /stock/in/scan`、`POST /stock/out/scan` |
| 库存 | `GET /inventory`、`GET /inventory/transactions`、`GET /inventory/warehouses` |
| 报表 | `GET /reports/dashboard`、`GET /reports/stock-alert`、`GET /reports/profit`、`GET /reports/inventory-age`、`GET /reports/in-out-summary` |
| 报损/报溢 | `GET/POST /adjustments`、`POST /adjustments/{id}/review`、`POST /adjustments/{id}/complete` |
| 退货单 | `POST /documents`（type=`RETURN_IN`/`RETURN_OUT`） |
| 反审/红冲 | `POST /documents/{id}/uncomplete`、`POST /documents/{id}/reverse` |
| 操作日志 | `GET /logs` |
| 二维码 | `GET /qrcodes/items/{code}`、`GET /qrcodes/items/{code}/png` |
| Excel | `GET /excel/items/export`、`POST /excel/items/import` |

## Excel 物品档案格式

导出和导入均使用首个 `.xlsx` 工作表，第一行列名如下：

```text
物品编码 | 物品名称 | 分类 | 单位 | 规格型号 | 安全库存 | 最小库存 | 最大库存 | 备注
```

导入以“物品编码”为唯一键：已存在物品会更新，不存在物品会创建；分类不存在时会自动创建。编码或名称为空的行会跳过。

## 验证命令

```bash
cd wms-server && mvn test
cd ../wms-web && npm run build
```

前端构建可能输出 Ant Design 主包体积警告，不影响构建结果。
