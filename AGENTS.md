# AGENTS.md

Instructions for AI coding assistants working in this repository. Focuses on project-specific standards, workflow, and guardrails; read [README.md](README.md), [REASONING.md](REASONING.md), and [ROADMAP.md](ROADMAP.md) for full context.

## Project Map
- `controller/` & `controller/api/` — Inbound REST endpoints and DTOs (`PostPaymentRequest`, `PaymentResponse`, `ErrorResponse` with OpenAPI `@Schema`).
- `client/` & `client/api/` — Outbound bank client (`AcquiringBankClient`, `HttpAcquiringBankClient`) and outbound DTOs (`BankPaymentRequest`, `BankPaymentResponse`, no Swagger `@Schema`).
- `model/` (`enums/`, `exception/`) — Domain `Payment` record, `PaymentStatus`, and gateway exceptions.
- `service/` & `validation/` — Business orchestration (`PaymentGatewayService`) and rules (`PaymentRequestValidator`, `PaymentRequestError`).
- `repository/` — Thread-safe `ConcurrentHashMap` in-memory store (`PaymentsRepository`).
- `configuration/` & `configuration/observability/` — Spring beans (`ApplicationConfiguration`, `BankClientProperties`), grouped `CommonExceptionHandler`, and `RequestLoggingFilter` (`X-Request-Id` / `MDC`).

## Commands & Verification
- Unit tests (`src/test/java`): `./gradlew unit-test`
- Integration tests (`src/integrationTest/java`): `./gradlew integration-test`
- Single test class: `./gradlew unit-test --tests "<fqcn>"` or `./gradlew integration-test --tests "<fqcn>"`
- All Docker-free checks (`test` + `integration-test` + JaCoCo + `mutation-test`): `./gradlew check`
- Containerized Cucumber E2E tests (requires Docker): `./gradlew component-test`
- Mutation testing (PITest on `validation.*`, `service.*`, `client.*`): `./gradlew mutation-test`
- Full verification suite (`check` + `component-test` + `mutation-test`): `./gradlew test-all`

Always run `./gradlew check` (or `./gradlew test-all` when touching Docker/main logic) after your final code edit before reporting completion.

## Engineering Standards (KISS, Clean, YAGNI)
- **No speculative over-engineering:** Only implement what the current task or hard requirements ask for. Leave 0.2/0.3 extensions (idempotency, auth, circuit breaker, rate limiting, PII log scrubbing) in [ROADMAP.md](ROADMAP.md) until requested.
- **Trust explicit contracts:**
  - Keep Jackson defaults unless strict coercion is explicitly requested.
  - Trust upstream DTO contracts (`BankPaymentResponse.authorized` is primitive `boolean`) and internal guarantees (`RequestLoggingFilter` populates `MDC` for HTTP requests).
  - Prefer `catch (Exception ex)` with concise structured logging over nested exception-cause inspection trees.
- **Don't alter production signatures just for tests:** Use `YearMonth.now(ZoneOffset.UTC)` directly in `PaymentRequestValidator` and mock it statically in unit tests with Mockito rather than injecting a `Clock`.
- **Use immutable records & preserve card formatting:** Keep DTOs and `Payment` as Java `record`s; store card numbers, CVVs, and `cardNumberLastFour` as `String` (preserving leading zeros like `"0042"`); redact sensitive fields in `toString()`.
- **Externalize operational config:** Bind client URLs and timeouts via `@ConfigurationProperties` (`BankClientProperties` + `application.properties`), not hardcoded magic numbers.
- **Keep error handling grouped:** Separate application exceptions, internal Spring MVC binding/JSON exceptions, HTTP negotiation errors (`405`/`406`/`415`), and the `500` fallback in `CommonExceptionHandler`.
- **Be explicit about requirements vs assumptions:** Distinguish `cko-recruitment` hard requirements from design assumptions (e.g., `amount > 0`, `GBP`/`USD`/`EUR`, current-month expiry, `Location` header).

## Testing Strategy (TDD + Ruby Model)
- **TDD Red → Green when implementing new modules:** Define clean empty method/constructor contracts first, write failing tests from requirements (confirming **0 tests pass** in Red), then implement class-by-class until green.
- **Ruby / Diamond coverage model:** Coverage is enforced via merged `test` + `integration-test` JaCoCo reports (`./gradlew check`, minimum `90%` line coverage; aim for `100%` on non-boilerplate code).
  - Exclude only pure boilerplate/data types by explicit name in `build.gradle`: `PaymentGatewayApplication`, `ApplicationConfiguration`, pure data records (`Payment`, `BankClientProperties`, `BankPaymentResponse`), enums/`PaymentRequestError`, and exceptions.
  - Keep records with custom methods (`from()`, `rejected()`, `toString()`), `CommonExceptionHandler`, and `RequestLoggingFilter` included in JaCoCo.
- **PITest (`>= 90%`) & Component tests:** Keep PITest thresholds at `90%` on `validation.*`, `service.*`, and `client.*`; reserve `src/componentTest` for black-box Docker BDD journeys.
- **Test each concern at the right layer:** Keep unit tests strictly isolated with mocks (`PaymentGatewayServiceTest`) and test concurrency directly on `ConcurrentHashMap` in `PaymentsRepositoryTest` using `CountDownLatch` with enough volume (`10 × 500`) to catch `HashMap` races reliably rather than adding slow HTTP concurrency loops.

## Documentation & Tone
- Keep Markdown docs (`README.md`, `REASONING.md`, `ROADMAP.md`) concise, human-sounding, prioritized, and free of cross-file duplication.

## Boundaries / What Not To Do
- Do **not** modify `imposters/bank_simulator.ejs` or `.editorconfig`.
- Do **not** add new dependencies, frameworks, or `@Schema` annotations on outbound `client/api` DTOs without explicit approval.
- Do **not** commit or push changes unless explicitly asked.
