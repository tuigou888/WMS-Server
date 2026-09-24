# WMS 生产上线就绪评估与整改记录

## 1. 当前结论

评估日期：2026-09-20

当前项目已经具备较完整的仓储、库存、商城和管理后台功能，适合进入上线前整改和验收阶段；但暂不建议直接作为公开互联网生产系统投产。

| 使用场景 | 结论 |
| --- | --- |
| 本地开发、功能演示 | 可以 |
| 内部 UAT / 局域网试运行 | 完成关键 P0 整改后可以 |
| 单实例正式生产 | 完成 P0、备份恢复和真实微信联调后再上线 |
| 多实例、高可用生产 | 当前不支持 |

自动化验证已通过：后端 171 项、API 71 项、Web UI 38 项，Web 和两个微信小程序均构建成功。自动化结果不等同于真实 MySQL、微信支付和真机生产验收。

## 2. 已确认的主要风险

| 编号 | 优先级 | 风险 | 当前状态 |
| --- | --- | --- | --- |
| PROD-001 | P0 | 全新生产库没有安全的首个管理员初始化通道 | 已修复 |
| PROD-002 | P0 | Token、登录限速、微信绑定票据使用 JVM 内存，重启或多实例会失效 | 代码已修复，待 MySQL 并发压测 |
| PROD-003 | P0 | 真实微信登录、支付、退款、通知链路尚未完成真实环境验收 | 代码防护已完成，待真实联调 |
| PROD-004 | P0 | 小程序 AppID 为空，API 默认地址仍为 localhost，生产发布配置未完成 | 代码防护已完成，待配置 |
| PROD-005 | P0 | Compose 未提供 HTTPS 网关，MySQL/API 端口直接映射到宿主机 | 默认暴露面已收紧，HTTPS 待接入 |
| PROD-006 | P0 | MySQL 全新部署、历史库升级、备份恢复尚未完成发布环境演练 | 工具已补齐，待发布环境演练 |
| PROD-007 | P1 | 缺少指标采集、日志落盘与轮转、告警（存活/就绪探针已具备） | 仓库侧最小集已落地（actuator+prometheus、logback 轮转、traceId），采集与告警待接入 |
| PROD-008 | P1 | 登录页仍保留演示账号和默认用户名 | 已修复 |
| PROD-009 | P1 | OCR 接口仍返回 mock 识别结果 | 已隔离，待接入真实 OCR |
| PROD-010 | P1 | 当前工作区存在大量未提交改动，缺少可追溯发布基线 | 检查脚本已补齐，提交/打标签待执行 |
| PROD-011 | P0 | 支付/退款回调把原始异常 message 回给未鉴权调用方，可回显内部信息与单号 | 已修复（四处固定文案 + 2 条断言测试），真实回调验签待 C5 |
| PROD-012 | P0 | `DocumentRequest.partnerId` 无 `@NotNull`，入库须供应商、出库须客户的规则可被绕过 | 未改，属接口契约变更需业务确认（证据见 A2） |
| PROD-013 | P1 | `operation_logs`/`inventory_transactions`/过期 `wechat_bind_tickets` 无保留与归档策略，`GET /logs` 仍全表入内存后切片 | 分页已下推并三端同步；`operation_logs` 保留期 + 每日归档已落地（删除默认关闭），归档异地备份与票据清理仍待办 |
| PROD-014 | P1 | V5 索引只在 H2 上验证过，MySQL 8 未实跑；生产连接池为 Hikari 默认 10 未调参 | 连接池/慢 SQL/日志级别已显式化，MySQL 实跑待 C1/C2 |
| PROD-015 | P1 | 三个 `@Scheduled`（预占过期、取消后退款、审计日志归档）无分布式锁，多副本重复扫描并产生错误日志噪音 | 行锁+状态机已防重复扣货/退款；归档任务重复跑只产生冗余 CSV，扩多实例前仍需加锁 |
| PROD-016 | P2 | 文档与现实不符：AGENTS.md 未记 `wms-shopping-miniapp`（但被 `ops/release-check.sh` 构建）、"4 个测试类"实际 15 类 177 测试、"OCR 返回 mock"已改为配置驱动且 prod 直接 400 | 已同步（AGENTS.md + CLAUDE.md，CLAUDE.md 原写 React 18/内存 TokenStore 亦已纠正） |

## 3. 整改顺序

1. PROD-001：生产首个管理员初始化与启动保护。
2. PROD-002：认证状态迁移到共享存储，或明确限制为单实例并补充失效策略。
3. PROD-003/004：真实微信登录、支付、退款、回调和小程序发布配置。
4. PROD-005：HTTPS、反向代理、端口收敛和安全响应头。
5. PROD-006：MySQL 迁移、备份恢复、库存对账和回滚演练。
6. PROD-007/008/009/010：运维、界面、功能边界和发布流程收口。

## 4. 整改记录

### 2026-09-20：建立生产就绪评估文档

- 已将上线结论、风险、优先级和修复顺序集中到本文档。
- 当前未改变业务代码。
- 下一项处理：PROD-001。

### 2026-09-20：完成 PROD-001 首个管理员初始化

- 新增 `ProductionBootstrapConfig`，仅在 `prod` Profile 生效。
- 生产库没有管理员时，必须临时设置 `WMS_BOOTSTRAP_ADMIN_USERNAME` 和至少 12 位的 `WMS_BOOTSTRAP_ADMIN_PASSWORD`；配置缺失会阻止应用启动。
- 已有管理员时不会重复创建；初始化成功后应立即从 `.env` 和部署环境移除引导密码。
- Docker Compose 与 `.env.example` 已补充引导配置说明。
- 新增 `ProductionBootstrapConfigTest`，覆盖创建管理员、缺少配置失败和已有管理员幂等三条路径。
- 验证：`cd wms-server && mvn -q -Dtest=ProductionBootstrapConfigTest test` 通过；`git diff --check` 通过。
- 下一项处理：PROD-002。

### 2026-09-20：完成 PROD-002 认证状态持久化

- `TokenService` 改为数据库会话，数据库只保存 SHA-256 token 摘要；服务重启后会话仍有效，多实例可共享认证状态。
- 登录失败限速记录改为数据库表，成功登录会删除对应记录，重启不会清空限速窗口。
- 微信绑定票据改为数据库持久化，客户端票据只以摘要形式保存，消费时使用悲观锁并删除，避免重复绑定。
- 新增 Flyway `V4__persist_auth_state.sql`，包含会话、限速和微信绑定票据表及必要索引。
- 新增实体和 Repository：`AuthSession`、`LoginAttempt`、`WechatBindTicket`。
- 验证：`cd wms-server && mvn -q test` 通过，166 项全部通过；`git diff --check` 通过。
- 尚未关闭的边界：V4 迁移和多实例并发登录需要在 MySQL 发布环境中完成演练；当前测试环境为 H2。
- 下一项处理：PROD-003/004，真实微信链路和小程序生产配置。

### 2026-09-20：完成 PROD-003/004 代码侧发布防护

- `ProductionSafetyConfig` 现在会在 `prod` Profile 下校验 `WECHAT_APPID` 和 `WECHAT_SECRET`，缺失时拒绝启动。
- 两个小程序只有开发模式允许回退到 `http://localhost:8088/api/v1`；生产构建未注入 `VITE_API_BASE` 时会返回明确配置错误，不再静默请求 localhost。
- 微信小程序 `urlCheck` 已打开，正式环境必须使用微信后台登记的 HTTPS 合法域名。
- 两个小程序新增 `check:release` 和 `build:*:release` 配置检查，AppID 为空或 API 地址不是 HTTPS 时主动失败。
- 验证：后端目标测试通过；`wms-miniapp npm run build:mp-weixin` 通过；`wms-shopping-miniapp npm run build:wechat` 通过；release 检查在当前空 AppID 配置下按预期失败。
- 尚未关闭的边界：真实 AppID、API 域名、微信登录、支付、退款、通知和真机流程需要发布环境及微信商户配置，当前不能标记为已验收。
- 下一项处理：PROD-005，HTTPS、反向代理和端口收敛。

### 2026-09-20：完成 PROD-005 默认暴露面收紧

- MySQL、API 和 Web 的 Compose 端口默认绑定 `127.0.0.1`，可通过 `WMS_*_BIND` 显式覆盖；数据库不再默认监听所有公网网卡。
- 三个容器增加 `restart: unless-stopped`，Web Nginx 增加请求体大小限制、代理超时、安全响应头和版本隐藏。
- 当前 Web Nginx 仍只提供 HTTP 80；正式公网环境必须在云负载均衡或外部 Nginx/Caddy 终止 HTTPS，并只开放 443。
- 验证：提供测试变量后 `docker compose config -q` 通过；`cd wms-web && npm run build` 通过；`git diff --check` 通过。
- 尚未关闭的边界：证书自动续期、WAF/网关限流和公网入口需要结合实际域名与部署平台配置。
- 下一项处理：PROD-006，MySQL 迁移、备份恢复和库存对账演练。

### 2026-09-20：完成 PROD-006 发布工具补齐

- 新增 `ops/backup-mysql.sh`：使用一致性快照导出到权限受限临时文件，压缩后自动清理并生成 SHA-256 校验文件。
- 新增 `ops/restore-mysql.sh`：默认拒绝恢复，必须显式设置 `WMS_CONFIRM_RESTORE=YES`，降低误覆盖数据库风险。
- 新增 `ops/reconcile-mysql.sh`：复用库存流水只读对账 SQL，便于上线前后留存报告。
- `DEVELOPMENT.md` 已补充 V4 迁移、备份、恢复和对账命令。
- 验证：三个脚本 `sh -n` 通过；恢复脚本未确认时按预期返回退出码 2；备份脚本导出/压缩失败会传递非零退出；`git diff --check` 通过。
- 尚未关闭的边界：当前环境 Docker 守护进程不可用，未完成真实 MySQL 全新库、历史库升级、恢复和 Flyway 演练；不得将 PROD-006 标记为完全关闭。
- 下一项处理：PROD-007，健康检查、监控和告警基础能力。

### 2026-09-20：完成 PROD-007 健康探针基础能力

- `/health` 保留为存活探针，只表示进程能够处理请求。
- 新增 `/health/ready` 数据库就绪探针，连接不可用时返回 HTTP 503，不向外暴露数据库异常细节。
- Spring Security 已放行 `/health/**`，可直接供负载均衡或容器探针访问。
- 新增 `HealthControllerTest`，覆盖存活、数据库正常和数据库异常三条路径。
- 验证：`cd wms-server && mvn -q -Dtest=HealthControllerTest test` 通过；`git diff --check` 通过。
- 尚未关闭的边界：当前没有 Prometheus/日志平台/告警规则，仍需结合部署平台接入。
- 下一项处理：PROD-008/009/010，清理演示内容、明确 OCR 能力边界并整理发布基线。

### 2026-09-20：完成 PROD-008 演示账号隔离

- Web 登录页不再预填 `admin`，演示账号提示仅在 Vite 开发模式显示。
- 仓储小程序和商城小程序的演示账号入口仅在开发模式显示，生产包不再暴露默认口令。
- 登录页内部环境版本文案已替换为正式业务文案。
- 验证：`wms-web npm run build`、`wms-miniapp npm run build:mp-weixin`、`wms-shopping-miniapp npm run build:wechat` 均通过。
- 下一项处理：PROD-009，明确并隔离 OCR mock 能力。

### 2026-09-20：完成 PROD-009 OCR mock 隔离

- 开发环境保留 OCR mock，方便本地录入流程演示；`prod` Profile 强制关闭 OCR mock。
- 生产环境调用 OCR 接口会明确返回“服务尚未配置”，不会伪造识别结果或写入库存表单。
- Web 生产包隐藏“演示识别”入口；开发模式仍保留人工核验提示。
- 新增 `OcrControllerTest`，覆盖生产拒绝 mock 和开发 mock 返回结果两条路径。
- 验证：`cd wms-server && mvn -q -Dtest=OcrControllerTest test` 通过；`cd wms-web && npm run build` 通过。
- 尚未关闭的边界：如业务确实需要 OCR，仍需接入真实 OCR 服务、凭据、上传限制、隐私合规和识别结果人工复核流程。
- 下一项处理：PROD-010，整理发布基线和可追溯交付流程。

### 2026-09-20：完成 PROD-010 发布检查脚本

- 新增 `ops/release-check.sh`，统一检查 Git 工作区、差异空白、后端测试、Web 构建和两个小程序构建。
- 默认发现未提交或未跟踪文件时退出码为 2，防止把临时工作区直接当成正式版本；仅允许通过 `RELEASE_ALLOW_DIRTY=YES` 显式执行非正式验证。
- 当前工作区仍有既有业务改动和本轮整改改动，因此 PROD-010 不能标记为完全关闭；发布前需要人工审阅、提交、打标签并保留构建产物摘要。
- 验证：`sh -n ops/release-check.sh` 通过；脚本的脏工作区保护逻辑按预期工作。
- 下一步：完成一次最终全量测试，并由项目负责人确认提交/版本标签和真实环境验收清单。

### 2026-09-20：完成当前工作区最终自动化检查

- 执行 `RELEASE_ALLOW_DIRTY=YES ./ops/release-check.sh`。
- 后端 Maven 测试 171 项全部通过；Web 生产构建、仓储小程序构建、商城小程序构建全部通过；`git diff --check` 通过。
- 这是允许脏工作区的验证，不代表已生成正式发布版本；正式发布仍必须先清理并提交工作区、创建版本标签。

### 2026-09-21：修复 Linux 微信开发者工具模拟器的 WXSS 兼容问题

- 根据开发者工具日志确认，`simulator launch failed` 和 `timeout` 不是首个根因；模拟器随后已成功创建 appservice，真正失败点是 `编译 .wxss 文件错误`。
- 定位到 Linux 版工具内置旧版 `wcsc` 无法解析 uni-app 自动追加到 `app.wxss` 的 CSS 自定义属性（`--status-bar-height` 等）。页面级 WXSS 可独立通过编译器检查。
- `wms-miniapp/vite.config.js` 新增微信小程序构建后处理，仅移除本项目未使用的这组运行时 CSS 变量；同时保留 `swc: false`、`disableSWC: true` 的 Linux 原生绑定兼容配置。
- 验证标准：重新运行 `npm run build:mp-weixin`，确认 `dist/build/mp-weixin/app.wxss` 不含上述变量，并重新导入该目录后再观察模拟器日志。当前环境无法直接启动用户桌面的 Linux 开发者工具，因此最终模拟器画面仍需用户本机复核。

### 2026-09-21：修复 Linux 工具 tabBar 图片路径校验错误

- Linux 移植版开发者工具持续报告 `tabBar.iconPath` 不存在，尽管最新构建产物中的文件实际存在；同时原图标为 1×1 透明占位图，没有实际显示价值。
- 移除四个 tabBar 的 `iconPath` 和 `selectedIconPath`，保留“首页、扫码、库存、我的”文字导航，避免工具继续执行不稳定的图片路径校验。
- `npm run build:mp-weixin` 的资源校验脚本保留，未来若重新添加图片引用仍会检查路径；当前构建应报告 0 个图片引用。

### 2026-09-21：确认开发者工具安全信息接口提示

- 检查仓储小程序源码和生成产物，未发现 `webapi_getwxaasyncsecinfo` 调用；该日志来自开发者工具模拟器内部。
- 当前 `src/manifest.json` 的微信 AppID 为空，开发包使用 `touristappid` 游客模式，因此工具获取内部安全信息失败属于已知非阻塞提示，不作为项目运行失败判定。
- 正式联调时必须配置真实 AppID，并确保开发者工具项目配置与构建产物一致；AppSecret 只允许注入后端环境变量，不能写入小程序代码。

### 2026-09-21：修复 Linux 工具 WXSS 通配子选择器编译错误

- 使用当前开发者工具挂载目录内的 `wcsc` 对构建产物逐个验证：15 个页面级 WXSS 均通过，只有 `app.wxss` 失败。
- 逐条验证确认失败规则为 `.row > *` 和 `.row > *:first-child`；Linux 工具内置编译器返回 `error file count: 0`，开发者工具界面表现为 `GetCompiledResult error file count: -1`。
- 两条规则在页面中没有使用，已从全局样式移除。重新构建后，使用同一份开发者工具自带 `wcsc` 验证 `app.wxss` 与全部 15 个页面 WXSS 均通过。

### 2026-09-21：修复小程序本机登录地址与输入框裁切

- 生产构建不允许回退到 localhost，因此未注入 `VITE_API_BASE` 的构建包会正确提示“未配置生产 API 地址”；这不适用于本机模拟器联调。
- 新增 `build:mp-weixin:local`，仅在 Linux 本机构建时注入 `http://127.0.0.1:8088/api/v1`；正式发布构建仍要求 HTTPS API 地址，不降低生产安全约束。
- 登录页识别本地 API 后默认切换账号密码登录并展示测试账户；输入框采用 44px 固定高度与行高，修复模拟器中的用户名和密码文字裁切。

### 2026-09-21：修复小程序登录成功后的首页跳转兜底

- 登录 API 返回成功后，原逻辑依赖延迟 `switchTab`，且未处理 Linux 移植版模拟器中的导航失败回调，可能导致登录状态已写入但页面仍停留在登录页。
- 改为优先调用原生 `wx.reLaunch('/pages/index/index')`；失败时自动以 `switchTab` 重试，并保留控制台错误和用户可见提示。
- 登录页增加固定显示的原生 `navigator` 兜底入口，避免 Linux 移植版模拟器的 JavaScript 导航回调异常时阻断已登录用户进入首页。

### 2026-09-21：兼容 Linux 模拟器的字符串 JSON 响应

- 登录接口在网络面板中返回 `code: 200`，但前端状态未更新；Linux 移植版模拟器可能将 JSON 作为字符串交给 `uni.request` 的 `res.data`。
- 统一请求层新增安全 JSON 归一化：字符串可解析时转换为对象，不可解析时保持原值，下载等非 JSON 响应不受影响。
- 该处理同时覆盖登录、库存、单据等全部 JSON API，避免仅因模拟器响应类型差异导致业务逻辑跳过响应壳解析。

### 2026-09-20：完成 UI-LOCALIZATION-001 中文文案与北京时间统一

- Web 后台接入 Ant Design Vue 中文语言包，并覆盖全局弹窗按钮、分页单位和空数据文案：`确定`、`取消`、`页`、`无数据`。
- 用户角色、权限矩阵、操作日志、商城状态和小程序权限标签统一使用中文展示；接口路径、数据库字段、枚举值和权限编码保持不变，避免破坏业务契约。
- Web、仓储小程序、商城小程序的时间显示统一按 `Asia/Shanghai`（北京时间）格式化；后端 JVM、Jackson 和 Hibernate 默认时区同步设置为北京时间。
- 清理可见的 `N/A`、`Top`、`API`、`URL`、`Excel` 等业务界面英文文案，保留必要的技术标识、接口路径和账号示例。
- 验证：Web 构建、两个小程序构建、后端 Maven 测试和 `git diff --check` 均通过。
- 尚未关闭的边界：真实浏览器/真机仍需进行一次视觉验收，重点确认 Ant Design Vue 的分页、弹窗和空状态在目标运行环境中均显示中文。

### 2026-09-20：修复 UI-LOCALIZATION-002 表格时间与权限编码未生效

- 原因：项目使用 Ant Design Vue 4，部分表格仍使用旧版 `render` 配置；该配置不会触发格式化回调，导致 ISO 时间和权限编码原样显示。
- 修复：用户与角色权限矩阵改用 `customRender`，所有 Web 端创建时间、更新时间、操作时间和流水时间列统一改用 `customRender`，接入北京时间格式化器；权限编码继续保留在接口层，仅在界面显示中文说明。
- 结果：权限矩阵显示“采购申请审核”“盘点查看”“物品档案维护”等中文说明，时间显示为 `YYYY-MM-DD HH:mm:ss` 的北京时间格式。
- 验证：`cd wms-web && npm run build` 通过；`git diff --check` 通过。

### 2026-09-20：修复 UI-LOCALIZATION-003 其他表格中文映射未生效

- 原因：除用户权限矩阵和时间列外，日志、单据、库存、报表及商城表格仍有历史 `render` 列配置；在 Ant Design Vue 4 中这些回调不会自动执行。
- 修复：新增统一的 `normalizeColumns` 适配层，将历史 `render(text, record, index)` 转换为 Ant Design Vue 4 的 `customRender`，覆盖主列表、详情明细、展开行和商城统计表。
- 结果：状态、动作、类型、金额、数量和权限相关的中文/格式化展示逻辑在各表格统一生效，后端英文枚举和接口字段保持不变。
- 验证：Web 生产构建和 `git diff --check` 通过。

### 2026-09-20：修复 MINIAPP-BUILD-001 微信开发者工具找不到 app.json

- 原因：`wms-miniapp/` 是 uni-app 源码目录，`app.json` 由 uni 编译器生成在 `dist/build/mp-weixin/`，不能直接把源码目录当作原生微信小程序项目编译。
- 修复：根目录 `project.config.json` 增加 `miniprogramRoot: "dist/build/mp-weixin"`，并在项目文档中明确“先构建、再打开编译产物”的操作路径。
- 使用：执行 `cd wms-miniapp && npm run build:mp-weixin`，微信开发者工具打开 `wms-miniapp/dist/build/mp-weixin`；也可打开 `wms-miniapp` 根目录，但必须先完成构建。
- 验证：编译产物包含 `app.json`、`app.js` 和 `project.config.json`，构建流程通过。

### 2026-09-20：定位 MINIAPP-BUILD-002 native binding 编译错误

- 现象：微信开发者工具编译时报 `Failed to load native binding getDevCodeByFileList-miniProgram`。
- 结论：`wms-miniapp` 执行 `npm run build:mp-weixin` 已成功，编译产物结构完整；当前 Linux 开发者工具安装包版本为 `2.02.2608070`，仅检测到 Windows 的 `@swc/core-win32-x64-msvc` 原生模块，没有匹配 Linux x64 的 SWC 绑定。因此错误发生在开发者工具内部，不是业务代码或 uni-app 产物语法错误。
- 关联报错：`module 'app.js' is not defined` 是 SWC 初始化失败后主包没有完成注册的连锁错误；编译产物中的 `dist/build/mp-weixin/app.js` 文件实际存在且内容完整。
- 修复：在 `wms-miniapp/project.config.json` 与 `src/manifest.json` 中增加 `swc: false`、`disableSWC: true`，让 v2.02+ 开发者工具跳过 SWC 原生编译路径；修改后需完全重启开发者工具并重新导入编译产物。
- 备用处理：若 Linux 工具仍未读取配置，再更新/重装包含 `@swc/core-linux-x64-gnu`（或对应架构）绑定的版本。
- 状态：项目侧已加入 SWC 兼容配置，待 Linux 开发者工具重新导入并编译复核。

### 2026-09-24：完成上线前整改清单 A1、A3①、A4、A5、A6

- A1 支付回调脱敏：四处异常响应改固定文案，状态码语义不变（验签失败 400 终止重试、业务异常 5xx 保留重试），补 2 条断言响应体固定的 Mockito 用例。
- A3① 审计日志：`GET /logs` 改服务端分页 `{records,total,page,pageSize}`（1 起算、`pageSize` 钳制 1..200），删除无调用方的全表 `search(...)`；`wms-web/LogsPage.vue` 与作业端 `mine.vue` 同步新形状（原清单判断"小程序端不受影响"是错的，已修正）。
- A4 可观测性：actuator + Prometheus 注册表、`logback-spring.xml` 落盘轮转、`TraceIdFilter` 关联单次请求日志；管理端口 `9089` 与 compose 绑定同步。
- A5 生产参数：Hikari 池、连接超时、`max-lifetime`、泄漏检测、慢 SQL 阈值与日志级别全部显式化并可用环境变量覆盖。
- A6 文档：AGENTS.md 与 CLAUDE.md 纠误（买家端小程序缺失章节、测试规模、OCR 现况、`/logs` 契约、React/内存 Token/JWT 等 stale 描述），`.gitignore` 补 `wms-server/logs/`。
- 实测发现并已修复的两个自身缺陷：独立管理端口仍会过 Spring Security 链（actuator 无 token 一律 401，采集器不可用）→ 新增 `SecurityConfig.managementPortChain` 按 `localPort` 精确放行；Hibernate 6 以 INFO 打慢语句，`LOG_LEVEL_SLOW_SQL` 默认 WARN 会把慢日志吃干净 → 默认改 INFO。
- 验证：`mvn -o test` 173/173 通过；默认 profile 启动后确认 `:9089/actuator/health`、`/actuator/prometheus` 免 token 返回 200，业务端口 actuator 仍 401，`logs/wms-server.log` 单次请求 16 行日志共享同一 traceId，阈值 1ms 下出现 `SQL_SLOW` 记录；`RELEASE_ALLOW_DIRTY=YES sh ops/release-check.sh` 通过（后端 + Web + 两个小程序构建）。
- 未完成并需要人来决策的三项：A2 往来单位必填（会改变现有表单与老草稿的可提交性）、A3② 审计日志保留与清理（删审计数据需合规签字）、A7 定时任务分布式锁（扩多实例前必做）。

### 2026-09-24：完成 A3② 审计日志保留期与每日归档

- 决策采集时得到两个互相冲突的回答：保留期选了"永久保留、只归档不删"，处理方式又选了"先导出归档再删"。删除不可逆，按可回溯那一侧实现：归档默认执行，删除由 `audit.archive.delete-enabled` 显式打开（默认 false），且只删本轮已 `fsync` 落盘的归档行。
- 新增 `service/AuditArchiveService`（每日 `0 30 3`，`audit.archive.enabled` 控制是否自动跑）+ `OperationLogRepository.findArchiveBatch`（主键游标 + `operation_at <` 上限，不用 offset 翻页）；配置项 `audit.archive.{enabled,after-days,cron,dir,batch-size,delete-enabled}` 全部支持环境变量覆盖，compose 显式把归档目录钉在 `wms-logs` 卷内的 `/app/logs/archive`。
- 实现中自查到的两个问题：归档文件名只到秒，同秒二次触发会复用路径并再写一遍表头 → 改为占用即加 `-2/-3` 后缀；水位若在 CSV 落盘前推进，崩溃会留下"水位超前于文件内容"的静默丢失 → 每批 `flush + getFD().sync()` 后才写水位。
- 安全：`target/path/message/requestBody` 都源自请求内容，导出的 CSV 会被 Excel 打开，故对首字符 `= + - @ Tab` 前置单引号并把内嵌换行压成空格，避免审计日志变成公式注入载体。
- 未改 `V5__query_path_indexes.sql` 里"operation_logs 无任何清理逻辑"那句注释：已应用的迁移脚本一改就校验和失配、Flyway 拒绝启动。归档目录 `logs/` 已在 `.gitignore` 内，不会污染发布检查。
- 验证：`mvn -o -Dtest=AuditArchiveServiceTest test` 4/4 通过（默认不删、二次运行水位生效、删除只动已归档行、公式与引号转义）；全量 `mvn -o test` 177/177、15 个测试类通过。测试 profile 关掉 `audit.archive.enabled`，用例直接 `new` 被测对象并注入 `@TempDir`，不写共享单例。
- 仍开放：归档文件的异地备份（B4）、`wechat_bind_tickets` 过期行清理（本次按用户限定只做 `operation_logs`）、多实例下的调度锁（A7）。

## 5. 当前仍不能自动关闭的事项

- PROD-002：需要 MySQL 多实例并发登录和 V4 Flyway 迁移演练。
- PROD-003/004：需要真实微信 AppID、支付商户参数、HTTPS 域名、真机支付/退款/通知验收。
- PROD-005：需要外部 HTTPS 网关、证书续期和公网限流策略。
- PROD-006：需要发布环境完成 MySQL 新库、历史库升级、备份恢复和库存对账。
- PROD-007：仓库侧指标/日志/traceId 已具备，仍需外部采集、集中化与告警规则落地。
- PROD-009：如果业务需要 OCR，需要接入真实服务；否则保持生产入口关闭。
- PROD-010：需要人工审阅当前工作区，提交并创建可回滚版本标签。
- PROD-012：需要业务确认出入库是否允许无往来单位，属接口契约变更（详见 A2）。
- PROD-013：`operation_logs` 保留期与每日归档已在仓库侧闭环（删除默认关闭）；仍需运维确认归档文件的存放/备份保留策略（B4），以及是否真要打开 `AUDIT_ARCHIVE_DELETE_ENABLED`。`wechat_bind_tickets` 过期行清理未在本次范围。
- PROD-015：扩容多实例前必须给三个 `@Scheduled` 加分布式锁或独立调度实例（详见 A7）。

## 6. 关闭标准

整改项只有同时满足以下条件才可关闭：代码或配置已修改；相关异常路径有测试；生产文档已同步；验证命令通过；涉及库存、金额或支付的改动完成对账或真实联调。

## 7. 上线前整改清单（Go / No-Go）

清单日期：2026-09-23，A 组执行与验证于 2026-09-24。A 组在仓库内即可闭环；B/C/D 组需要环境、外部资源与人工演练配合，**缺一不可放行**。

### A. 仓库内可闭环（代码 / 配置）

- [x] **A1 回调异常脱敏**（PROD-011，P0）：**已完成**。`WechatPayNotifyController` 四处 catch 路径改回固定文案（"支付回调验签或解密失败"/"支付回调处理失败"/"退款回调…"），状态码语义保留：验签失败回 400 让微信停止重试，业务/未知异常回 5xx 让其按 15s/15s/30s… 重试，细节只进 `log.error`。验证：新增 2 条纯 Mockito 用例直接断言响应体固定（`WechatPayNotifyControllerTest`，8/8 通过），全量 173 用例绿。真实回调闭环仍在 C5。
- [ ] **A2 往来单位必填**（PROD-012，P0）：**未动，卡在一个业务决策**。现状证据：`DocumentsPage.vue:142` 把该字段标注为"（可选）"且 `allow-clear`；两个小程序的建单表单根本不提交 `partnerId`；`ReportController` 不读往来单位；`DocumentService.createDocument` 是 `r.partnerId()==null ? null : partner(...)`。因此直接加 `@NotNull` 会让现有 Web/小程序表单和老草稿一起 400。要推进必须先回答"出入库是否真的允许无往来单位"。**验证方式**：决策后新增 400 用例 + `python3 test-artifacts/run_api_regression.py` 全绿。
- [x] **A3① 审计日志服务端分页**（PROD-013，P1）：**已完成**。`OperationLogRepository.searchPage(...)` + `PageRequest`，`GET /logs` 返回 `{records,total,page,pageSize}`（`page` 从 1 起算、`pageSize` 钳制 1..200），并删除已无调用方的全表 `search(...)`。三端已同步：`wms-web/LogsPage.vue` 接服务端分页控件、作业端 `mine.vue` 改取 `data.records`。**注意**：清单原写"小程序端已传 `pageSize:20`，不受影响"是错的——该页把响应直接当数组渲染，形状变更后必须显式取 `records`，已修正。
- [x] **A3② 保留与归档**（PROD-013，P1）：**已完成（删除默认关闭）**。决策口径：审计数据**永久保留、只归档不删**，删除作为可选能力显式打开。新增 `AuditArchiveService`：`audit.archive.after-days`（默认 365）为保留期，每日 `0 30 3` 由 `@Scheduled` 跑一轮，把 `operationAt` 早于保留期、且主键大于水位的日志按批（`batch-size` 500）导出成 `${audit.archive.dir:-logs/archive}/operation_logs-<时间戳>.csv`（容器内即 `wms-logs` 卷）。按**主键游标**而非 offset 翻页，删除后不会漏行；每批 `fsync` 后才推进 `.operation_logs.watermark`，因此水位之后的数据不会丢，最坏情况是同批重复导出（冗余而非缺失）。**删除仅在 `audit.archive.delete-enabled=true` 时发生，且只删本轮已成功写入 CSV 的那批**；默认 false。CSV 文本字段全部加引号转义，首字符为 `= + - @ Tab` 者前置单引号，避免 `target/path/message` 被构造成 Excel 公式。归档失败只记 error、不影响业务，下一轮从水位续跑。验证：`AuditArchiveServiceTest` 4 用例（默认不删、二次运行空、删除只动已归档行、公式与引号转义）+ 全量 `mvn -o test` 绿。**仍留的口子**：归档文件的异地备份属 B4；`wechat_bind_tickets` 过期行清理不在本次范围（用户限定只处理 `operation_logs`）。
- [x] **A4 可观测性最小集**（PROD-007，P1）：**已完成并实测**。`spring-boot-starter-actuator` + `micrometer-registry-prometheus`（版本由 parent 管理，`1.13.14` 已确认可解析）；`management` 独立端口 `9089` 只暴露 `health,info,prometheus` 且 `show-details: never`；`logback-spring.xml` 落 `logs/wms-server.log`（50MB/30 天/2GB）；`TraceIdFilter` 写 MDC 并回写 `X-Trace-Id`，非法头值一律丢弃重新生成。验证：无 token `curl :9089/actuator/health` → `{"status":"UP"}`、`/actuator/prometheus` → 200；一次请求的 16 行日志全部带同一 `[trace:req-777]`；伪造 `X-Trace-Id: fake] [forgery 123` 被拒并回退为自生成 id；业务端口 `/api/v1/actuator/**` 仍 401。**过程中修掉两个真实缺陷**：① 独立管理端口并不会自动绕过 Spring Security，actuator 一律 401，Prometheus 根本抓不到 → 新增 `SecurityConfig.managementPortChain` 按 `localPort` 精确放行（未配独立端口时匹配器恒 false，随机端口测试行为不变）；② 轮转仅按大小+日期命名，跨天验证留到 C 组。
- [x] **A5 生产参数显式化**（PROD-014，P1）：**已完成**。`application-prod.yml` 补 Hikari 六项（`pool-name`、`maximum-pool-size` 默认 20、`minimum-idle`、`connection-timeout`、`max-lifetime` 默认 29min 且注释要求小于 MySQL `wait_timeout`、`leak-detection-threshold`）与 `logging.level`；慢 SQL 阈值 `SLOW_SQL_THRESHOLD_MS:200` 用方括号键写入 `spring.jpa.properties.hibernate`。验证：`configprops` 显示绑定后的键名大小写未被宽松绑定破坏；同形式的 `"[use_sql_comments]"` 实测让日志 SQL 带上 `/* */`，证明该写法确实交付给 Hibernate；阈值调到 1ms 后日志出现 `org.hibernate.SQL_SLOW - Slow query took 2 milliseconds`。**同时修掉一个自己埋的坑**：Hibernate 6 用 INFO 打慢语句，原默认 `LOG_LEVEL_SLOW_SQL:WARN` 会把慢日志全吃掉，已改为 INFO。MySQL 8 上的执行计划与连接池水位仍待 C1/C2。
- [x] **A6 文档纠误**（PROD-016，P2）：**已完成**。AGENTS.md 补 `wms-shopping-miniapp` 结构与"两端 appid 相同"的上线硬门槛、买家端 `/market/**` 需 token、`mockPay` 边界；测试规模改为真实 14 类 173 用例；`ocr/recognize` 改写为配置驱动（prod 直接 400）；`GET /logs` 契约与新增管理端口/日志说明同步；部署段纠正"root 密码默认 wms_password"（现为 `.env` 强制项）。CLAUDE.md 同步纠误（原写 React 18 + Recharts、"内存 TokenStore 重启即丢"、"permitAll 覆盖整个 `/auth/**`"、"security 用 JWT"，均与代码不符）。`.gitignore` 补 `wms-server/logs/`，否则本地启动即污染发布检查。验证：`RELEASE_ALLOW_DIRTY=YES sh ops/release-check.sh` 通过（后端 + 三个前端构建）。
- [ ] **A7（可选，扩多实例前必做）**（PROD-015，P1）：给三个 `@Scheduled`（预占过期、取消后退款、A3② 新增的审计日志归档）加 ShedLock 或独立调度实例，避免副本数增长后重复扫描与日志噪音放大。归档任务多副本并发不会丢数据（各写各的 CSV、删除按批幂等），但会产出重复归档文件，且多实例各自维护独立水位，因此在 A7 落地前应只在一个实例上开启 `audit.archive.enabled`。

### B. 环境与外部资源（仓库外，需人配合）

- [ ] **B1 HTTPS 网关**（PROD-005，P0）：反代 + 证书 + 自动续期 + HSTS + 安全响应头；公网只开 443；compose 三服务保持 `127.0.0.1` 绑定不变。回调地址 `WECHAT_PAY_NOTIFY_URL` / `REFUND_NOTIFY_URL` 必须是该域名下的 HTTPS。
- [ ] **B2 微信侧凭据**（PROD-003/004，P0）：AppID/Secret、商户号、API 证书序列号、APIv3 密钥（32 位）、商户私钥与微信支付公钥文件（挂 `secrets/`，已 gitignore）、小程序合法域名白名单。
      注意硬门槛：**prod profile 缺 `WECHAT_APPID/WECHAT_SECRET` 直接启动失败**（`ProductionSafetyConfig`），compose 又把商户变量标为必填。所以"先只上内网 Web、暂不接微信"这条路需要先做一个决策：拿到真实 AppID，还是把该校验放宽为"未配置即禁用商城模块"。不决策就没有降级方案。
- [ ] **B3 监控与告警落地**（PROD-007，P0）：采集 A4 暴露的指标，配 DB 连接池、磁盘、5xx 比例、支付回调失败、库存对账差异告警；日志集中化；主机 NTP 校时（对账与预占过期都依赖时间）。
- [ ] **B4 备份调度**（PROD-006，P0）：`ops/backup-mysql.sh` 进 cron，明确保留份数、异地存放、加密与访问控制；恢复用 `ops/restore-mysql.sh`。

### C. 发布环境演练（必须留证据，不接受口头确认）

- [ ] **C1 全新库部署**：空 MySQL 8 起 compose，确认 Flyway V1→V5 全部应用、`ddl-auto: validate` 通过、`ProductionBootstrapConfig` 用临时 `WMS_BOOTSTRAP_ADMIN_*` 建出首个管理员，**建号后立即从 `.env` 删除该两项并重启验证**。
- [ ] **C2 历史库升级**：对 Hibernate `ddl-auto: update` 时代的老库做 `baseline-on-migrate` 演练，确认 V2/V4/V5 在真实数据上可重放；记录 V5 前后的关键查询执行计划（`inventory_transactions` 的 `reference_no`、`market_order.refund_no`、`operation_logs` 过滤）。
- [ ] **C3 恢复演练 + 对账**：备份文件恢复到隔离实例 → `ops/reconcile-mysql.sh` 核对库存与流水一致，出具差异为 0 的记录。
- [ ] **C4 并发与容量**：出库/调拨并发不超卖、预占过期释放正确、`LoginRateLimiter` 在多实例下共享窗口生效；给出目标 TPS 下的响应时间与连接池水位。
- [ ] **C5 真实微信闭环**：真机支付 → 回调 → 发货 → 退款 → 退款回调，含回调重复投递与乱序（幂等）验证；`WechatPayNotifyController` 的 REFUNDING 中间态可查询。
- [ ] **C6 回滚演练**：镜像 tag 回退 + 数据库回滚点（Flyway 无 undo，回滚依赖 C3 的备份点），并演练一次实际回退。

### D. 基线与放行

- [ ] **D1 提交与打标签**：本仓库已有提交 `d4aafd0`（V5 索引迁移 + 交易查询下推 + 收藏事务修复 + AGENTS.md）并推送 `origin/master`，PROD-010 的"工作区未提交"部分已缓解；仍缺**可回滚版本标签**（当前 `git tag` 为空），且本评估文档尚未提交。要求：跑 `sh ops/release-check.sh`（不得用 `RELEASE_ALLOW_DIRTY=YES` 绕过）通过后建 tag（PROD-010）。
- [ ] **D2 关闭复核**：逐项对照第 6 节关闭标准，涉及库存、金额、支付的项必须有对账或真实联调证据。
- [ ] **D3 放行结论**：按第 1 节场景表重新判定 —— 目标为"单实例正式生产"时，A/B/C 组全绿即可放行；目标为"多实例高可用"时额外要求 A7 与 C4 多实例结论。
