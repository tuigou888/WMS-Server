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

### 微信小程序开发工具编译

`wms-miniapp` 使用 uni-app，微信开发者工具只能打开 uni 编译后的原生小程序目录，不能直接编译 `src/` 源码目录：

```bash
cd wms-miniapp
npm install
npm run build:mp-weixin
```

本机联调时先启动后端，再构建带本机 API 地址的调试包：

```bash
cd wms-server
mvn spring-boot:run

cd ../wms-miniapp
npm run build:mp-weixin:local
```

该命令只用于 Linux 本机调试，会把 `http://127.0.0.1:8088/api/v1` 写入小程序构建产物；生产发布必须继续使用 `build:mp-weixin:release` 并传入 HTTPS 的 `VITE_API_BASE`。本机登录页默认显示账号密码登录与测试账户：管理员 `admin / admin123`，仓库操作员 `operator / operator123`。

导入本机构建产物后，在微信开发者工具的“详情 → 本地设置”勾选“不校验合法域名、Web-view（业务域名）、TLS 版本以及 HTTPS 证书”；该设置仅用于本地 HTTP 联调，正式包必须使用已登记的 HTTPS 合法域名。

编译完成后，在微信开发者工具中打开 `wms-miniapp/dist/build/mp-weixin`。该目录必须包含 `app.json`、`app.js` 和 `project.config.json`。为兼容 Linux 移植版工具的资源路径解析，当前 tabBar 使用文字导航，不在 `app.json` 中引用图片路径；`npm run build:mp-weixin` 仍会校验未来新增的 tabBar 图片引用。

针对 Linux 版微信开发者工具 v2.02 及以上版本，项目已在 `project.config.json` 和 uni-app `manifest.json` 中关闭 SWC 编译（`swc: false`、`disableSWC: true`），以避免工具因缺少 Linux SWC 原生绑定而报错。修改配置后需要完全退出并重新打开开发者工具，再重新导入编译产物。

Linux 移植版工具的旧版 `wcsc` 还可能无法编译 uni-app 自动追加到 `app.wxss` 的 CSS 自定义属性（例如 `--status-bar-height`），从而显示 `simulator launch failed`、`timeout` 或“编译 `.wxss` 文件错误”。`vite.config.js` 已在微信小程序构建阶段移除本项目未使用的这组运行时变量；请重新执行 `npm run build:mp-weixin`，不要直接复用旧的 `dist/build/mp-weixin` 缓存。

如果出现 `Failed to load native binding getDevCodeByFileList-miniProgram`，先确认 `npm run build:mp-weixin` 本身成功。该错误发生在开发者工具内部编译器加载原生模块阶段，通常不是项目代码错误，尤其常见于 Linux 非官方移植版工具缺少 Linux 原生 SWC 绑定。处理顺序如下：

1. 关闭微信开发者工具，删除并重新导入 `dist/build/mp-weixin` 项目，避免继续使用旧的编译缓存。
2. 确认编译产物的 `project.config.json` 中存在 `"swc": false` 和 `"disableSWC": true`；若仍失败，再更新或重装包含 `@swc/core-linux-x64-gnu`（或对应架构）绑定的 Linux 工具。
3. 若工具仍未读取项目配置，可在开发者工具的项目设置中关闭 SWC，或使用 Linux 版工具的最新稳定/持续构建版本。

如果日志仍出现“编译 `.wxss` 文件错误”，先检查编译产物的 `app.wxss` 中不应再出现 `--status-bar-height`、`--window-top` 等变量。页面级 `.wxss` 仍保留正常的 scoped 样式；此兼容处理只针对 Linux 工具编译器，不影响后端接口和业务逻辑。

当前 Linux 工具内置的 `wcsc` 还不支持全局样式中的通配子选择器，例如 `.row > *`、`.row > *:first-child`；会以 `WXSS GetCompiledResult`、`error file count: -1` 或 `0` 的形式失败。项目已移除这两条未使用的规则。若后续新增全局布局样式，请为子元素显式添加类名，不要使用 `> *`。

如果仍出现 `tabBar` 图标未找到，说明开发者工具正在使用旧产物：重新运行构建，完全退出工具后重新导入最新的 `dist/build/mp-weixin` 目录。当前源码已移除图片路径引用，不应再生成 `iconPath` 或 `selectedIconPath`。

如果控制台出现 `webapi_getwxaasyncsecinfo:fail`、`appServiceSDKScriptError`，先确认小程序页面是否已经正常打开。当前仓储小程序代码没有调用该接口；由于 `src/manifest.json` 未填写真实微信 AppID，开发者工具会以 `touristappid` 游客模式运行，工具内部安全信息接口可能产生这条红色日志。页面和普通业务接口正常时可以忽略。需要消除该日志时，在 `mp-weixin.appid` 填写真实 AppID，并确保开发者工具项目 AppID 一致，重新执行构建后导入；不要把 AppSecret 写入前端项目。

登录输入框在小程序端使用固定 `44px` 高度和行高，避免 Linux 模拟器将用户名、密码或占位文字垂直裁切；字段标签始终显示在输入框上方。

登录成功后会优先使用微信原生 `wx.reLaunch('/pages/index/index')`，避免 Linux 移植版中 uni 导航包装层异常；若失败再回退到 `switchTab`。若两个导航 API 都失败，控制台会输出具体错误并提示“登录成功，但首页打开失败”。

登录页还提供固定在底部的“进入首页”原生导航兜底按钮（`navigator` + `open-type="reLaunch"`）。如果 Linux 模拟器未自动切页但已显示“登录成功，正在打开首页”，可直接点击该按钮；这也可用于确认当前开发者工具已加载最新构建产物。

Linux 移植版模拟器在部分网络请求中会把 JSON 响应作为字符串放入 `res.data`。统一请求层会先安全解析字符串 JSON，再判断 `code/message/data`，避免接口已返回 `code: 200` 但登录状态没有写入、页面没有跳转。

### 使用 MySQL

```bash
export DB_URL='jdbc:mysql://localhost:3306/wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME=root
export DB_PASSWORD=your_password
export DB_DRIVER=com.mysql.cj.jdbc.Driver
cd wms-server && mvn spring-boot:run
```

根目录的 Compose 面向生产化部署：它固定启用 `prod` Profile，要求 `.env` 中提供独立应用数据库账号及微信支付真实参数，不会生成演示数据。先复制模板再执行：

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

容器默认将 Web `3000`、API `8088`、MySQL `3306` 绑定到宿主机 `127.0.0.1`；正式公网访问必须通过 HTTPS 反向代理。

Compose 的 `prod` Profile 使用 Flyway 和 `ddl-auto=validate`。全新库会依次执行 `V1` 基线、`V2` 库存预占/冲销追溯、`V3` 历史 `reverse` 类型规范化和 `V4` 认证状态持久化；已有 Hibernate 管理的库会先基线为 V1，再执行后续迁移。上线前必须完成数据库备份、恢复抽检，并运行只读对账脚本留存结果：

```bash
WMS_DB_HOST=<host> WMS_DB_PORT=3306 WMS_DB_NAME=wms \
WMS_DB_USER=<app-user> WMS_DB_PASSWORD='<password>' \
WMS_BACKUP_DIR=./backups ./ops/backup-mysql.sh

WMS_DB_HOST=<host> WMS_DB_PORT=3306 WMS_DB_NAME=wms \
WMS_DB_USER=<app-user> WMS_DB_PASSWORD='<password>' \
WMS_BACKUP_FILE=./backups/<backup>.sql.gz \
WMS_CONFIRM_RESTORE=YES ./ops/restore-mysql.sh

WMS_DB_HOST=<host> WMS_DB_PORT=3306 WMS_DB_NAME=wms \
WMS_DB_USER=<app-user> WMS_DB_PASSWORD='<password>' \
./ops/reconcile-mysql.sh
```

对账出现差异时停止上线，按单据和库存流水人工确认；脚本不会擅自修改历史数量或金额。

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
