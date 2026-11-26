# Payment Gateway

Spring Boot API for validating card payment requests, forwarding them to the acquiring bank simulator, and retrieving masked payment records by ID.

For design trade-offs, test philosophy, and starter-code fixes, see [REASONING.md](REASONING.md). For planned 0.2/0.3 production extensions, see [ROADMAP.md](ROADMAP.md).

## Prerequisites

- **JDK 17**
- **Docker / Docker Compose** (needed for the bank simulator and component tests)

## Starting the Program

### Option A: Everything in Docker (recommended for quick testing)

```bash
docker compose up -d --build
```

Starts `bank_simulator` (`http://localhost:8080`) and `payment_gateway` (`http://localhost:8090`).

### Option B: Bank simulator in Docker + Gateway on host

```bash
docker compose up -d bank_simulator
./gradlew check bootJar
java -jar build/libs/payment-gateway-challenge-java-0.0.1-SNAPSHOT.jar
```

### Ports, Config & API Docs

| Setting | Default | Property / Env Var |
| --- | --- | --- |
| Gateway Port | `8090` | `server.port` (`SERVER_PORT`) |
| Bank Simulator URL | `http://localhost:8080` | `bank.client.base-url` (`BANK_SIMULATOR_BASE_URL`) |
| Bank Connect Timeout | `10000ms` | `bank.client.connect-timeout-ms` |
| Bank Read Timeout | `10000ms` | `bank.client.read-timeout-ms` |

- **Swagger UI:** http://localhost:8090/swagger-ui/index.html
- **OpenAPI JSON:** http://localhost:8090/v3/api-docs

## Testing Use Cases via `curl`

### 1. Authorized payment (`201 Created`) + retrieve (`200 OK`)

Card numbers ending in an odd digit (`1, 3, 5, 7, 9`) are authorized:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -d '{
    "card_number": "2222405343240041",
    "expiry_month": 12,
    "expiry_year": 2028,
    "currency": "GBP",
    "amount": 1050,
    "cvv": "099"
  }'
```

Use the `id` or `Location` header from the response (`/payment/<uuid>`) to fetch the masked record (`cardNumberLastFour` preserves leading zeros like `"0041"`):

```bash
curl -i http://localhost:8090/payment/<uuid>
```

### 2. Declined payment (`201 Created`)

Card numbers ending in an even digit (`2, 4, 6, 8`) are declined and stored for retrieval via `GET /payment/<uuid>`:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -d '{
    "card_number": "2222405343240042",
    "expiry_month": 12,
    "expiry_year": 2028,
    "currency": "USD",
    "amount": 2500,
    "cvv": "123"
  }'
```

### 3. Bank simulator unavailable (`503 Service Unavailable`)

Card numbers ending in `0` trigger a bank `503`; no payment is saved:

```bash
curl -i -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -d '{
    "card_number": "2222405343248870",
    "expiry_month": 12,
    "expiry_year": 2028,
    "currency": "EUR",
    "amount": 500,
    "cvv": "123"
  }'
```

### 4. Invalid request (`400 Bad Request` — `"status": "Rejected"`)

Rejected locally before calling the bank (supported currencies: `GBP`, `USD`, `EUR`; amount `> 0`; card `14–19` digits; CVV `3–4` digits):

```bash
curl -i -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -d '{
    "card_number": "4111BAD",
    "expiry_month": 13,
    "expiry_year": 2020,
    "currency": "JPY",
    "amount": 0,
    "cvv": "9"
  }'
```

### 5. Lookup errors (`404 Not Found` and `400 Bad Request`)

```bash
curl -i http://localhost:8090/payment/550e8400-e29b-41d4-a716-446655440000  # 404 ID not found
curl -i http://localhost:8090/payment/not-a-uuid                            # 400 Invalid UUID format
```

## Running the Test Suites

| Command | Scope | Requires Docker |
| --- | --- | --- |
| `./gradlew unit-test` | Pure unit tests in `src/test/java` (repository, validator, service) | No |
| `./gradlew integration-test` | HTTP client & Spring MockMvc tests in `src/integrationTest/java` | No |
| `./gradlew component-test` | Black-box Cucumber tests against containerized gateway + simulator (`src/componentTest`) | Yes (starts containers automatically) |
| `./gradlew mutation-test` | PITest mutation analysis on `validation.*`, `service.*`, and `client.*` | No |

Aggregate / Gradle lifecycle tasks:
- **`./gradlew test`** — Speedy unit-test run (`src/test/java`).
- **`./gradlew check`** — Runs all Docker-free checks (`test`, `integration-test`, JaCoCo coverage verification, and `mutation-test`).
- **`./gradlew test-all`** — Runs everything (`check` plus Docker `component-test`).

Generated reports:
- Unit / `test`: `build/reports/tests/test/index.html` and `build/reports/tests/unit-test/index.html`
- Integration: `build/reports/tests/integration-test/index.html`
- JaCoCo coverage: `build/reports/jacoco/test/html/index.html`
- Component: `build/reports/tests/component-test/index.html`
- PITest mutation: `build/reports/pitest/index.html`
