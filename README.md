# Awin Merchant Dashboard API

A small Spring Boot backend for an affiliate merchant dashboard. Each request is authenticated with a merchant's API key, and a merchant can only see its own data.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/merchants` | Merchant selector: the merchant that owns the API key |
| `GET /api/v1/merchants/{merchantId}/dashboard/summary` | Revenue, transactions and clicks, compared with the previous period |
| `GET /api/v1/merchants/{merchantId}/dashboard/revenue-trend` | Daily revenue for the period and the previous period |
| `GET /api/v1/merchants/{merchantId}/todos` | Open to-do items |
| `PATCH /api/v1/merchants/{merchantId}/todos/{todoId}` | Mark a to-do item completed |

The project is for **local development only**. It runs natively on **Windows** and **macOS** with a local PostgreSQL install; Docker is not needed. Setup scripts are in `scripts/windows/` (PowerShell) and `scripts/macos/` (shell).

| | |
|---|---|
| Java | 21 |
| Framework | Spring Boot 4.1, Spring Security 7 |
| Database | PostgreSQL 17 |
| SQL access | JDBI 3 |
| Migrations | Liquibase |
| Build | Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on macOS); no separate Gradle install needed |

---

## Quick start

Once Java 21 and PostgreSQL 17 are installed ([section 1](#1-prerequisites)), run these from the project root.

**Windows (PowerShell):**

```powershell
.\scripts\windows\create-databases.ps1                            # 1. create the awin and awin_test databases (once)
.\gradlew.bat bootRun                                             # 2. start the app; the first start loads demo data
# In a second terminal, from the project root:
$apiKey = .\scripts\windows\provision-api-key.ps1 -MerchantId 1   # 3. create an API key for merchant 1
Invoke-RestMethod "http://localhost:8080/api/v1/merchants" -Headers @{ 'X-API-Key' = $apiKey }   # 4. call the API
```

If PowerShell refuses to run a script, run `Set-ExecutionPolicy -Scope Process Bypass` first. It only affects the current terminal.

**macOS (Terminal):**

```bash
./scripts/macos/create-databases.sh                       # 1. create the awin and awin_test databases (once)
./gradlew bootRun                                         # 2. start the app; the first start loads demo data
# In a second terminal, from the project root:
API_KEY=$(./scripts/macos/provision-api-key.sh 1)         # 3. create an API key for merchant 1
curl -H "X-API-Key: $API_KEY" http://localhost:8080/api/v1/merchants   # 4. call the API
```

---

## 1. Prerequisites

### 1.1 Java 21

Install any Java 21 JDK, for example Eclipse Temurin (https://adoptium.net/temurin/releases/?version=21):

- **Windows:** download the x64 `.msi`, or run `winget install EclipseAdoptium.Temurin.21.JDK`.
- **macOS:** download the `.pkg`, or with [Homebrew](https://brew.sh) run `brew install --cask temurin@21`.

Check it in a new terminal:

```
java -version   # should print version "21..."
```

### 1.2 PostgreSQL 17 on Windows

1. Download the Windows installer from https://www.postgresql.org/download/windows/ and choose version **17**.
2. Run it with the defaults, except:
   - **Components:** keep *PostgreSQL Server* and *Command Line Tools*. pgAdmin is optional; Stack Builder is not needed.
   - **Password for the `postgres` user:** `admin`. The app expects this password by default. If you choose another one, see [section 3](#3-configuration).
   - **Port:** `5432`.
3. PostgreSQL now runs as a Windows service. Check it:

   ```powershell
   Get-Service postgresql*   # Status should be "Running"
   ```

`psql` (the PostgreSQL command-line client) is not added to `PATH`. Where this README uses `psql`, first set these two variables in the terminal:

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = 'admin'
```

### 1.3 PostgreSQL 17 on macOS

With [Homebrew](https://brew.sh):

```bash
brew install postgresql@17
brew services start postgresql@17      # starts now and at every login

# Homebrew does not add this version's tools (psql, createuser) to PATH; add them for zsh, the macOS default shell:
echo 'export PATH="$(brew --prefix postgresql@17)/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Homebrew creates a database user named after your macOS account, **not** `postgres`. Create the `postgres` user with password `admin`, which the app expects by default:

```bash
createuser --superuser postgres
psql -d postgres -c "ALTER USER postgres PASSWORD 'admin';"
```

Check it:

```bash
brew services list | grep postgresql   # status should be "started"
```

For the `psql` commands later in this README, set the password once per terminal:

```bash
export PGPASSWORD=admin
```

---

## 2. Database setup

The app uses two databases:

| Database | Used by | Contents |
|---|---|---|
| `awin` | the running app | tables + demo data |
| `awin_test` | the automated tests | tables only; each test inserts its own data |

Create both once, before the first start:

```powershell
.\scripts\windows\create-databases.ps1     # Windows
```

```bash
./scripts/macos/create-databases.sh        # macOS
```

The script is safe to run again: databases that already exist are skipped. It uses the `PGPASSWORD` environment variable if set, otherwise it asks for the `postgres` password.

- Windows options: `-Databases awin` (only one database), `-Username`, `-DbHost`, `-Port`, `-PsqlPath`.
- macOS options: `-U user`, `-h host`, `-p port`, then optional database names (e.g. `./scripts/macos/create-databases.sh awin`).

You don't create tables yourself. **Liquibase creates them when the app or the tests start**. The changesets are in `src/main/resources/db/changelog/`. The demo data is loaded only into `awin`, never into `awin_test`.

---

## 3. Configuration

The defaults work if your `postgres` password is `admin`. Otherwise, override them with environment variables:

| Variable | Default | Used for |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/awin` | app database |
| `DB_USERNAME` | `postgres` | app database user |
| `DB_PASSWORD` | `admin` | app database password |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/awin_test` | test database |
| `TEST_DB_USERNAME` | `postgres` | test database user |
| `TEST_DB_PASSWORD` | `admin` | test database password |

To set the passwords for the current terminal only (nothing is saved to disk), load the helper script into your shell:

```powershell
. .\scripts\windows\set-db-env.ps1        # Windows: keep the leading dot
```

```bash
source scripts/macos/set-db-env.sh        # macOS: use "source", don't run it as ./set-db-env.sh
```

**Profiles:** the app runs with the `dev` profile unless you choose another one. The tests use the `test` profile, which connects to `awin_test`.

---

## 4. Build and run

All commands run from the project root.

| Goal | Windows | macOS |
|---|---|---|
| Start the app from source | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| Build the runnable jar (skips tests) | `.\gradlew.bat bootJar` | `./gradlew bootJar` |
| Start the jar | `java -jar build\libs\awinMerchant-0.0.1-SNAPSHOT.jar` | `java -jar build/libs/awinMerchant-0.0.1-SNAPSHOT.jar` |
| Run all tests | `.\gradlew.bat test` | `./gradlew test` |
| Compile, test and build everything | `.\gradlew.bat build` | `./gradlew build` |
| Delete build output | `.\gradlew.bat clean` | `./gradlew clean` |

- The first build downloads Gradle and the dependencies, so it needs internet access and takes a few minutes.
- `build` and `test` need PostgreSQL running and the `awin_test` database (section 2).
- `build/libs/` also contains a `-plain.jar`. You don't need it.

The app listens on **http://localhost:8080**. Stop it with `Ctrl+C`.

### Demo data

The first start loads this data into `awin`:

| Merchant | Currency | Data |
|---|---|---|
| 1, Merchant A | GBP | transactions and clicks every day from 2026-01-01 to 2026-09-30; to-do items |
| 2, Merchant B | EUR | transactions and clicks every day from 2026-01-01 to 2026-09-30; to-do items |
| 3, Merchant C | GBP | nothing (an empty dashboard) |

The data uses fixed dates, so the same request always returns the same numbers. Use dates between **2026-01-01 and 2026-10-01**. Later starts never reload or overwrite it. To start over, see [section 8](#8-reset-the-demo-database).

### Public endpoints

These need no API key:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI document: http://localhost:8080/v3/api-docs
- Health check: http://localhost:8080/actuator/health

---

## 5. API keys

The demo data contains no API keys. Create one per merchant **after the app has started once**:

```powershell
$apiKey = .\scripts\windows\provision-api-key.ps1 -MerchantId 1   # Windows; merchant 1, 2 or 3
```

```bash
API_KEY=$(./scripts/macos/provision-api-key.sh 1)                 # macOS; merchant 1, 2 or 3
```

- The script generates a random key, stores only its hash in the database, and returns the key. Keep it in `$apiKey` (Windows) or `$API_KEY` (macOS), as above, for the examples in section 6.
- The key is shown **once** and can't be recovered. If you lose it, run the script again. That replaces the merchant's old key.
- Each merchant has at most one key.
- Windows options: `-Database` (default `awin`), `-Username`, `-DbHost`, `-Port`, `-PsqlPath`.
- macOS options, before the merchant id: `-d database` (default `awin`), `-U user`, `-h host`, `-p port`.

To **revoke** a key, or **list** which merchants have one, use `psql`. On Windows, set `$psql` first (section 1.2):

```powershell
& $psql -U postgres -h localhost -d awin -c "UPDATE merchant_api_keys SET active = FALSE WHERE merchant_id = 1;"
& $psql -U postgres -h localhost -d awin -c "SELECT merchant_id, active, created_at FROM merchant_api_keys ORDER BY merchant_id;"
```

```bash
psql -U postgres -h localhost -d awin -c "UPDATE merchant_api_keys SET active = FALSE WHERE merchant_id = 1;"
psql -U postgres -h localhost -d awin -c "SELECT merchant_id, active, created_at FROM merchant_api_keys ORDER BY merchant_id;"
```

---

## 6. API reference

Every request below needs the header `X-API-Key: <key>`. The `{merchantId}` in the URL must be the merchant that owns the key; any other id returns `403`.

The examples below use PowerShell's `Invoke-RestMethod` with the `$apiKey` from section 5. On Windows you can also use curl, as `curl.exe` (not the PowerShell alias).

**macOS:** the same calls with curl and the `$API_KEY` from section 5:

```bash
H="X-API-Key: $API_KEY"; BASE=http://localhost:8080/api/v1/merchants
curl -H "$H" $BASE                                                                         # 6.1 merchant selector
curl -H "$H" "$BASE/1/dashboard/summary?from=2026-07-01&to=2026-10-01"                     # 6.2 summary
curl -H "$H" "$BASE/1/dashboard/revenue-trend?from=2026-09-01&to=2026-09-08"               # 6.3 revenue trend
curl -H "$H" $BASE/1/todos                                                                 # 6.4 open to-dos
curl -X PATCH -H "$H" -H "Content-Type: application/json" -d '{"status":"COMPLETED"}' $BASE/1/todos/101   # 6.5 complete
```

The example responses come from the demo data.

### 6.1 Merchant selector

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/merchants" -Headers @{ 'X-API-Key' = $apiKey }
```

```json
{ "merchants": [ { "id": 1, "name": "Merchant A" } ] }
```

It returns only the merchant that owns the key, as a list for the frontend's selector.

### 6.2 Dashboard summary

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/merchants/1/dashboard/summary?from=2026-07-01&to=2026-10-01" `
  -Headers @{ 'X-API-Key' = $apiKey } | ConvertTo-Json -Depth 5
```

```json
{
  "merchantId": 1,
  "period": { "from": "2026-07-01", "to": "2026-10-01" },
  "currency": "GBP",
  "revenue":      { "current": 8797.50, "previous": 8650.00, "changePercentage": 1.71 },
  "transactions": { "current": 110,     "previous": 109,     "changePercentage": 0.92 },
  "clicks":       { "current": 3643,    "previous": 3638,    "changePercentage": 0.14 }
}
```

- `current` covers the requested period, and `previous` covers the same number of days just before it. Here that's 92 days from `2026-03-31` to `2026-07-01`.
- Revenue and transactions count **approved** transactions only. Clicks count every click.
- `changePercentage` is `(current - previous) / previous × 100`, rounded to 2 decimals. It is `null` when `previous` is 0.

### 6.3 Revenue trend

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/merchants/1/dashboard/revenue-trend?from=2026-09-01&to=2026-09-08" `
  -Headers @{ 'X-API-Key' = $apiKey } | ConvertTo-Json -Depth 5
```

```json
{
  "merchantId": 1,
  "currency": "GBP",
  "granularity": "DAY",
  "currentPeriod": {
    "from": "2026-09-01",
    "to": "2026-09-08",
    "data": [
      { "date": "2026-09-01", "revenue": 55.00 },
      { "date": "2026-09-02", "revenue": 0.00 },
      { "date": "2026-09-03", "revenue": 242.50 },
      { "date": "2026-09-04", "revenue": 107.50 },
      { "date": "2026-09-05", "revenue": 157.50 },
      { "date": "2026-09-06", "revenue": 142.50 },
      { "date": "2026-09-07", "revenue": 0.00 }
    ]
  },
  "previousPeriod": {
    "from": "2026-08-25",
    "to": "2026-09-01",
    "data": [
      { "date": "2026-08-25", "revenue": 270.00 },
      { "date": "2026-08-26", "revenue": 0.00 },
      { "date": "2026-08-27", "revenue": 92.50 },
      { "date": "2026-08-28", "revenue": 0.00 },
      { "date": "2026-08-29", "revenue": 0.00 },
      { "date": "2026-08-30", "revenue": 52.50 },
      { "date": "2026-08-31", "revenue": 210.00 }
    ]
  }
}
```

- There is one entry per day, including days without sales (`0.00`). Both lists have the same length; entry 1 of the current period matches entry 1 of the previous period.
- Days are calendar days in UTC.
- `granularity` is optional. `DAY` is the only accepted value.

### 6.4 Open to-do items

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/merchants/1/todos" -Headers @{ 'X-API-Key' = $apiKey } | ConvertTo-Json -Depth 5
```

On freshly loaded demo data:

```json
{
  "merchantId": 1,
  "items": [
    { "id": 101, "title": "Review expired offers", "status": "OPEN", "createdAt": "2026-09-20T09:00:00Z" },
    { "id": 102, "title": "Approve pending partner applications", "status": "OPEN", "createdAt": "2026-09-22T14:30:00Z" }
  ]
}
```

It returns only `OPEN` items, oldest first.

### 6.5 Complete a to-do item

```powershell
Invoke-RestMethod -Method Patch "http://localhost:8080/api/v1/merchants/1/todos/101" `
  -Headers @{ 'X-API-Key' = $apiKey } -ContentType 'application/json' -Body '{"status":"COMPLETED"}'
```

```json
{ "id": 101, "merchantId": 1, "title": "Review expired offers", "status": "COMPLETED", "createdAt": "2026-09-20T09:00:00Z" }
```

- The only allowed change is `{"status":"COMPLETED"}`. Other fields in the body are ignored. Reopening (`"OPEN"`) returns `400`.
- Completing an item that is already completed returns `200` with the same result, so the call is safe to repeat.
- An item of another merchant returns `404`, the same as an id that doesn't exist.

### 6.6 Dates and periods

These rules apply to the summary and the revenue trend:

- `from` and `to` are required, in the format `YYYY-MM-DD`.
- The period includes `from` and excludes `to`. For example, `from=2026-09-01&to=2026-09-08` is 7 days, 1–7 September.
- Days start and end at midnight UTC.
- A period can be at most **366 days** (one full year, including leap years).

### 6.7 Errors

Errors are returned as JSON with the content type `application/problem+json` (RFC 9457):

```json
{ "status": 403, "title": "Forbidden", "detail": "The API key is not authorized for the requested merchant.", "instance": "/api/v1/merchants/2/todos" }
```

| Status | When |
|---|---|
| `400` | Missing or invalid dates, `from` not before `to`, a period over 366 days, an unsupported `granularity`, an invalid PATCH body, reopening a to-do, a non-numeric to-do id |
| `401` | The API key is missing, unknown or revoked |
| `403` | The merchant id in the URL isn't the key's merchant (including ids that aren't numbers) |
| `404` | The to-do item doesn't exist or belongs to another merchant, or the URL doesn't exist |
| `415` | The PATCH body is not sent as `application/json` |
| `500` | Unexpected server error. The response says only "Unexpected server error."; details go to the server log |

---

## 7. Tests

```
.\gradlew.bat test     # Windows
./gradlew test         # macOS
```

The HTML report is written to `build/reports/tests/test/index.html`.

- **Unit tests** cover date and period rules, percentage calculation and rounding, filling days without sales, API-key hashing, and the service logic.
- **Integration tests** call the endpoints and run the SQL against the real `awin_test` database. They cover the totals, period boundaries, transaction statuses, time zones, merchant isolation, to-do completion and every error status above.
- Each integration test empties the tables in `awin_test` first and inserts its own data. As a safety check, it refuses to run against any database other than `awin_test`, so your demo data in `awin` is never touched.
- API keys used by tests are generated at random while the tests run. No working key is stored in the repository.

**Running tests from an IDE:** if tests fail at startup with a Spring context error, reload the project (VS Code: *Java: Reload Projects*). The IDE has probably not picked up `src/test/resources`.

---

## 8. Reset the demo database

This deletes everything in `awin`, including API keys. Stop the app, then:

```powershell
# Windows (set $psql first, section 1.2)
& $psql -U postgres -h localhost -d postgres -c "DROP DATABASE awin;"
.\scripts\windows\create-databases.ps1 -Databases awin
```

```bash
# macOS
psql -U postgres -h localhost -d postgres -c "DROP DATABASE awin;"
./scripts/macos/create-databases.sh awin
```

The next start reloads the tables and the demo data. Then create API keys again (section 5).

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `password authentication failed for user "postgres"` | Your `postgres` password isn't `admin`. Load the `set-db-env` script or set `DB_PASSWORD` / `TEST_DB_PASSWORD` (section 3). |
| `role "postgres" does not exist` (macOS) | Create the `postgres` user (section 1.3). |
| `database "awin" does not exist` (or `awin_test`) | Run the `create-databases` script (section 2). |
| `Connection refused` on port 5432 | PostgreSQL isn't running. Windows: `Start-Service postgresql-x64-17`. macOS: `brew services start postgresql@17`. |
| `Port 8080 was already in use` | The app is already running in another terminal. Stop it, or start on another port: `.\gradlew.bat bootRun --args='--server.port=8081'` (macOS: `./gradlew bootRun --args='--server.port=8081'`). |
| A script "cannot be loaded because running scripts is disabled" (Windows) | Run `Set-ExecutionPolicy -Scope Process Bypass`, then try again. |
| `permission denied` running `./gradlew` or a script (macOS) | Run `chmod +x gradlew scripts/macos/*.sh` once. |
| `psql` is not recognized / `command not found` | Windows: use the `$psql` variable from section 1.2. macOS: add PostgreSQL to `PATH` (section 1.3). |
| Every API call returns `401` | The header is missing, the key is mistyped, or the key was created in another database. Create a new key (section 5). |
| `403` on a dashboard or to-do URL | The `{merchantId}` in the URL isn't the merchant that owns the key. |
| Tests fail in the IDE but pass with Gradle | Reload the IDE project (section 7). |

---

## 10. More information

- [`docs/requirements.md`](docs/requirements.md): scope, API contracts and business rules.
- [`docs/acceptedRisks.md`](docs/acceptedRisks.md): known limitations that were accepted.

**Simplifications in this project:**

- There are no user accounts. Each merchant has one API key, intended for server-to-server calls. A real browser dashboard would use a user login through a trusted backend instead.
- A transaction's current status applies to all reports, including past periods. This is not a financial ledger.
- Metrics are calculated from the raw data on each request, with no caching.
- Each merchant has one currency; there is no currency conversion.
