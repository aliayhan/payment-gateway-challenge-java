# Reasoning & Assessment Analysis

This document compares the current implementation directly against the **Checkout.com Payment Gateway assessment requirements** (`cko-recruitment`), critiques what was broken or incomplete in the starter repository (`origin/main`), and categorizes every addition beyond the core specification.

---

## 1. 1:1 Comparison Against Hard Requirements

> Scope: Checked strictly against the `cko-recruitment` assessment specification.

| Hard Requirement | Status | Current Implementation |
| --- | --- | --- |
| **POST payment endpoint** accepting 6 required fields (`card_number`, `expiry_month`, `expiry_year`, `currency`, `amount`, `cvv`) | **1:1 Fulfilled** | [PaymentGatewayController.submitPayment](src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java#L126-L131) + [PostPaymentRequest](src/main/java/com/checkout/payment/gateway/controller/api/PostPaymentRequest.java) |
| **Card number**: 14–19 numeric characters | **1:1 Fulfilled** | [PaymentRequestValidator.validateCardNumber](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L48-L59) |
| **Expiry month**: 1–12 | **1:1 Fulfilled** | [PaymentRequestValidator.validateExpiry](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L61-L72) |
| **Expiry year + month**: combination must be in the future | **1:1 Fulfilled** | [PaymentRequestValidator.validateExpiry](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L74-L90) |
| **Currency**: 3 characters, max 3 supported ISO currency codes | **1:1 Fulfilled** | `GBP`, `USD`, `EUR` in [PaymentRequestValidator.validateCurrency](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L23) |
| **Amount**: integer in minor currency units (e.g., `1050` for `10.50`) | **1:1 Fulfilled** | `Integer amount` in `PostPaymentRequest` + [validateAmount](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L106-L114) |
| **CVV**: 3–4 numeric characters | **1:1 Fulfilled** | [PaymentRequestValidator.validateCvv](src/main/java/com/checkout/payment/gateway/validation/PaymentRequestValidator.java#L116-L127) |
| **Status `Authorized` / `Declined` / `Rejected`** (`Rejected` returns before bank call & creates no payment) | **1:1 Fulfilled** | `PaymentRequestValidator.validateOrThrow` runs before `acquiringBankClient.submitPayment` in [PaymentGatewayService](src/main/java/com/checkout/payment/gateway/service/PaymentGatewayService.java#L41-L62) |
| **Return & retrieve (`GET /payment/{id}`)**: ID, status (`Authorized`/`Declined`), last 4 card digits, expiry month, expiry year, currency, amount | **1:1 Fulfilled** | [PaymentResponse](src/main/java/com/checkout/payment/gateway/controller/api/PaymentResponse.java) returned on both `POST /payment` and `GET /payment/{id}` |
| **In-memory repository** storing both `Authorized` and `Declined` payments | **1:1 Fulfilled** | [PaymentsRepository](src/main/java/com/checkout/payment/gateway/repository/PaymentsRepository.java) |
| **Bank simulator integration** (`POST /payments` with `card_number`, `expiry_date` as `MM/yyyy`, `currency`, `amount`, `cvv`), simulator config kept intact, `503` handling | **1:1 Fulfilled** | [HttpAcquiringBankClient](src/main/java/com/checkout/payment/gateway/client/HttpAcquiringBankClient.java) + [BankPaymentRequest](src/main/java/com/checkout/payment/gateway/client/api/BankPaymentRequest.java); `imposters/bank_simulator.ejs` untouched vs `origin/main` |
| **JDK 17 compilation & automated test coverage** | **1:1 Fulfilled** | `sourceCompatibility = '17'` in [build.gradle](build.gradle) + unit, integration, component, and mutation suites |

**Result:** All hard requirements are **1:1 fulfilled** with zero functional gaps.

---

## 2. Issues & Flaws in the Starter Code (`origin/main`)

1. **Broken `PostPaymentRequest` contract & types**
   - Mapped `@JsonProperty("card_number_last_four") private int cardNumberLastFour` instead of `@JsonProperty("card_number") String cardNumber` (a 14–19 digit card number does not even fit in a 32-bit `int`).
   - Used primitive `int` for `cvv`, `expiryMonth`, `expiryYear`, and `amount`:
     - Missing JSON fields defaulted silently to `0` instead of `null`.
     - Numeric `cvv` dropped leading zeros (e.g., `"099"` became `99`).
   - `getExpiryDate()` formatted `"%d/%d"` (producing `"4/2028"` instead of the simulator's `"MM/yyyy"` `"04/2028"`).
   - `toString()` printed both `cardNumberLastFour` (actually the card field) and `cvv` in plaintext.

2. **Duplicate & buggy response DTOs**
   - Both `GetPaymentResponse` and `PostPaymentResponse` existed with identical fields, mutable getters/setters, and `PostPaymentResponse.toString()` accidentally printing `"GetPaymentResponse{..."`. `GetPaymentResponse` was completely unused.
   - `cardNumberLastFour` was typed as `int` instead of `String`, losing leading zeros on cards ending in e.g. `0042` (`42` instead of `"0042"`).

3. **Non-thread-safe `PaymentsRepository` (`HashMap` vs `ConcurrentHashMap`)**
   - `PaymentsRepository` used `java.util.HashMap`, which is not thread-safe. Because Spring MVC (Tomcat) handles incoming HTTP requests concurrently across worker threads, simultaneous `put`/`get` operations can cause lost updates, stale reads, corrupted bucket/tree state, or `ConcurrentModificationException` (if iterated).
   - **Verification on `HashMap` vs `ConcurrentHashMap` fail-fast vs fail-safe:**
     - `HashMap`'s *iterators* are fail-fast (`ConcurrentModificationException`), **not** `put`/`get` across threads—concurrent `put`/`get` calls do not reliably throw; they fail silently with race conditions or corrupt state.
     - `ConcurrentHashMap` is thread-safe (CAS + per-bucket synchronization, lock-free reads) and its iterators/views are **fail-safe / weakly consistent** rather than throwing `ConcurrentModificationException`.
   - Storing an immutable Java `record` ([Payment](src/main/java/com/checkout/payment/gateway/model/Payment.java)) instead of mutable response beans also guarantees safe publication without accidental post-save mutation.

4. **Missing implementation & leftover boilerplate**
   - `POST /payment` and the outbound acquiring bank HTTP client were not implemented at all (`PaymentGatewayService.processPayment` merely returned `UUID.randomUUID()`).
   - `EventProcessingException` was leftover template boilerplate mapped to `404` (`"Page not found"`) when a payment ID was missing.

5. **Exception handling & operational visibility**
   - `CommonExceptionHandler` lacked separation between domain/application exceptions (`PaymentValidationException`, `PaymentNotFoundException`, `BankUnavailableException`), Spring binding/JSON errors (`HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`), Spring MVC negotiation errors (`405`, `415`, `406`), and unexpected `500`s.

6. **Package structure & configuration**
   - DTOs, domain models, and enums were not cleanly separated; `ApplicationConfiguration` hardcoded `10000ms` connect/read timeouts.
   - Minimal tests (`PaymentGatewayControllerTest` only had two tests, one asserting `"Page not found"`, and no bank client, validation, component, or mutation tests).

---

## 3. Additions Beyond the Hard Requirements

### A. Assumptions on Unsaid Requirements
- **Positive minor-unit amount (`amount > 0`):** Rejects `0` and negative amounts (`AMOUNT_NOT_POSITIVE`) rather than allowing zero-value payments.
- **Current-month expiry rule (`YearMonth >= YearMonth.now(ZoneOffset.UTC)`):** Cards expiring in the current UTC month are treated as valid through the end of that month (standard card-industry convention).
- **Exact supported currency set (`GBP`, `USD`, `EUR`):** Case-sensitive uppercase ISO 4217 validation.
- **ASCII-only digits (`0–9`) for `card_number` and `cvv`:** Rejects non-ASCII Unicode numerals (such as Arabic-Indic digits) that `Character.isDigit` would accept.
- **Leading-zero preservation (`String`):** Kept `card_number`, `cvv`, and `cardNumberLastFour` as `String`s.
- **Bank failure & `503` semantics without retries:** Any upstream bank failure (`503`, unexpected status, timeout, connection failure, or deserialization error) maps to `503 Service Unavailable` without persisting a payment or automatically retrying (preventing duplicate/ambiguous charges).
- **HTTP status & Spring error hygiene:**
  - `POST /payment` returns `201 Created` (rather than `200 OK`).
  - Malformed JSON (`HttpMessageNotReadableException`) returns `400 Bad Request` (`Rejected`).
  - Non-UUID path ID (`MethodArgumentTypeMismatchException`) returns `400 Bad Request`.
  - Spring MVC `405` / `415` / `406` errors return JSON with their standard HTTP status and headers (`Allow`, etc.) instead of falling into a `500` catch-all.

### B. Minor Adjustments
- **`Location` response header on `POST /payment`:** Returns `Location: /payment/{id}` for RESTfulness so merchants can follow the URI directly to `GET /payment/{id}`.
- **Clean package structure & domain/DTO separation:**
  - Separated inbound HTTP DTOs (`controller/api`), outbound bank DTOs (`client/api`), domain entity/enums/exceptions (`model`, `model/enums`, `model/exception`), and `configuration` / `configuration/observability`.
  - Extracted `AcquiringBankClient` interface cleanly decoupling service logic from `HttpAcquiringBankClient`.
- **Immutable `record` models + redacted `toString()`:** Converted DTOs and stored `Payment` to records; redacted `cardNumber` and `cvv` in `PostPaymentRequest.toString()` and `BankPaymentRequest.toString()`.
- **Externalized configuration (`application.properties` + `BankClientProperties`):** Moved `bank.client.base-url` (with `BANK_SIMULATOR_BASE_URL` env override) and the `10000ms` connect/read timeouts out of hardcoded Java constants into properties.
- **OpenAPI / Swagger enrichment:** Added `@Operation`, `@ApiResponses`, `@Header`, `@Schema`, and example payloads on [PaymentGatewayController](src/main/java/com/checkout/payment/gateway/controller/PaymentGatewayController.java) and DTOs, plus `paymentGatewayOpenApi()` metadata in [ApplicationConfiguration](src/main/java/com/checkout/payment/gateway/configuration/ApplicationConfiguration.java).

### C. Minor+ (Borderline Overengineering for a Take-Home)
- **Centralized Correlation IDs & Request Summary Logging:** [RequestLoggingFilter](src/main/java/com/checkout/payment/gateway/configuration/observability/RequestLoggingFilter.java) (`OncePerRequestFilter`) binds `X-Request-Id` into SLF4J `MDC` (`ThreadLocal`), sets `X-Request-Id` on every response, propagates it to the outbound bank call (`HttpAcquiringBankClient`), and emits single-line `request_summary` logs formatted via `logging.pattern.level` in [application.properties](src/main/resources/application.properties).
- **Rich error catalog inside `PaymentValidationException`:** `PaymentRequestValidator` collects a full `List<PaymentRequestError>` (`PR_ERR_*` codes + safe messages) and attaches it to `PaymentValidationException`. Currently [CommonExceptionHandler](src/main/java/com/checkout/payment/gateway/configuration/CommonExceptionHandler.java#L35-L40) intentionally stops at returning a single summary message (`"Payment request validation failed"`), while keeping the field-level error list ready if future requirements ask for detailed error arrays.
- **Docker / Testcontainers Cucumber Component Tests (`src/componentTest`):** Builds and runs the gateway container alongside Mountebank (`bank_simulator`) via Testcontainers to verify black-box end-to-end behavior.
- **PITest Mutation Testing & Segregated Gradle Test Suites:** Configured `unit-test`, `integration-test`, `component-test`, `mutation-test` (`pitest` with 90% coverage/mutation thresholds), and `test-all` in [build.gradle](build.gradle).
- **Static `YearMonth.now(ZoneOffset.UTC)` Mockito Mocking:** Deterministic date-boundary unit tests via `Mockito.mockStatic(YearMonth.class)` without altering production signatures to inject a `Clock`.
- **Forward-Looking Roadmap ([ROADMAP.md](ROADMAP.md)):** Planning notes for 0.2 (idempotency & reconciliation to stop double payments) and 0.3 (operational readiness—auth/OAuth 2.0, circuit breaking, time budgets, rate limiting, shared storage, OpenTelemetry/Prometheus—followed by PII log compliance, multi-tenancy, and audit trail).

---

## 4. Testing Approach

- **Deliberate TDD (Red → Green):** Once the design was clear and contracts were set as empty methods with defined parameters and return types, tests were written first against the hard requirements so every test initially failed (**Red**), then production classes were implemented step-by-step until green.
- **Ruby (Diamond) Model over the Traditional Test Pyramid:** JaCoCo coverage is anchored by combining `unit-test` (`src/test/java`) and `integration-test` (`src/integrationTest/java`) execution data rather than forcing a unit-heavy pyramid. With LLM-assisted authoring, Spring/HTTP integration tests are nearly as quick to write and run as unit tests, making a Ruby-shaped suite (focused unit tests + rich integration coverage) well suited to a microservice of this size.
- **Component Tests (`src/componentTest`):** Black-box BDD Cucumber scenarios running the containerized gateway against the Mountebank `bank_simulator` via Testcontainers.
- **PITest (`./gradlew mutation-test`):** Mutation testing on `validation.*`, `service.*`, and `client.*` (`>= 90%` coverage and mutation thresholds) to ensure assertions catch real logic defects, not just execute lines.
