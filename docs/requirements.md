# Affiliate Merchant Dashboard Backend — Requirements

## 1. Purpose and scope

Implement a small Java backend that powers the **merchant dashboard** shown in the take-home assignment. No frontend is required. This document records agreed API contracts and business assumptions; it is the source of truth for implementation alongside `CLAUDE.md`.

**In scope:** merchant selector; revenue, approved-transaction, and click summary metrics; current-versus-previous percentage changes; daily revenue trend with previous-period comparison; retrieval and completion of to-do items; stateless API-key authentication and merchant-scoped authorization.

**Out of scope:** full affiliate attribution/ingestion pipeline, campaign management, partnerships, commission workflows, task creation/deletion, UI, Kubernetes, Redis, Kafka, and speculative scaling infrastructure.

## 2. Technology and design constraints

- Java 21, Spring Boot, Spring Security, PostgreSQL, JDBI v3, Liquibase, HikariCP.
- Modular monolith; thin controllers, services for business rules, DAOs for parameterized SQL.
- DTOs for HTTP responses, Jakarta Bean Validation, consistent error responses using Spring `ProblemDetail` (RFC 9457).
- JUnit 5, Mockito, Spring Boot integration tests; test PostgreSQL semantics using a dedicated local PostgreSQL test database (no Docker dependency for local execution).
- OpenAPI/Swagger UI and a clear README are desirable for evaluation.
- Native PostgreSQL on Windows is supported; Kubernetes and Docker are not required to build or run the application.
- API-key authentication is required. One active API key belongs to exactly one merchant, and each merchant has at most one active key (simplified assignment model). Every dashboard request is authorized for the authenticated merchant. See Section 6.

## 3. Domain model and database schema

Four business tables plus one security table, each with a primary key:

### `merchants`
- `id BIGINT` primary key
- `name VARCHAR(255)` not null
- **Assumption for coherent reporting:** `reporting_currency VARCHAR(3)` not null, e.g. `GBP`. All transactions for the merchant use this currency; currency conversion is out of scope.

### `transactions`
- `id BIGINT` primary key
- `merchant_id BIGINT` not null, foreign key to `merchants(id)`
- `amount NUMERIC(19,2)` not null, must be positive (refunds are a status, not negative amounts)
- `status VARCHAR(20)` not null, enum values `PENDING`, `APPROVED`, `DECLINED`, `REFUNDED`
- No per-transaction currency column: every amount is in the merchant's `reporting_currency`. Nothing at the database level validates incoming currency; this is a documented assumption.
- `occurred_at TIMESTAMPTZ` not null
- For this simplified model, only `APPROVED` transactions contribute to both revenue and transaction count. Other statuses contribute to neither. `occurred_at` is the only timestamp used in reporting; no approval/settlement timestamp is needed.
- For the take-home, a transaction's latest status controls historical calculations. This is a simplification, not a financial ledger.

### `clicks`
- `id BIGINT` primary key
- `merchant_id BIGINT` not null, foreign key to `merchants(id)`
- `occurred_at TIMESTAMPTZ` not null
- Every click row counts once; unique visitor tracking is out of scope.

### `todo_items`
- `id BIGINT` primary key
- `merchant_id BIGINT` not null, foreign key to `merchants(id)`
- `title VARCHAR(255)` not null
- `status VARCHAR(20)` not null, enum values `OPEN`, `COMPLETED`
- `created_at TIMESTAMPTZ` not null
- Items are persisted directly, not derived from broader offers/commission/application workflows.

### `merchant_api_keys` (authentication credential, not a fifth business entity)
- `id BIGINT` primary key
- `merchant_id BIGINT` not null, foreign key to `merchants(id)`, **unique** (one key record per merchant for this assignment)
- `key_hash VARCHAR(64)` not null, unique; lower-case hexadecimal SHA-256 of a cryptographically random high-entropy API key
- `active BOOLEAN` not null, default `TRUE`
- `created_at TIMESTAMPTZ` not null
- Never persist the plaintext API key. Generate keys securely outside request handling; distribute them privately as configuration/test credentials. No key creation, rotation, or administration API is required. For replacement, update the merchant's stored key hash through an explicit administrator/manual procedure.
- SHA-256 is acceptable here **only** because generated API keys have high entropy; never use it for human passwords.

Initial indexes to consider: `transactions(merchant_id, occurred_at)`, `clicks(merchant_id, occurred_at)`, `todo_items(merchant_id, status)`. Confirm index choices against actual queries; avoid premature optimizations. Schema and seed changes are managed through Liquibase.

## 4. Reporting semantics

- Revenue = `SUM(amount)` of `APPROVED` transactions for the selected merchant and reporting window.
- Transactions = `COUNT(*)` of `APPROVED` transactions for the same window.
- Clicks = `COUNT(*)` of click rows for the same window.
- Revenue trend = sum of approved transaction amounts grouped by UTC calendar day of `occurred_at`.
- All report windows use **UTC** dates and half-open intervals: `[from, to)` (inclusive `from`, exclusive `to`). API `from`/`to` are ISO date strings (`YYYY-MM-DD`), translated to UTC midnight instants.
- Comparison period is the immediately preceding period of **equal number of days**: if `duration = to - from`, previous is `[from - duration, from)`.
- Percentage change = `(current - previous) / previous * 100`. Return JSON `null` when previous equals zero, including when both current and previous are zero.
- Return zero totals for empty datasets, not errors. For revenue trends, include **every calendar date**, even days with zero approved transactions.
- Use `BigDecimal` for money and percentage computations. **Decided:** `changePercentage` is rounded to two decimal places with `RoundingMode.HALF_UP`.
- **Decided:** money is serialized as a JSON decimal number from `BigDecimal` with two decimals (e.g. `300.00`, `0.00`), not as a string. Never calculate monetary totals with `double`/`float`.
- Summary and trend filters are **independent**. Suggested frontend defaults are last 90 days for summary and last calendar month for trend; the API accepts explicit ranges. If omitted date parameters are supported, document precisely how defaults are calculated from a controllable clock.
- For a small exercise, support daily granularity only.
- **Decided:** a reporting period (`to - from`) is at most **366 days** (one full year, including a leap year), for both the summary and the revenue trend. Longer periods return `400`. `from`/`to` must also lie between `0001-01-01` and `9999-12-31`, and the comparison period must not start before `0001-01-01`.

## 5. REST API contracts (`/api/v1`)

All endpoints below require `X-API-Key: <secret>`. URL merchant identifiers must match the authenticated merchant. Missing/invalid/revoked credentials produce `401`; valid credentials for a different merchant produce `403`. Never accept a merchant identity supplied by the client as proof of authorization.

### 5.1 Merchant selector

`GET /api/v1/merchants`

Return **only the merchant associated with the authenticated API key**. The array shape is retained for frontend compatibility; it contains exactly one merchant in this assignment.

Example `200 OK` for the API key owned by Merchant A:

```json
{
  "merchants": [
    { "id": 1, "name": "Merchant A" }
  ]
}
```

### 5.2 Dashboard summary

`GET /api/v1/merchants/{merchantId}/dashboard/summary?from=2026-07-01&to=2026-10-01`

Each metric includes `current`, `previous`, and `changePercentage` (Option B, approved).

Illustrative `200 OK`:

```json
{
  "merchantId": 1,
  "period": { "from": "2026-07-01", "to": "2026-10-01" },
  "currency": "GBP",
  "revenue": {
    "current": 42850.50,
    "previous": 38122.33,
    "changePercentage": 12.40
  },
  "transactions": {
    "current": 1248,
    "previous": 1153,
    "changePercentage": 8.24
  },
  "clicks": {
    "current": 63400,
    "previous": 65300,
    "changePercentage": -2.91
  }
}
```

The values are illustrative only; test fixture expectations must be computed from actual fixture data.

### 5.3 Daily revenue trend

`GET /api/v1/merchants/{merchantId}/dashboard/revenue-trend?from=2026-09-01&to=2026-09-08`

Return two **separate arrays** with their real dates; arrays contain the same number of days and align by index (first day of current range to first day of previous range).

Abbreviated response (real responses must include all seven days):

```json
{
  "merchantId": 1,
  "currency": "GBP",
  "granularity": "DAY",
  "currentPeriod": {
    "from": "2026-09-01",
    "to": "2026-09-08",
    "data": [
      { "date": "2026-09-01", "revenue": 120.00 },
      { "date": "2026-09-02", "revenue": 180.00 },
      { "date": "2026-09-03", "revenue": 0.00 }
    ]
  },
  "previousPeriod": {
    "from": "2026-08-25",
    "to": "2026-09-01",
    "data": [
      { "date": "2026-08-25", "revenue": 90.00 },
      { "date": "2026-08-26", "revenue": 140.00 },
      { "date": "2026-08-27", "revenue": 110.00 }
    ]
  }
}
```

PostgreSQL performs daily aggregation; the service may fill absent days with zero values in Java. Avoid extra granularity options initially.

### 5.4 Open to-do items

`GET /api/v1/merchants/{merchantId}/todos`

Return **open** items only, oldest `created_at` first. Add `id` as a stable tie-breaker if timestamps match.

```json
{
  "merchantId": 1,
  "items": [
    {
      "id": 101,
      "title": "Review expired offers",
      "status": "OPEN",
      "createdAt": "2026-09-20T09:00:00Z"
    }
  ]
}
```

### 5.5 Complete to-do item

`PATCH /api/v1/merchants/{merchantId}/todos/{todoId}`

Request:

```json
{ "status": "COMPLETED" }
```

Return `200 OK` with the updated item (`id`, `merchantId`, `title`, `status`, `createdAt`). Repeating completion on an already completed item is **idempotent** and returns `200 OK`. Only completing a task is in scope; reopening it is not required. Restrict lookup/update by **both** `merchant_id` and item ID.

## 6. Authentication, authorization, validation, and errors

### 6.1 API-key authentication

- Use Spring Security and a custom authentication component/filter integrated with the Spring Security filter chain; run statelessly (no session, login, JWT, or OAuth2 server).
- Client sends the credential in `X-API-Key: <secret>` over HTTPS in real deployments. Reject missing, blank, unknown, or inactive keys with `401 Unauthorized`.
- Store **only** SHA-256 hashes of securely generated random API keys in `merchant_api_keys`. Hash the provided key and look up an active record using a parameterized JDBI query. The merchant ID from this lookup is the authenticated principal.
- Never log, echo, or place raw keys in URLs, exception messages, API responses, repository fixtures, or committed configuration. Read demo credentials from local environment or other untracked configuration. Provide README instructions for generating/provisioning a key securely, without committing a working secret.
- This is intentionally one key **per merchant**, with a unique merchant association. No multi-merchant keys, key-management endpoints, or rotation protocol are required.
- API keys are intended for server-to-server use. Document that a real browser-based merchant dashboard would normally use user login/session or OIDC through a trusted backend, rather than exposing permanent API keys in frontend JavaScript.

### 6.2 Merchant authorization

- `GET /api/v1/merchants` returns **only the authenticated merchant**, not the full merchant directory.
- For every endpoint containing `{merchantId}`, verify it matches the authenticated merchant **before** performing merchant data access. A mismatch yields `403 Forbidden`.
- DAO queries must remain merchant-scoped as defense in depth: metrics and to-do operations filter by merchant ID. `PATCH` restricts the update using both `merchant_id` and `todo_id`.
- Unknown merchant/task IDs within an otherwise permitted merchant scope return `404 Not Found`; a task owned by another merchant also returns `404` from the merchant-scoped lookup. Avoid leaking data from other merchants.

### 6.3 Error contract

| Scenario | HTTP status |
|---|---|
| Missing, blank, invalid, or inactive API key | `401 Unauthorized` |
| Valid API key requesting another merchant's endpoint | `403 Forbidden` |
| Invalid dates, `from >= to`, period longer than 366 days, malformed body, unsupported task status | `400 Bad Request` |
| Unknown merchant or task within permitted scope | `404 Not Found` |
| Unexpected server errors | `500 Internal Server Error` |

- Errors use consistent Spring `ProblemDetail` responses, including security failures where feasible.
- Never expose credentials, SQL details, or internal stack traces to clients.
- All business endpoints are authenticated; any exceptions for OpenAPI documentation or health probes must be explicit and documented rather than inadvertently public.

## 7. Quality and tests

- Unit-test date window calculations, percentage changes (including division by zero), zero-fill behavior, and task completion semantics.
- Integration-test SQL aggregations against PostgreSQL: mixed transaction statuses, multiple merchants, empty results, boundary timestamps, no-transaction days, and task updates scoped by merchant.
- Security-test missing/invalid/inactive API keys (`401`), valid same-merchant access, cross-merchant access (`403`), filtered merchant selector, and inability to read/complete another merchant's to-do item. Validate keys are not logged or exposed in error responses.
- Ensure data seed fixtures have known expected results and a README explaining how to start the DB, run Liquibase, run the app, and execute tests natively on Windows.
- Don't assert that tests were executed unless they actually ran.
- Document decisions and trade-offs in README, including the simplified transaction lifecycle, one-key-per-merchant security model, browser API-key limitation, and on-demand aggregation.

## 8. Implementation guidance for Claude Code

1. Read this document and `CLAUDE.md` before changes.
2. Propose schema (including `merchant_api_keys`), endpoint DTOs, Spring Security/JDBI integration, and test strategy; identify any remaining decisions.
3. Await approval for architecture or business-rule changes; implement in small vertical slices.
4. Keep the solution simple and reviewable; do not add unrelated endpoints or infrastructure.
5. Run relevant tests, review your diff, and report modified files, test results, assumptions, and unresolved questions.

## 9. Items to confirm before implementation

Resolved:

- Money format: JSON decimal numbers with two decimals (see Section 4).
- `changePercentage` rounding: two decimal places, `HALF_UP` (see Section 4).
- `from`/`to` are required query parameters for the summary endpoint; missing values return `400`.
- Consistency of the four summary queries under READ COMMITTED is an accepted risk; see `docs/acceptedRisks.md` (AR-1).

Do **not** treat these open items as authorization to silently invent additional features.
