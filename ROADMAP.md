# Payment Gateway Roadmap

Planned improvements beyond the 0.1 implementation in [README.md](README.md): **Version 0.2** focuses on stopping double payments during retries/crashes, and **Version 0.3** focuses on making the service operationally production-ready first, then extending business and compliance features.

---

## Version 0.2 (TBD): Preventing Double Payments (Idempotency & Recovery)

Allow merchants to safely retry `POST /payment` after timeouts or dropped connections without creating duplicate charges, and retain uncertain attempts for reconciliation.

### Merchant API
- Require an `Idempotency-Key` header on `POST /payment`, scoped per merchant (defaulting to the single demo merchant).
- **Safe replay:** If the key already completed (`Authorized` or `Declined`), return the original `201 Created`, `Location: /payment/{id}`, and body without calling the bank again.
- **Payload mismatch:** Return `409 Conflict` if the same key is reused with different `amount`, `currency`, expiry, or card identity (checked via a keyed card fingerprint—never storing raw card numbers or CVVs).

### Stateful 0.2 Bank Simulator
Keep the 0.1 simulator intact and run a stateful 0.2 variant backed by its own file store (storing reservations and decisions without full card numbers or CVVs):

| Endpoint | Behavior |
| --- | --- |
| `POST /payment-attempts` | Creates a 24-hour reservation (`201 Created` with `bankAttemptId` and `expiresAt`) with no payment side effect. |
| `POST /payments` | Accepts the payment payload with `Idempotency-Key: <bankAttemptId>`. Atomically binds one `Authorized`/`Declined` result to a valid reservation; replays return the stored outcome and auth code; `503` leaves it unresolved. |
| `GET /payment-attempts/{bankAttemptId}` | Reads reservation state (`UNUSED`, `IN_PROGRESS`, or final decision) without side effects, or returns `404` if missing/expired. |

### Gateway Flow & Recovery
Persist one durable record per `(merchant, idempotencyKey)` (gateway `paymentId`, request fingerprint, masked card details, `bankAttemptId`, `expiresAt`, and state) in the same file-backed store as `Payment` so final outcomes commit atomically:
1. Validate the request, obtain a bank reservation (`bankAttemptId`), and commit the mapping locally.
2. Mark the attempt as *potentially submitted* before sending `POST /payments`.
3. Send `POST /payments` with `bankAttemptId`, then atomically save the payment and mark the attempt complete before returning `201 Created`.
4. On retries, replay completed local results; otherwise reconcile via `GET /payment-attempts/{bankAttemptId}`:

| State on retry | Gateway action |
| --- | --- |
| Mapping never committed | Request a fresh bank reservation (nothing could have been sent). |
| Committed, submission never started, reservation valid | Mark submission started and send `POST /payments` with the saved `bankAttemptId`. |
| Committed, submission never started, reservation expired | Replace with a new `bankAttemptId` on the same record (keeping `paymentId`). |
| Potentially submitted; bank holds a final decision | Save and return that decision with the original `paymentId`. |
| Potentially submitted; bank still shows valid unused reservation | Retry `POST /payments` with the original `bankAttemptId`. |
| Bank still processing or status unavailable | Leave pending/unresolved; merchant retries later with the same key. |
| Potentially submitted; bank record expired or missing | Leave unresolved for manual reconciliation—do not re-submit. |
| Outcome already committed locally | Replay `201 Created` without contacting the bank. |

### Verification
- Inject failures into persistent test stores and a fake bank across every crash boundary (before mapping commit, after submission marker, lost bank response after bank commit, failed local save, lost gateway response).
- Verify fixed-clock expiry rules (unused expired reservations can be replaced; potentially submitted ones stay unresolved; completed payments still replay), single-authorization concurrency under same-key races, and `409 Conflict` before calling the bank.

---

## Version 0.3 (TBD): Becoming Operational First, Then Extending Features

### Part A: Operational Readiness *(Ranked by Priority)*

#### 1. Authentication *(Priority: Critical)*
- Add `POST /login` issuing short-lived, stateless bearer tokens (`401 Unauthorized` when missing, invalid, or expired) validatable across replicas without HTTP sessions.
- Later, delegate token issuance to the company-wide OAuth 2.0 provider (validating issuer, audience, signing keys, and scopes).

#### 2. Fault Isolation, Resilience & Time Budgets *(Priority: High)*
- **Time budget & timeouts:** Make the end-to-end payment budget configurable (e.g., `5s`, depending on target SLOs) and align `bank.client.connect-timeout-ms` and `bank.client.read-timeout-ms` (currently `10000ms`) so bank calls leave headroom to save and respond.
- **Circuit breaker:** Wrap bank calls in a circuit breaker counting technical failures (timeouts, `5xx`) while treating `Authorized`/`Declined` as healthy. Fail fast with `503 Service Unavailable` when open, probe carefully after cooldown, and never auto-retry timeouts.
- **In-flight bank call cap:** Bound concurrent outbound bank calls per instance (fail fast with `503` when full—no queueing) so slow bank responses cannot exhaust Tomcat threads and starve `GET /payment/{id}`.

#### 3. Rate Limiting *(Priority: High)*
- Enforce a configurable per-client rate limit (e.g., `5 requests/second` depending on target SLOs) keyed by `(merchant, HTTP method, route template)` using a rolling window (`GET /payment/{id}` calls share one bucket).
- Return `429 Too Many Requests` with `Retry-After` when exceeded. Use a local limiter for the single-instance demo and an API gateway limiter for multi-replica deployments (assuming edge DDoS protection).

#### 4. Shared Durable Storage & Scaling *(Priority: Medium)*
- Keep `ConcurrentHashMap` + immutable `Payment` records for single-instance/test runs, and introduce a shared durable store written before `201 Created` so stateless round-robin routing works across replicas.
- **Scale only when measured:** Track p95/p99 commit latency, pool wait, lock contention, and deadlocks before adding short optimistic-locked transactions, write partitioning/sharding, or a bounded `GET /payment/{id}` cache (never caching `404`).

#### 5. Observability & Verification *(Priority: Medium)*
- **Logs & tracing:** Extend 0.1 summary logs with `bankDurationMs` and bounded failure categories (`timeout`, `rate_limited`, `capacity_exhausted`, `circuit_open`) correlated by `X-Request-Id`; add OpenTelemetry distributed tracing across gateway, bank, and database.
- **Prometheus (`/actuator/prometheus`):** Expose request and bank latency histograms (with a bucket at the payment budget) and outcome counters (`Authorized`, `Declined`, `Rejected`, technical failures) using bounded labels only (route + outcome category—no IDs), plus alerts separating definitive bank decisions from fast technical failures.
- **Additional E2E/system, performance/load & smoke tests:** Add full **E2E/system tests** (token expiry/`401`, clock-controlled rate limits, held bank calls not blocking `GET`s, circuit recovery without retries, cross-replica lookups), **performance/load tests** (measuring throughput, tail latency percentiles, saturation, and budget compliance under concurrency), and **post-deploy smoke tests** (verifying health, downstream bank connectivity, and basic payment flow in live/staging environments).

#### 6. Fine-Grained Error Reporting *(Priority: Low / Optional)*
- Surface richer safe failure detail where client contracts need it—for instance returning field-level validation `errors` on `400 Rejected` (using `PaymentRequestError` on `PaymentValidationException`) or returning distinct HTTP statuses per upstream failure (`504 Gateway Timeout` on bank timeout, `502 Bad Gateway` on connection or invalid-response errors) instead of a uniform `503 Service Unavailable`.

### Part B: Business & Compliance Feature Extensions *(Ranked by Priority)*

#### 1. PII / PCI Data Removal & Compliance *(Priority: Critical)*
- Keep card numbers, CVVs, credentials, tokens, and raw payloads out of logs, exception messages, and audit records—scrub `Throwable` messages/stack traces at the logging-framework level (e.g., Logback masking) or log only allowlisted fields and bounded failure categories.
- Before production, define field retention, access controls, and deletion rules compliant with UK GDPR and PCI DSS.

#### 2. Multi-Tenancy *(Priority: High)*
- Derive `merchantId` from authentication, store ownership at payment creation, and scope `GET /payment/{id}` lookups and rate limits by `(merchantId, paymentId)` so tenants cannot read or exhaust each other's resources.

#### 3. Payment Audit Trail *(Priority: Medium)*
- Persist an append-only audit log (`timestamp`, actor, action, outcome, `requestId`, `paymentId`) in the same transaction as definitive payment decisions, and decide which rejected, uncertain, or lookup events require auditing.

### Open Operational Choices
- Target production latency and throughput SLOs, which drive concrete values for the payment budget, timeouts, rate limit, in-flight bank-call cap, circuit-breaker threshold/cooldown, and shared storage engine.
