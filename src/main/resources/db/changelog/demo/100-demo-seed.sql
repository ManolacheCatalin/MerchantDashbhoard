--liquibase formatted sql

-- Deterministic demo data covering [2026-01-01, 2026-10-01) UTC (day index d = 0..272).
-- Values derive from fixed formulas on d; no random() or now(). API keys are NOT seeded:
-- provision them locally with the README procedure.
-- "@dev": runs only when the "dev" context is passed explicitly, never on an unfiltered update.
--
-- Liquibase records each changeset in databasechangelog and never runs it twice. The preconditions are a second
-- guard for when that record is missing (e.g. tracking rows deleted, or demo data loaded another way): if the demo
-- rows a changeset would insert already exist, it is marked as run (MARK_RAN) instead of inserting duplicates.
-- A failing check query itself still halts the update (onError:HALT).

--changeset awin-merchant:100-demo-seed-merchants context:@dev
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM merchants WHERE id IN (1, 2, 3)
INSERT INTO merchants (id, name, reporting_currency) VALUES
    (1, 'Merchant A', 'GBP'),
    (2, 'Merchant B', 'EUR'),
    (3, 'Merchant C', 'GBP');  -- intentionally has no activity (empty dashboard)
SELECT setval(pg_get_serial_sequence('merchants', 'id'), (SELECT max(id) FROM merchants));
--rollback DELETE FROM merchants WHERE id IN (1, 2, 3);

-- Merchant A: 1-3 transactions per day, none on every 7th day (d % 7 = 6) to produce zero-revenue days.
-- Status cycles by (d + k) % 10: 0 PENDING, 1 DECLINED, 2 REFUNDED, otherwise APPROVED.
--changeset awin-merchant:100-demo-seed-transactions context:@dev
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM transactions WHERE merchant_id IN (1, 2)
INSERT INTO transactions (merchant_id, amount, status, occurred_at)
SELECT 1,
       20.00 + ((d * 7 + k * 13) % 50) * 2.50,
       CASE (d + k) % 10 WHEN 0 THEN 'PENDING' WHEN 1 THEN 'DECLINED' WHEN 2 THEN 'REFUNDED' ELSE 'APPROVED' END,
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + make_interval(days => d, hours => 8 + k * 4)
FROM generate_series(0, 272) AS d
CROSS JOIN LATERAL generate_series(1, 1 + d % 3) AS k
WHERE d % 7 <> 6
ORDER BY d, k;

-- Merchant B: 1-2 transactions per day, none when d % 5 = 4.
INSERT INTO transactions (merchant_id, amount, status, occurred_at)
SELECT 2,
       15.00 + ((d * 11 + k * 5) % 40) * 3.25,
       CASE (d + k) % 8 WHEN 0 THEN 'PENDING' WHEN 1 THEN 'REFUNDED' ELSE 'APPROVED' END,
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + make_interval(days => d, hours => 6 + k * 7)
FROM generate_series(0, 272) AS d
CROSS JOIN LATERAL generate_series(1, 1 + d % 2) AS k
WHERE d % 5 <> 4
ORDER BY d, k;
--rollback DELETE FROM transactions WHERE merchant_id IN (1, 2);

-- Clicks: a fixed per-day count, spaced 20 minutes apart from midnight UTC.
--changeset awin-merchant:100-demo-seed-clicks context:@dev
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM clicks WHERE merchant_id IN (1, 2)
INSERT INTO clicks (merchant_id, occurred_at)
SELECT 1, TIMESTAMPTZ '2026-01-01 00:00:00+00' + make_interval(days => d, mins => c * 20)
FROM generate_series(0, 272) AS d
CROSS JOIN LATERAL generate_series(0, 19 + (d * 17) % 40) AS c
ORDER BY d, c;

INSERT INTO clicks (merchant_id, occurred_at)
SELECT 2, TIMESTAMPTZ '2026-01-01 00:00:00+00' + make_interval(days => d, mins => c * 20)
FROM generate_series(0, 272) AS d
CROSS JOIN LATERAL generate_series(0, 9 + (d * 11) % 25) AS c
ORDER BY d, c;
--rollback DELETE FROM clicks WHERE merchant_id IN (1, 2);

--changeset awin-merchant:100-demo-seed-todo-items context:@dev
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM todo_items WHERE id IN (101, 102, 103, 201, 202)
INSERT INTO todo_items (id, merchant_id, title, status, created_at) VALUES
    (101, 1, 'Review expired offers',                'OPEN',      '2026-09-20T09:00:00Z'),
    (102, 1, 'Approve pending partner applications', 'OPEN',      '2026-09-22T14:30:00Z'),
    (103, 1, 'Update commission groups',             'COMPLETED', '2026-09-10T08:15:00Z'),
    (201, 2, 'Validate pending transactions',        'OPEN',      '2026-09-18T11:00:00Z'),
    (202, 2, 'Upload new product feed',              'COMPLETED', '2026-09-05T16:45:00Z');
SELECT setval(pg_get_serial_sequence('todo_items', 'id'), (SELECT max(id) FROM todo_items));
--rollback DELETE FROM todo_items WHERE id IN (101, 102, 103, 201, 202);
