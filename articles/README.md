# 微信公众号系列文章

本目录收录「从零搭建仓库进销存系统」系列公众号文章，与 `wms-server/`、`wms-web/` 配套阅读。

| 序号 | 标题 | 主题 | 对应代码 |
| :--: | --- | --- | --- |
| Day 1 | [从零搭建一套仓库进销存系统](微信公众号文章_Day1_从零搭建仓库进销存系统.md) | 移动加权平均成本、库存流水、利润计算 | `wms-server/src/main/java/com/wms/service/InventoryCostCalculator.java`、`InventoryService.java` |
| Day 2 | [让仓库系统真正能用起来](微信公众号文章_Day2_让仓库系统真正能用起来.md) | 跨仓调拨、盘点差异调整、智能预警、轻量权限、扫码 | `DocumentService.java`（调拨/盘点）、`ReportController.java`、`SecurityConfig.java` |
| Day 3 | [从「能跑」到「能交付」](微信公众号文章_Day3_从能跑到能交付单据流Excel报表与部署.md) | 单据流（草稿/审核/执行/取消）、Excel 导入导出、仪表盘、Docker Compose 部署 | `DocumentController.java`、`ExcelController.java`、`docker-compose.yml`、`wms-web/nginx.conf` |
| Day 4 | [测试只有 1 个，但 101 项检查全过](微信公众号文章_Day4_测试回归与验收报告.md) | JUnit 单测取舍、Python API 回归、CDP UI 回归、生产可交付评估 | `wms-server/src/test/java/com/wms/service/InventoryCostCalculatorTest.java`、`test-artifacts/run_*_regression.py`、`PRODUCTION_READINESS_REPORT.md` |
| Day 5 | [从「45 分」到「敢谈上线」](微信公众号文章_Day5_生产化整改审计与P0修复.md) | 功能差距审计、凭证闭环（报损报溢/退货/反审红冲/操作日志）、单据号落库与并发行锁、登录限速、演示数据保护、报表口径修正、RBAC 权限矩阵、Vue 3 重构、微信小程序 | `AUDIT_REPORT.md`、`DocumentNumberService.java`、`RolePermissions.java`、`LoginRateLimiter.java`、`OperationLogAspect.java`、`DemoDataConfig.java`、`wms-miniapp/` |

## 写作约定

- 编号顺序：`Day 1 → Day 2 → Day 3 → Day 4 → Day 5`，每篇结尾会预告下一篇主题
- 代码片段以**真实仓库文件**为依据，与 `AGENTS.md` 引用的代码路径一致
- 新增文章时：在上方表格中插入一行，文件名保持 `微信公众号文章_DayN_主题.md` 格式
