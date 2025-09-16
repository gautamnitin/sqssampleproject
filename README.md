# SQS Employee Ingestion — Spring Boot + LocalStack + PostgreSQL

This project demonstrates a high‑throughput SQS consumer using Spring Boot 3, Spring Cloud AWS SQS (3.x), and PostgreSQL as the application database. It also includes a simple REST API to publish Employee messages to SQS and OpenAPI/Swagger UI for manual testing.

Contents
- Prerequisites
- How to run unit tests
- Start Docker services (PostgreSQL + LocalStack SQS)
- Configure the app to use LocalStack (SQS)
- Run the application
- Test via Swagger UI
- Verify data in PostgreSQL
- Test via cURL (optional)
- Troubleshooting

## Prerequisites
- Java 21
- Maven 3.9+
- Docker Desktop (with Docker Compose v2)
- Optional: AWS CLI (if you want to inspect SQS manually)

Project entry points:
- Spring Boot main class: `com.sqs.sqsproject.SqsProjectApplication`
- REST publisher endpoints: `EmployeePublisherController` at `/api/sqs/employees`
- Swagger UI: provided by springdoc-openapi

## How to run unit tests
Unit tests are configured to use an in‑memory H2 database and to disable SQS at startup. No external services are required for tests.

PowerShell (Windows):

```powershell
# From the project root
./mvnw.cmd clean test
```

Bash (macOS/Linux):

```bash
./mvnw clean test
```

Expected result: tests run and pass.

## Start Docker services (PostgreSQL + LocalStack SQS)
We provide a docker-compose file that starts:
- PostgreSQL 16 (database `employeesdb`, user `postgres`, password `postgres`)
- LocalStack (SQS)

PowerShell:

```powershell
# From the project root
docker compose up -d

# Check container status
docker compose ps
```

- PostgreSQL: `localhost:5432`
- LocalStack SQS: `http://localhost:4566`

Optional: verify SQS queue (requires AWS CLI):

```powershell
aws --endpoint-url http://localhost:4566 sqs list-queues
```

The queue `employee-queue` is created by the init script at `localstack/ready.d/01-create-queues.sh`.

## Configure the app to use LocalStack (SQS)
The application uses an endpoint override when `app.sqs.endpoint` is set. For LocalStack, set it to `http://localhost:4566`.

Options to configure:

1) Command-line property when running the app
- `--app.sqs.endpoint=http://localhost:4566`

2) Environment variable (Spring Boot maps kebab-case to upper snake case)
- `APP_SQS_ENDPOINT=http://localhost:4566`

3) Property file override
- `app.sqs.endpoint=http://localhost:4566` in `src/main/resources/application.properties`

Note: Option 1 or 2 is preferred to avoid committing local changes.

## Run the application
By default, the app is configured to use PostgreSQL at `jdbc:postgresql://localhost:5432/employeesdb` with `postgres/postgres`.

PowerShell:

```powershell
# Using command-line properties (recommended for SQS)
./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--app.sqs.endpoint=http://localhost:4566"
```

If you prefer environment variable:

```powershell
$env:APP_SQS_ENDPOINT = "http://localhost:4566"
./mvnw.cmd spring-boot:run
```

When starting, the app will connect to LocalStack SQS and to the local PostgreSQL container.

## Test via Swagger UI
Swagger UI is available once the app is running.

- Open: http://localhost:8080/swagger-ui.html
- Or: http://localhost:8080/swagger-ui/index.html

Endpoints in `EmployeePublisherController`:
- POST `/api/sqs/employees` — publish one employee
- POST `/api/sqs/employees/batch` — publish a list of employees

Example request bodies you can use in Swagger “Try it out”:

Single employee:
```json
{
  "firstName": "Alice",
  "lastName": "Johnson",
  "email": "alice.johnson@example.com",
  "department": "Engineering",
  "hiredAt": "2024-06-01T10:00:00Z"
}
```

Batch employees:
```json
[
  {
    "firstName": "Bob",
    "lastName": "Smith",
    "email": "bob.smith@example.com",
    "department": "IT",
    "hiredAt": "2023-01-15T09:30:00Z"
  },
  {
    "firstName": "Carol",
    "lastName": "Davis",
    "email": "carol.davis@example.com",
    "department": "Finance",
    "hiredAt": "2022-11-05T12:15:00Z"
  }
]
```

On submit, the REST controller publishes the message(s) to SQS. The SQS listener (`@SqsListener`) consumes them in batches and upserts records into PostgreSQL using Spring Data JPA with batching enabled.

## Verify data in PostgreSQL
Connect with any Postgres client (psql, DBeaver, etc.). Default credentials:

- Host: `localhost`
- Port: `5432`
- Database: `employeesdb`
- User: `postgres`
- Password: `postgres`

Example (psql):

```powershell
# Windows PowerShell using Docker's psql inside the container
docker exec -it postgres-db psql -U postgres -d employeesdb -c "SELECT id, first_name, last_name, email, department, hired_at FROM employees;"
```

You should see the records sent from Swagger.

## Test via cURL (optional)
If you prefer cURL instead of Swagger UI:

Single publish:
```powershell
curl -X POST http://localhost:8080/api/sqs/employees `
  -H "Content-Type: application/json" `
  -d '{
    "firstName":"Dana",
    "lastName":"Lee",
    "email":"dana.lee@example.com",
    "department":"HR",
    "hiredAt":"2024-02-20T08:00:00Z"
  }'
```

Batch publish:
```powershell
curl -X POST http://localhost:8080/api/sqs/employees/batch `
  -H "Content-Type: application/json" `
  -d '[
    {"firstName":"Eve","lastName":"King","email":"eve.king@example.com","department":"Sales","hiredAt":"2024-07-10T11:00:00Z"},
    {"firstName":"Frank","lastName":"Young","email":"frank.young@example.com","department":"Ops","hiredAt":"2024-07-11T11:00:00Z"}
  ]'
```

## jOOQ-based upsert repository
In addition to the JDBC implementation, the project now includes a jOOQ-based repository for Employee upserts:

- Class: `com.sqs.sqsproject.employee.EmployeeJooqRepository`
- Method: `int[] bulkUpsert(List<Employee> employees)`

It uses Spring Boot's jOOQ starter and the same PostgreSQL ON CONFLICT (by `email`) logic. The repository is not yet wired into `EmployeeService` (which still uses the JDBC version) to preserve existing behavior. You can inject and use `EmployeeJooqRepository` where desired, or update `EmployeeService` to depend on it instead.

## Troubleshooting
- Swagger UI not loading: ensure the app is running on port 8080 and `springdoc-openapi` dependency is present (it is in pom.xml). Use `/swagger-ui.html`.
- SQS connection errors: make sure LocalStack is up (`docker compose ps`) and `app.sqs.endpoint` is set to `http://localhost:4566`.
- Queue not found: the init script should create `employee-queue`. If needed, create it manually with AWS CLI:
  ```powershell
  aws --endpoint-url http://localhost:4566 sqs create-queue --queue-name employee-queue
  ```
- Database connection errors: ensure the `postgres` container is healthy (`docker compose ps`) and the connection information in `application.properties` matches the docker-compose service.
- Tests failing due to DB: tests use in‑memory H2 automatically via test-specific overrides in `InternalapiApplicationTests`.

---
Happy testing!


## Repository approaches: JPA vs JDBC vs jOOQ — pros/cons and performance

This project contains three ways to upsert Employees into PostgreSQL (ON CONFLICT by email). Each solves a slightly different problem and has different trade‑offs.

Approaches in this repo
- Spring Data JPA repository with native SQL
  - File: com.sqs.sqsproject.employee.EmployeeRepository
  - Methods: upsertByEmail (single row), bulkUpsertByEmail (set-based using unnest arrays)
- JDBC repository using a generic batch utility
  - File: com.sqs.sqsproject.employee.EmployeeJdbcRepository
  - Utility: com.sqs.sqsproject.jdbc.JdbcBulkUpsertUtil
- jOOQ repository using reflection over JPA entities
  - File: com.sqs.sqsproject.employee.EmployeeJooqRepository

How they work
- JPA (native SQL)
  - Uses a single INSERT ... SELECT FROM unnest(...) ... ON CONFLICT DO UPDATE statement for bulk upserts.
  - Binds arrays once and lets PostgreSQL expand them set‑wise on the server.
  - Also offers a single-row native upsert method.
- JDBC batch
  - Builds a parametrized INSERT ... ON CONFLICT ... DO UPDATE statement and executes it once per row via JdbcTemplate.batchUpdate.
  - Sends N statements in one roundtrip (as a batch) rather than one set-based statement.
- jOOQ (reflection-based)
  - Uses jOOQ DSL to build an INSERT .. ON CONFLICT .. DO UPDATE per row and batches them (dsl.batch(queries)).
  - Derives table/column/sequence from JPA annotations to make it more reusable for other entities.

Performance characteristics (PostgreSQL)
- Bulk upserts of large batches (e.g., thousands of rows):
  1) JPA native (unnest set-based) — usually fastest
     - Single SQL statement, server-side set processing, minimal client/server chattiness.
  2) JDBC batch — fast, slightly behind set-based for very large batches
     - One statement per row in the batch; still efficient but more parsing/ON CONFLICT checks than the single set-based statement.
  3) jOOQ batch (per-row) — similar to JDBC batch in this implementation
     - Comparable to JDBC; advantages come from DSL/type-safety rather than raw speed with the current per-row pattern.
- Small batches (tens of rows): all three are typically “fast enough,” and differences are small.
- Extremely large payloads: prefer set-based INSERT ... SELECT (JPA native here) to minimize per-row overhead.

Pros and cons
- JPA native (unnest)
  - Pros: top performance for large batches; single SQL statement; concise caller code; stays within Spring Data transaction model; easy to call; no extra libraries.
  - Cons: SQL is PostgreSQL-specific (unnest + ON CONFLICT); less type-safe; debugging SQL still manual; requires constructing arrays and keeping lengths aligned by the caller.
- JDBC batch
  - Pros: very fast; simple/explicit; fully under your control; no extra runtime beyond Spring JDBC; easy to profile; portable to other RDBMS with minor SQL tweaks.
  - Cons: more boilerplate (SQL strings, parameter binding, conversions); one statement per row; harder to generalize across entities without more utility code.
- jOOQ (reflection-based)
  - Pros: expressive DSL; safer SQL construction; reusable across entities due to reflection; centralizes naming/sequence logic; easy to extend with jOOQ features.
  - Cons: current implementation sends one INSERT per row (batched) — not as fast as single set-based insert; adds jOOQ dependency; reflection has some runtime overhead; still PostgreSQL-specific for ON CONFLICT behavior.

Operational considerations
- Observability: JDBC and jOOQ make it straightforward to log the generated SQL (jOOQ can render SQL with bindings). JPA native queries can also be logged but require enabling SQL logging.
- Portability: All three use PostgreSQL ON CONFLICT, so they are Postgres-centric. If cross‑DB support is required, abstraction or vendor-specific paths will be needed.
- Transactions: All three integrate cleanly with Spring’s @Transactional. Ensure reasonable batch sizes (e.g., 500–2000) to avoid long transactions and memory pressure.
- Constraints/indexes: Performance depends heavily on a proper unique index over the conflict target (email) and on table bloat/maintenance (VACUUM/ANALYZE).

Which is best for performance?
- For the highest throughput with large batches: the JPA native bulk method that uses a single INSERT ... SELECT FROM unnest(...) ... ON CONFLICT typically wins.
- Close second: JDBC batch upsert (per-row) — still excellent and often simpler to reason about operationally.
- jOOQ (current per-row batch) performs on par with JDBC batch but shines more for maintainability/type-safety and multi-entity reuse than absolute peak speed.

Recommended default
- If your primary goal is raw bulk upsert performance on PostgreSQL, use the Spring Data JPA native bulk method (unnest) provided in EmployeeRepository.
- If you need explicit control and minimal dependencies, or want easier portability to other DBs, choose the JDBC utility.
- If you prefer a fluent, type-safe DSL and plan to generalize upsert across multiple entities with consistent naming/sequence rules, choose the jOOQ repository. Consider enhancing it to use multi-row INSERT or set-based operations for even higher throughput.

Tuning tips
- Use sensible batch sizes (e.g., 500–2000) for JDBC/jOOQ batching.
- Keep only necessary columns in the DO UPDATE SET list.
- Ensure autovacuum is healthy and the conflict index (email) is not bloated.
- Measure under realistic workloads: row size, indexes, triggers, and network latency all influence results.
