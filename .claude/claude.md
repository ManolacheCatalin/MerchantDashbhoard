# Affiliate Merchant Dashboard Backend — Claude Code Context

## Delivery profile: balanced engineering
Build the complete **visible dashboard backend** with strong API contracts, realistic PostgreSQL queries, meaningful tests, and reproducible setup. Keep the implementation deliberately small: no speculative infrastructure, authentication platform, ingestion pipeline, or non-dashboard modules.

Prioritize correctness and clarity over feature count. All significant business assumptions require explicit review.

## Mission and scope
Build a **small, maintainable Java backend API** powering the merchant dashboard shown in the assignment screenshot (Awin affiliate-marketing context). **No frontend is required.** Review criteria explicitly include code quality, API design, problem solving, and communication.

The screenshot shows a merchant selector, summary cards for revenue / transactions / clicks with percentage comparisons and date filters, a revenue trend chart (with comparison series), and a merchant to-do list. Navigation links (campaigns, partnerships, reports) do **not** imply that those full modules must be implemented.

The assignment is intentionally underspecified. Make assumptions visible, keep scope bounded, and avoid fabricating business rules.

## Fixed technical decisions
- Java 21 and Spring Boot, using a version compatible with the chosen dependencies.
- PostgreSQL as the relational database, running **natively on Windows** during development (no Docker, WSL2, or Kubernetes prerequisite).
- **JDBI v3** for SQL access, including SQL Object APIs where useful. Do not add JPA/Hibernate.
- **Liquibase** for schema migrations. Do not add Flyway.
- HikariCP for connection pooling.
- JUnit 5, Mockito, and Spring Boot testing support.
- Use the existing build tool once initialized; do not introduce another build system.
- Swagger/OpenAPI and Actuator are optional small enhancements, not requirements.

## Working principles
- Favor a simple modular monolith. Organize by feature when it clarifies ownership; avoid unnecessary layers and abstractions.
- Thin REST controllers, explicit DTOs, business logic in services, SQL in DAO/repository classes or JDBI SQL Objects.
- Constructor injection, validated inputs, parameterized queries, consistent error responses.
- Use `BigDecimal`/PostgreSQL `NUMERIC` for money. No floating-point arithmetic for monetary totals.
- No Kafka, Redis, Kubernetes, microservices, event ingestion, batch aggregation infrastructure, or authentication system unless there is a demonstrated requirement and explicit approval.
- Do not build unrelated campaign/partnership/report functionality just because it appears in navigation.

## Known dashboard capabilities (candidate MVP)
1. List/select merchants.
2. Fetch revenue, transaction-count, and click-count summaries for a merchant and reporting period, including previous-period percentage comparisons.
3. Fetch a revenue trend series for a merchant, selected reporting period, and granularity, potentially including a comparison series.
4. Fetch a merchant-specific to-do list.

Treat this as a **candidate scope**, not a claim that every detail is specified. Propose API contracts for review before implementation.

## Decisions that MUST be surfaced before coding business logic
- Does “revenue” mean merchant sales value, affiliate commission, or another amount? Which currency/currencies are supported?
- Which transaction statuses count; how do refunds/cancellations affect totals and trend dates?
- What does “transactions” count (all, approved, conversions, etc.)?
- Does “clicks” mean raw clicks or unique clicks? Is a conversion rate even required? (Not visible as a card.)
- Which reporting periods are supported, and how are previous periods aligned?
- Which timezone controls calendar-day buckets? Define timestamps and interval boundaries consistently (prefer half-open `[from, to)`).
- Should missing days appear with zero revenue? How are undefined percentage changes represented (e.g., previous value = 0)?
- What are to-do items: static seeded sample records or writable tasks? Do not invent task management if only display is required.
- How is merchant access controlled? If authentication is excluded, explicitly document that assumption; do not misrepresent path-based merchant filtering as authorization.

When a choice is unspecified, propose a **minimal documented assumption** and wait for approval on decisions that affect the public contract or stored data.

## Candidate API shape — not yet finalized
- `GET /api/v1/merchants`
- `GET /api/v1/merchants/{merchantId}/dashboard/summary?from=...&to=...`
- `GET /api/v1/merchants/{merchantId}/dashboard/revenue-trend?from=...&to=...&granularity=DAY`
- `GET /api/v1/merchants/{merchantId}/dashboard/todos`

Do not lock in these routes or response DTOs until they have been reviewed. Decide how predefined filters such as `LAST_90_DAYS` and `LAST_MONTH` map to date boundaries.

## Database and query quality
- Start with only the tables needed for the approved MVP (likely merchants, transactions, clicks, and to-do items; avoid adding campaigns unless necessary).
- Schema changes must go through Liquibase. Never rewrite already applied changesets.
- Seed a small **deterministic** example dataset and clearly distinguish demo data from migrations needed in production; ensure repeatable setup.
- Scope data queries by merchant ID; filter time using indexed timestamp columns with half-open intervals.
- Avoid accidental double-counting when joining clicks and transactions; aggregate independently where appropriate.
- Use appropriate indexes based on actual access patterns. Avoid premature aggregate tables/caches.
- Confirm correct Spring/JDBI transaction integration before relying on Spring `@Transactional`; do not assume annotations work without verified wiring.

## Testing and quality bar
- Unit-test date-period calculations, percentage changes, amount calculations, validation, and corner cases.
- Integration-test real SQL against PostgreSQL semantics using a dedicated local test database or another verified reproducible setup; H2 is not assumed equivalent to PostgreSQL.
- Cover empty datasets, previous period with zero values, period boundary inclusivity/exclusivity, zero-revenue days, merchant isolation, and any approved refund/status rules.
- Exercise HTTP contracts with controller/API tests (valid and invalid inputs, error status codes).
- Run the relevant build/test commands and report *actual* results. Never state that tests pass if they weren't run.
- Keep data, credentials, and configuration for development/tests separate. Don't print secrets.

## Development workflow for Claude
1. Read this file and any approved `docs/requirements.md` / `docs/architecture.md` first.
2. Before an architectural or public API decision: present options, tradeoffs, and a preferred minimal approach; **wait for approval**.
3. Implement one vertical feature at a time; keep diffs small and reviewable.
4. Add/adjust tests, run them, inspect failures, and correct defects.
5. Review the diff for unrelated changes, hidden assumptions, and needless complexity.
6. At each checkpoint, report: files changed, behavior implemented, commands/tests run with outcomes, assumptions, risks, and questions needing a decision.
7. Do not delete/overwrite data, run destructive Git operations, change credentials, or execute external installers without explicit permission.

## Deliverables
- Runnable backend with documented local PostgreSQL configuration (no container requirement).
- Versioned Liquibase changelog and optional reproducible demo-data procedure.
- Clearly documented API with example requests/responses.
- Tests and a concise README: setup, run, test, assumptions, tradeoffs, limitations, and scaling ideas.

## Implementation milestones (after design approval)
1. Agree metric definitions, date handling, API contracts and minimal schema.
2. Bootstrap Spring Boot, JDBI v3, Liquibase and dedicated PostgreSQL development/test configuration.
3. Implement merchant selection, summary metrics and percentage comparisons, then revenue-trend endpoint and to-do list, each as a tested vertical slice.
4. Verify API errors, database isolation by merchant, documentation and a fresh-start setup.

Each milestone must have acceptance criteria and a recorded decision or test result.

## First task when entering this project
**Plan only; do not generate application code yet.** Review the dashboard scope, identify open business rules, propose a minimal data model and endpoint contracts, outline integration-testing strategy without Docker, and list implementation stages. Ask for approval before proceeding.
