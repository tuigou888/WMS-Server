# WMS 仓库进销存系统自动化测试报告

## 1. 测试结论

测试日期：2026-09-20（Asia/Shanghai）

当前代码在本地 H2 演示环境、Chrome Headless 和前端生产构建环境下通过全部自动化门禁：

| 测试层级 | 命令/方式 | 结果 |
| --- | --- | --- |
| 后端单元/集成测试 | `cd wms-server && mvn -q test` | 171 passed，0 failed，0 errors，0 skipped |
| HTTP API 回归 | `python3 test-artifacts/run_api_regression.py http://127.0.0.1:18088/api/v1` | 71/71 passed |
| Web UI 回归 | `python3 test-artifacts/run_ui_regression.py http://127.0.0.1:5175/` | 38/38 passed |
| Web 生产构建 | `cd wms-web && npm run build` | 通过 |
| 仓储微信小程序构建 | `cd wms-miniapp && npm run build:mp-weixin` | 通过 |
| 商城微信小程序构建 | `cd wms-shopping-miniapp && npm run build:wechat` | 通过 |

综合判断：当前自动化覆盖范围内的功能可正常使用，未发现失败用例、未捕获 JavaScript 异常或 HTTP 5xx。

## 2. 测试环境

- Java 21.0.12
- Spring Boot 3.3.12
- H2 内存数据库，测试服务端口 `18088`，上下文 `/api/v1`
- Vue 3 + Vite 6 + Ant Design Vue 4，Web 端口 `5175`
- Google Chrome Headless，通过 Chrome DevTools Protocol 驱动
- API 回归使用 `requests`、`openpyxl`
- 微信支付与 OCR 按项目约定使用 mock 模式；未调用真实微信第三方服务

## 3. 后端测试结果

Maven Surefire 共执行 14 个测试类、171 个测试用例，全部通过：

- 认证与微信登录：`AuthControllerWxTest`，4
- 报表集成：`ReportControllerIntegrationTest`，3
- 商城控制器：`MarketControllerTest`，38
- 微信支付回调：`WechatPayNotifyControllerTest`，6
- 本地 CORS：`LocalCorsIntegrationTest`，1
- 角色权限：`RolePermissionsTest`，1
- 报损/报溢：`AdjustmentIntegrationTest`，6
- 单据号：`DocumentNumberServiceTest`，1
- 库存移动加权成本：`InventoryCostCalculatorTest`，2
- 商城业务服务：`MarketServiceIntegrationTest`，90
- 微信支付服务：`WechatPayServiceTest`，11
- 生产管理员引导：`ProductionBootstrapConfigTest`，3
- 健康探针：`HealthControllerTest`，3
- OCR 能力边界：`OcrControllerTest`，2

覆盖的关键业务包括库存成本计算、报损报溢、单据号持久化、角色权限、报表历史期间重算、库存库龄、商城订单与库存预占、微信支付签名/回调和本地跨域。

## 4. API 回归结果

回归脚本共 71 项，全部通过。模块分布如下：

| 模块 | 用例数 | 主要验证内容 |
| --- | ---: | --- |
| 基础可用性 | 1 | 健康检查 |
| 认证鉴权 | 6 | 未登录拦截、错误密码、管理员/操作员登录、当前用户、退出失效 |
| 权限控制 | 4 | 操作员禁止用户管理、仓库管理、扫码出入库、单据审核 |
| 基础资料 | 2 | 演示数据、物品分类 |
| 物品档案 | 7 | 增删改查、按编码查询、分页、编码唯一性 |
| 参数校验 | 2 | 物品必填、入库数量 |
| 供应商/客户 | 7 | 供应商、客户、双类型单位、筛选、更新、删除、唯一性 |
| 仓库管理 | 4 | 仓库增改查、编码唯一性 |
| 扫码出入库 | 2 | 入库、出库、利润 |
| 库存校验 | 1 | 库存不足阻止出库 |
| 库位管理 | 3 | 全部库位、按仓库查询、无效仓库校验 |
| 库存管理 | 4 | 库存分页、按物品、库存流水、仓库列表 |
| 入出库单 | 7 | 草稿、审核、执行、详情、取消、驳回、往来单位规则 |
| 状态机校验 | 1 | 已完成单据不可重复执行 |
| 业务规则 | 2 | 往来单位类型、禁止同仓调拨 |
| 库存调拨 | 3 | 完整流转、列表、成本继承、驳回 |
| 库存盘点 | 3 | 完整流转、差异调整、列表、驳回 |
| 报表中心 | 4 | 仪表盘、库存预警、利润、异常 |
| 二维码/Excel | 4 | Base64 二维码、PNG、Excel 导出回导、Excel 新增导入 |
| OCR | 2 | mock 识别、空文件校验 |
| 用户与权限 | 2 | 用户创建登录、更新停用、用户列表 |

原始结果：[api-results-current.json](./api-results-current.json)

## 5. Web UI 回归结果

Chrome Headless 共执行 38 项，全部通过：

- 登录页品牌、标题、登录按钮和演示账号提示
- 管理员登录、身份显示、完整 20 项菜单
- 仓储运营概览默认页面与截图生成
- 管理员访问物品、往来单位、扫码入库、扫码出库、入出库单、报损报溢、调拨、盘点、库存、采购、报表、库龄、二维码/Excel、用户、日志和商城全部页面
- 调拨新建表单批次号字段可见
- 管理员退出、操作员登录与身份显示
- 操作员隐藏用户管理菜单，并验证其 16 项业务菜单
- 无未捕获 JavaScript 异常
- 无浏览器错误日志
- 无 HTTP 5xx 响应

原始结果：[ui-results-current.json](./ui-results-current.json)

管理员仪表盘截图：[ui-admin-dashboard-current.png](./ui-admin-dashboard-current.png)

## 6. 测试过程中修复的问题

1. `wms-web/src/main.js` 原先无论是否存在 token 都调用 `/auth/me`，未登录打开页面会制造一次 401 浏览器错误。已移除重复的启动请求，由 `App.vue` 仅在存在 token 时负责校验。
2. UI 回归脚本原先仍断言旧登录标题、旧仪表盘标题和旧菜单列表，已同步为当前 UI 文案与完整菜单，并补充采购、报损、日志、库龄和商城页面切换覆盖。
3. 操作员菜单断言按实际权限契约修正：操作员不显示扫码入库/出库，扫码接口由 API 回归确认返回 HTTP 403。

## 7. 已知提示与未覆盖边界

- Web 构建报告 Ant Design Vue 和 ECharts 产物超过 500 kB 的 chunk warning；构建成功，不影响本次功能测试，但建议后续通过 manualChunks 或按页面进一步拆包。
- Spring Boot 启动时提示 Flyway 版本对 H2 2.2.224 的支持范围提醒；迁移已成功执行，未造成测试失败。
- 微信支付真实商户证书、签名、通知网络链路未在本地验证，当前仅验证 mock 和服务层/控制器测试。
- 微信小程序本次验证的是编译构建；未使用微信开发者工具执行真机/模拟器交互测试。
- 商城 API 已由 `MarketControllerTest` 和 `MarketServiceIntegrationTest` 覆盖，HTTP 回归脚本主要覆盖仓储后台主链路；如需发布前更高置信度，建议增加商城端到端 API 回归。

## 8. 可复现命令

```bash
cd wms-server && mvn -q test

# 另开终端启动 API 测试实例
cd wms-server && SERVER_PORT=18088 mvn -q spring-boot:run

cd <repo-root>
python3 test-artifacts/run_api_regression.py http://127.0.0.1:18088/api/v1

# 另开终端启动 Web，指向 18088 API
cd wms-web
VITE_API_BASE=http://127.0.0.1:18088/api/v1 npm run dev -- --host 127.0.0.1 --port 5175

cd <repo-root>
python3 test-artifacts/run_ui_regression.py http://127.0.0.1:5175/
```
