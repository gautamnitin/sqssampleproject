# SQS Employee Ingestion — Spring Boot + LocalStack + Swagger

This project demonstrates a high‑throughput SQS consumer using Spring Boot 3, Spring Cloud AWS SQS (3.x), and an H2 in‑memory database. It also includes a simple REST API to publish Employee messages to SQS and OpenAPI/Swagger UI for manual testing.

Contents
- Prerequisites
- How to run unit tests
- Start LocalStack (Docker) with SQS
- Configure the app to use LocalStack
- Run the application
- Test via Swagger UI
- Verify data in H2
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
The unit tests disable SQS at context startup, so they do not require AWS credentials or LocalStack.

PowerShell (Windows):

```powershell
# From the project root
./mvnw.cmd clean test
```

Bash (macOS/Linux):

```bash
./mvnw clean test
```

Expected result: 1 test runs and passes.

## Start LocalStack (Docker) with SQS
We provide a docker-compose file that starts LocalStack and automatically creates the `employee-queue` via an init script.

PowerShell:

```powershell
# From the project root
docker compose up -d

# Check container status
docker compose ps
```

LocalStack is exposed at `http://localhost:4566`.

Optional: verify queue (requires AWS CLI):

```powershell
aws --endpoint-url http://localhost:4566 sqs list-queues
```

You should see `employee-queue` in the output. The init script lives at `localstack/ready.d/01-create-queues.sh`.

## Configure the app to use LocalStack
The application uses an endpoint override when `app.sqs.endpoint` is set. For LocalStack, set it to `http://localhost:4566`.

Options to configure:

1) Command-line property when running the app
- `--app.sqs.endpoint=http://localhost:4566`

2) Environment variable (Spring Boot maps kebab-case to upper snake case)
- `APP_SQS_ENDPOINT=http://localhost:4566`

3) Temporary change in `src/main/resources/application.properties`
- Uncomment and set `app.sqs.endpoint=http://localhost:4566`

Note: Option 1 or 2 is preferred to avoid committing local changes.

## Run the application
Use Maven Spring Boot plugin:

PowerShell:

```powershell
# Using command-line properties (recommended)
./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--app.sqs.endpoint=http://localhost:4566"
```

If you prefer environment variable:

```powershell
$env:APP_SQS_ENDPOINT = "http://localhost:4566"
./mvnw.cmd spring-boot:run
```

When starting, the app will connect to LocalStack SQS. The queue `employee-queue` should already exist from the init script.

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

On submit, the REST controller publishes the message(s) to SQS. The SQS listener (`@SqsListener`) consumes them in batches and upserts records into H2 using JPA with batching enabled.

## Verify data in H2
H2 console is enabled.

- Open: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:employeesdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`
- User: `sa`
- Password: (empty)

Query example:

```sql
SELECT id, first_name, last_name, email, department, hired_at FROM employees;
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

## Troubleshooting
- Swagger UI not loading: ensure the app is running on port 8080 and `springdoc-openapi` dependency is present (it is in pom.xml). Use `/swagger-ui.html`.
- SQS connection errors: make sure LocalStack is up (`docker compose ps`) and `app.sqs.endpoint` is set to `http://localhost:4566`.
- Queue not found: the init script should create `employee-queue`. If needed, create it manually with AWS CLI:
  ```powershell
  aws --endpoint-url http://localhost:4566 sqs create-queue --queue-name employee-queue
  ```
- Tests failing due to SQS: unit test already disables SQS listener; re-run `./mvnw.cmd clean test`.
- H2 console login: use the exact JDBC URL above; username `sa`, no password.

---
Happy testing!
