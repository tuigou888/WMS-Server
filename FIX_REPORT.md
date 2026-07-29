# WMS 问题修复报告

**修复日期：2026-07-19**

## 已完成修复

1. 修复“用户与权限”页面切换时 React `destroy is not a function` 崩溃。
2. 修复调拨到空库存时平均成本由 `13.5417` 漂移为 `13.5425` 的问题，并新增回归单元测试。
3. 统一开发、Docker、Compose 和文档的 API 端口为 `8088`。
4. 新增生产 Nginx 配置，将 `/api/v1/` 反向代理到 `wms-server:8088`，并支持 SPA history fallback。
5. `/auth/me`、`/auth/logout` 和用户管理接口改为必须认证；失效 Token 返回 HTTP 401。
6. 管理员权限不足由 HTTP 400 规范为 HTTP 403。
7. 统一扫码接口文档为 `/stock/in/scan`、`/stock/out/scan`。
8. 增加前端构建分包配置，降低入口业务包体积。

## 回归验证

- `mvn test`：2 个测试全部通过。
- `npm run build`：生产构建成功。
- `docker compose config`：配置解析成功，API 映射为 `8088:8088`，Web 映射为 `3000:80`。
- `git diff --check`：无空白字符错误。

## 说明

前端 Ant Design 公共依赖块仍会触发 Vite 500 kB 体积提示，但入口业务代码已拆分；该提示不影响功能或部署。进一步优化需要页面级懒加载或组件库按需拆包，属于性能优化而非本次功能缺陷。
