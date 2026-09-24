# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A full-stack warehouse management system (WMS) for purchase-sales-inventory, modeled after the README scenario. Separated frontend and backend:

- **Backend** (`wms-server/`): Spring Boot 3 + Spring Data JPA + Spring Security, Java 21
- **Frontend** (`wms-web/`): Vue 3 + Vite 6 + Ant Design Vue 4 + axios + dayjs + echarts + Pinia
- **Mini programs** (`wms-miniapp/` = warehouse ops, `wms-shopping-miniapp/` = buyer storefront): uni-app + Vue 3 + Pinia. Both are source projects built with the WeChat devtools, **not** compose services. Their `mp-weixin.appid` values are currently identical — they must differ before launch.
- **Default DB**: H2 in-memory (auto-seeds demo data on every restart). MySQL supported via env vars.
- **Demo users**: `admin / admin123` (ADMIN), `operator / operator123` (WAREHOUSE) — dev/test seeding only; prod never creates them.

Ports: API `8088` (context path `/api/v1`), actuator on a separate management port `9089` (no context path; `health`/`info`/`prometheus` only — a separate port does **not** bypass Spring Security: requests there hit 401 until `SecurityConfig.managementPortChain` matches them by `localPort`), Web dev `5173`, Docker stack maps Web→`3000`.

## Build / Run Commands

### Backend
```bash
cd wms-server
mvn spring-boot:run                           # start API on :8088
mvn test                                     # run all tests
mvn -Dtest=ClassNameTest test                 # run a single test class
mvn -Dtest=ClassNameTest#methodName test      # run a single test method
mvn package                                  # build jar
```

### Frontend
```bash
cd wms-web
npm install
npm run dev        # Vite dev server on :5173 with /api/v1 → :8088 proxy
npm run build      # production build (manualChunks: react, antd, charts)
npm run preview
```

### Full stack via Docker
```bash
cp .env.example .env      # required: MYSQL_*/WECHAT_* creds are ${VAR:?} — compose refuses to start without them
docker compose up --build # web:3000, api:8088, management:9089, mysql:3306 — all bound to 127.0.0.1 by default
```

### Release check (runs everything the launch gate expects)
```bash
sh ops/release-check.sh   # git diff --check + mvn test + wms-web build + both miniapp builds
RELEASE_ALLOW_DIRTY=YES sh ops/release-check.sh   # the script refuses a dirty tree unless you say so explicitly
```

### Switch to MySQL (local)
```bash
export DB_URL='jdbc:mysql://localhost:3306/wms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME=root DB_PASSWORD=xxx DB_DRIVER=com.mysql.cj.jdbc.Driver
cd wms-server && mvn spring-boot:run
```

## Architecture

### Backend (`wms-server/src/main/java/com/wms/`)

```
controller/   REST endpoints, one controller per resource
service/      Business logic + state machines
model/        JPA entities (User, Item, Warehouse, Location, Inventory,
              Document, DocumentLine, Transfer, Stocktake, Partner, etc.)
repository/   Spring Data JPA repositories
dto/         Request/response DTOs (separated from entities)
common/      ApiResponse<T> envelope, GlobalExceptionHandler, error codes
security/    SecurityConfig (@EnableMethodSecurity), TokenAuthenticationFilter,
             TokenService (DB-backed sessions, SHA-256 digest — not JWT),
             Permissions + RolePermissions (RBAC matrix), SecurityUtils
config/      WebConfig (CORS), DemoDataConfig (dev/test seed),
             ProductionSafetyConfig + ProductionBootstrapConfig (@Profile("prod")),
             TraceIdFilter (MDC traceId, X-Trace-Id)
```

There is **no** Springdoc/OpenAPI/Swagger dependency, and no Lombok — don't assume either exists.

**State machines** (enforced in `DocumentService` / transfer / stocktake services):
- Inbound/Outbound document: `DRAFT → APPROVED → COMPLETED`, also `→ REJECTED`, `→ CANCELLED`. Only `COMPLETED` writes to inventory and `stock_transaction`. Includes `RETURN_IN` (customer return) and `RETURN_OUT` (return to supplier) types. `COMPLETED` documents can be **reversed** (`/uncomplete` → `APPROVED` with compensating inventory) or **red-reversed** (`/reverse` → generates a `.V` inverse document).
- Adjustment (报损/报溢): `DRAFT → APPROVED → COMPLETED`, `LOSS` reduces and `GAIN` increases inventory via `loss_out` / `gain_in` transactions.
- Transfer: `DRAFT → APPROVED → COMPLETED`, also `→ REJECTED`. Must use different source/target warehouses. Execution writes paired `transfer_out` + `transfer_in` transactions sharing the same reference number.
- Stocktake: snapshotted expected vs counted → ADMIN adjusts inventory. Creating with `itemCodes` / `locationCodes` filters performs a partial stocktake.

**Cost model** (`InventoryCostCalculator`): moving weighted average. Inbound recalculates average cost on completion; outbound transfers cost at the current average cost.

**Auth**: opaque Bearer token, 12-hour TTL, persisted in `auth_sessions` (only its SHA-256 digest is stored, so tokens survive restarts on MySQL). `POST /auth/logout` revokes the current session; saving a user via `PUT /auth/users/{id}` revokes all of that user's sessions. Permit-all is **not** the whole `/auth/**` — only `/auth/login`, `/auth/wx-login`, `/auth/wx-bind`, `/auth/wx-register`, `/health`, `/health/**`, the two WeChat pay callbacks `/market/pay/notify` + `/market/pay/refund-notify`, plus `/h2-console/**` under dev. Everything else (including all `/market/**` browsing) requires a token; the two pay callbacks are unauthenticated and rely on WeChat APIv3 signature verification — trust nothing extra in them. Login binding is rate-limited per `IP|username` (`auth_login_attempts`, 5 failures / 10 min → HTTP 429).

### Frontend (`wms-web/src/`)

Vite + Vue 3 SPA (Composition API — every page under `src/pages/` uses `<script setup>`). Ant Design Vue for UI, axios for HTTP, echarts for the dashboard, Pinia for state. HTTP envelope handling lives in `src/api/client.js` — it resolves only when `code === 200` and rejects with `message` otherwise; endpoint wrappers are in `src/api/wms.js`. Routes are declared in `src/router/index.js` with a `meta.perm` per page, and `src/utils/menu.js` filters the sidebar by permission. Vite proxy forwards `/api/v1` to `http://localhost:8088` in dev — frontend code should always use relative `/api/v1/...` paths.

State machines on the UI must mirror backend transitions; the UI calls the same status-transition endpoints (`/{id}/review`, `/{id}/complete`, `/{id}/cancel`).

## Conventions

- **API path prefix**: all endpoints are under `/api/v1`. Frontend uses relative URLs.
- **Response envelope**: `{ code, message, data }` via `ApiResponse<T>`. Errors throw `BusinessException` / handled by `GlobalExceptionHandler`.
- **Document numbering**: generated centrally by `DocumentNumberService` from persistent `document_sequences` table (row-locked), one series per document type, embedded in `referenceNo`.
- **Transaction types** (`service/TransactionType.java`): `purchase_in`, `sale_out`, `transfer_in`, `transfer_out`, `adjustment`, `stocktake`, plus `return_in`, `return_out`, `loss_out`, `gain_in`, `reverse`. All inventory mutations go through `InventoryService` to keep the ledger consistent.
- **Stock transactions** are append-only — never edit past rows; reverse via a new compensating entry.
- **QR codes & Excel import/export**: PNG / Base64 Data URL via `QrCodeController`; Excel via Apache POI (`poi-ooxml`) through `ExcelController`.
- **Permissions**: RBAC over `resource:action` strings, not role equality. `security/Permissions.java` holds the constants, `security/RolePermissions.java` the matrix for 7 roles (`ADMIN`, `WAREHOUSE`, `PROCUREMENT`, `AUDITOR`, `FINANCE`, `CUSTOMER_SERVICE`, `CUSTOMER`). The filter writes both `ROLE_<role>` and the expanded permissions as authorities; controllers use `@PreAuthorize("hasAuthority('x:y')")` and services re-check `SecurityUtils.require(Permissions.X)` — both sides must be added together. Never branch on `role == "ADMIN"`. WAREHOUSE has no `*:review`, no `inventory:scan`, no `user:manage`/`warehouse:manage`/`log:view`.
- **Indexes only change via migration**: `application-prod.yml` runs `ddl-auto: validate`, so an `@Index` on an entity creates nothing in production. Add `db/migration/Vn__*.sql`, written to execute on both MySQL 8 and H2 `MODE=MySQL`. The test profile disables Flyway, so `mvn test` will not exercise a new migration — boot the default profile to verify it.
- **Operation logging**: `aspect/OperationLogAspect` wraps controllers and records to `operation_logs` (append-only, no manual write endpoint — anti-forgery); `OperationLogService` writes in a `REQUIRES_NEW` nested transaction so it survives business rollbacks. Query via `GET /logs`, which returns a server-paged envelope `{records,total,page,pageSize}` with **1-based** `page` and `pageSize` clamped to 1..200.
- **Audit retention**: `service/AuditArchiveService` runs daily (`audit.archive.cron`, default `0 30 3 * * *`) and exports rows older than `audit.archive.after-days` (default 365) into CSV files under `audit.archive.dir`, tracked by a primary-key watermark file. Archiving never deletes: removal requires `audit.archive.delete-enabled=true` and only touches rows the same run flushed to disk. Keep this job on a single instance until ShedLock (A7) lands.
- **Partners**: inbound documents require a SUPPLIER-side partner; outbound requires a CUSTOMER-side partner; BOTH works for either. `RETURN_IN` uses a customer partner; `RETURN_OUT` uses a supplier.

## Useful References

- `AGENTS.md` — full project conventions, role permissions, status flows, and operational notes (read this first for context).
- `DEVELOPMENT.md` — module list, run instructions, and API examples for auth and the main document flows.
- `README.md` — business scenario and feature scope.
- `docker-compose.yml` (repo root) and `wms-server/Dockerfile` for containerized deployment.
