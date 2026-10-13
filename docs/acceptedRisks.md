# Accepted Risks

Known limitations that have been reviewed and deliberately accepted for the current scope. Each entry states the risk, why it is acceptable now, and what would trigger a change.

## AR-1: Dashboard summary figures are read from four separate snapshots (READ COMMITTED)

**Status:** Accepted (2026-10-12)
**Affects:** `GET /api/v1/merchants/{merchantId}/dashboard/summary`, implemented in `DashboardService.getSummary`

### What happens

A summary request runs four independent SQL queries:

| # | DAO call | Window |
|---|---|---|
| 1 | `SummaryTransactionDao.sumApproved` | current period |
| 2 | `SummaryTransactionDao.sumApproved` | previous period |
| 3 | `ClickDao.count` | current period |
| 4 | `ClickDao.count` | previous period |

The queries do not share a transaction. Each one runs in auto-commit mode on PostgreSQL's default isolation level, **READ COMMITTED**, so each sees its own snapshot of committed data at the moment it starts. Wrapping the four queries in a single READ COMMITTED transaction would not change this: under READ COMMITTED, every *statement* takes a new snapshot.

### Possible effect

If data is written while a request is in progress, the figures in one response can come from different points in time. For example:

- A transaction inserted, or changed to/from `APPROVED`, between queries 1 and 2 can be reflected in one period's totals but not the other's.
- Clicks recorded between queries 3 and 4 can be counted for the current period while transaction totals predate them.
- The percentage changes are then computed from values that never existed together.

Nothing is corrupted or persisted wrongly. Only the single response may be inconsistent, and the next request reflects the newer data.

### Why it is accepted now

- The application has **no write path** for transactions or clicks; data comes only from the Liquibase demo seed and test fixtures, so the data is static while the application runs.
- Each query is individually correct, merchant-scoped and parameterized; aggregates are never joined, so there is no double-counting.
- A dashboard tolerates figures that are a few milliseconds apart; they are not financial statements.
- The JDBI ↔ Spring transaction integration (`TransactionAwareDataSourceProxy` in `JdbiConfig`) is not yet verified by tests, so relying on `@Transactional` now would add unverified behavior.

### When to revisit

Revisit when any of these becomes true:

- Transactions or clicks are written while the API serves requests (ingestion, status updates, imports).
- Summary figures are used for reconciliation, billing, or anything that must add up exactly.
- Another endpoint (e.g. the revenue trend) must agree exactly with the summary in the same request.

### Planned mitigation

Run the four queries in **one read-only transaction at `REPEATABLE READ`**. In PostgreSQL this gives all statements in the transaction the same snapshot, taken at the first query. Read-only `REPEATABLE READ` transactions do not raise serialization failures, so no retry logic is needed.

Implementation options (pick one when needed):

1. **JDBI directly:** in `DashboardService`, wrap the four DAO calls in `jdbi.inTransaction(TransactionIsolationLevel.REPEATABLE_READ, handle -> ...)` and attach the DAOs to that handle (`handle.attach(SummaryTransactionDao.class)`).
2. **Spring-managed:** annotate `getSummary` with `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. This option is valid only after an integration test proves that on-demand JDBI DAOs join the Spring transaction through `TransactionAwareDataSourceProxy`.

Either option should include an integration test that shows all four queries use the same snapshot (e.g. a concurrent insert committed between the queries is not visible to the later ones).

A single SQL statement that aggregates both periods with `FILTER` clauses would also produce one consistent snapshot, but it couples the transaction and click queries. Prefer the transaction-based fix unless query count becomes a performance concern.


## AR-2 Dasboard summary response does not contain previousPeriod int he payload once returned 
"revenue": {
    "current": 2840.00,
    "previous": 2797.50,
    "changePercentage": 1.52
  }

### Possible effect
Hard to debug/understand on the fontend side if not clear from the beggning.

### Why it is accepted now
Reducing complexity and focusing on the main functionality is the priority. At later stages we can add
in the payload the results from the period periods as well

### Planned mitigation
Introduce the priviousPeriod in the DashboardSummaryResponse when this will become a priority.