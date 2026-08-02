# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A full-stack warehouse management system (WMS) for purchase-sales-inventory, modeled after the README scenario. Separated frontend and backend:

- **Backend** (`wms-server/`): Spring Boot 3 + Spring Data JPA + Spring Security, Java 21
- **Frontend** (`wms-web/`): React 18 + Vite 6 + Ant Design 5 + Recharts
- **Default DB**: H2 in-memory (auto-seeds demo data on every restart). MySQL supported via env vars.
- **Demo users**: `admin / admin123` (ADMIN), `operator / operator123` (WAREHOUSE)

Ports: API `8088`, Web dev `5173`, Docker stack maps Web→`3000`.

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
docker compose up --build    # web:3000, api:8088, mysql:3306
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
common/      ApiResponse<T> envelope, exceptions, error codes
security/    JWT token filter, SecurityConfig, UserDetailsService
config/      CORS, OpenAPI, data seeding
```

**State machines** (enforced in `DocumentService` / transfer / stocktake services):
- Inbound/Outbound document: `DRAFT → APPROVED → COMPLETED`, also `→ REJECTED`, `→ CANCELLED`. Only `COMPLETED` writes to inventory and `stock_transaction`. Includes `RETURN_IN` (customer return) and `RETURN_OUT` (return to supplier) types. `COMPLETED` documents can be **reversed** (`/uncomplete` → `APPROVED` with compensating inventory) or **red-reversed** (`/reverse` → generates a `.V` inverse document).
- Adjustment (报损/报溢): `DRAFT → APPROVED → COMPLETED`, `LOSS` reduces and `GAIN` increases inventory via `loss_out` / `gain_in` transactions.
- Transfer: `DRAFT → APPROVED → COMPLETED`, also `→ REJECTED`. Must use different source/target warehouses. Execution writes paired `transfer_out` + `transfer_in` transactions sharing the same reference number.
- Stocktake: snapshotted expected vs counted → ADMIN adjusts inventory. Creating with `itemCodes` / `locationCodes` filters performs a partial stocktake.

**Cost model** (`InventoryCostCalculator`): moving weighted average. Inbound recalculates average cost on completion; outbound transfers cost at the current average cost.

**Auth**: Bearer Token (custom, in-memory `TokenStore`), 12-hour TTL, lost on restart. Spring Security filter chain. All endpoints except `/auth/**`, `/health`, H2 console require auth.

### Frontend (`wms-web/src/`)

Vite + React 18 SPA. Ant Design for UI, axios for HTTP, Recharts for the dashboard. Vite proxy forwards `/api/v1` to `http://localhost:8088` in dev — frontend code should always use relative `/api/v1/...` paths.

State machines on the UI must mirror backend transitions; the UI calls the same status-transition endpoints (`/{id}/review`, `/{id}/complete`, `/{id}/cancel`).

## Conventions

- **API path prefix**: all endpoints are under `/api/v1`. Frontend uses relative URLs.
- **Response envelope**: `{ code, message, data }` via `ApiResponse<T>`. Errors throw `BusinessException` / handled by `GlobalExceptionHandler`.
- **Document numbering**: generated centrally by `DocumentNumberService` from persistent `document_sequences` table (row-locked), one series per document type, embedded in `referenceNo`.
- **Transaction types** (`service/TransactionType.java`): `purchase_in`, `sale_out`, `transfer_in`, `transfer_out`, `adjustment`, `stocktake`, plus `return_in`, `return_out`, `loss_out`, `gain_in`, `reverse`. All inventory mutations go through `InventoryService` to keep the ledger consistent.
- **Stock transactions** are append-only — never edit past rows; reverse via a new compensating entry.
- **QR codes & Excel import/export**: PNG / Base64 Data URL via `QrCodeController`; Excel via Apache POI (`poi-ooxml`) through `ExcelController`.
- **Permissions**: ADMIN reviews documents / transfers / stocktakes / adjustments and manages users & warehouses. WAREHOUSE drafts and completes already-approved documents.
- **Partners**: inbound documents require a SUPPLIER-side partner; outbound requires a CUSTOMER-side partner; BOTH works for either. `RETURN_IN` uses a customer partner; `RETURN_OUT` uses a supplier.
- **Operation logging**: `aspect/OperationLogAspect` wraps controllers and records to `operation_logs` (query via `GET /logs`); `OperationLogService` writes in a `REQUIRES_NEW` nested transaction so it survives business rollbacks.

## Useful References

- `AGENTS.md` — full project conventions, role permissions, status flows, and operational notes (read this first for context).
- `DEVELOPMENT.md` — module list, run instructions, and API examples for auth and the main document flows.
- `README.md` — business scenario and feature scope.
- `docker-compose.yml` (repo root) and `wms-server/Dockerfile` for containerized deployment.
