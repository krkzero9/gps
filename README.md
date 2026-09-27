# Payment Request Service

A business creates a payment request; the payment side settles it once the money has arrived. Retrying a create never creates a second request, and a request is settled exactly once, also with several instances.

Spring Boot 3.5.16 · Java 21 · Maven · H2 (PostgreSQL mode) · Flyway · Microsoft Entra ID. Extensions: **C (concurrency proof)** and **B (security)**.

## Run

```bash
./mvnw clean test          # 23 tests, offline, no credentials needed
./mvnw spring-boot:run     # http://localhost:8080
```

Calls need a token from Microsoft Entra ID. The callers' client ids and secrets are not in this repository; I sent them separately.

```bash
curl -s https://login.microsoftonline.com/e3f0ddb8-42ef-470c-9148-3d4e776d03c3/oauth2/v2.0/token \
  -d grant_type=client_credentials -d client_id=<client id> -d client_secret=<secret> \
  -d scope=api://6bdd59a8-e2b0-463e-a39b-2e1227f83be5/.default
```

`payment-request.postman_collection.json` gets the tokens and runs every scenario, including the security checks.

## API

| Request | Permission | Result |
|---|---|---|
| `POST /api/v1/payment-requests` + `Idempotency-Key` | `PaymentRequests.Write` | 201 + `Location`. Same key + same body → 201 with `Idempotent-Replayed: true`; different body → 422 |
| `GET /api/v1/payment-requests/{id}` | `PaymentRequests.Read` | 200; 404 if unknown or another client's |
| `POST /api/v1/payment-requests/{id}/settle` `{"settlementReference": "…"}` | `PaymentRequests.Settle` | 200; same reference again → 200; different → 409 |

```json
{ "recipientName": "Acme GmbH", "recipientIban": "DE89 3704 0044 0532 0130 00",
  "amount": 49.90, "currency": "EUR", "purpose": "Invoice 2026-0815" }
```

Errors are RFC 9457 Problem Details with a stable `code`; validation lists all invalid fields at once.

## Decisions

- **Idempotent create:** `UNIQUE(owner_client_id, idempotency_key)` is the guard. A lost race catches the constraint violation and answers from the winner. Replays compare the stored normalized fields (`49.9` = `49.90`).
- **Settle once:** one `UPDATE … WHERE id = ? AND status = 'OPEN'`; the database picks the single winner. Same reference = retried event (200).
- **Several instances:** no state in memory; both rules are decided by the database. Two tests fire 10 parallel requests to prove it.
- **Money:** `BigDecimal` / `NUMERIC(19,2)`; more than 2 decimals is rejected, not rounded.
- **Security:** Entra ID tokens; issuer and audience validated; app roles checked with `@PreAuthorize`. Only the payment side can settle. Owner = calling client (`azp`); others get 404, not 403. No secrets in the repo.
- **In production I'd change:** client certificates instead of client secrets, secrets kept in a vault and rotated, and each new business onboarded as its own Entra client with only Write and Read.

## Assumptions

EUR only (decimals and limits are per currency; `currency` stays in the API). Name ≤ 70 and purpose ≤ 140 characters (SEPA limits). Max amount 1,000,000.00 (configurable). A replay returns the current state.

## Extensions I didn't pick

The brief allows two extensions. I chose C because it proves the two rules that matter most, exactly-once settle and safe retries, under real parallel requests, and B because it ties into them: only the payment side may settle, and Idempotency-Keys are scoped per client. I preferred doing two properly over four halfway.

- **A, messaging:** a consumer would call the same settle logic, so duplicate events are already handled by the settle-once rule.
- **D, operability:** health checks and metrics come largely from Spring Boot Actuator; I'd add them when deploying.
- **E, CI:** a pipeline would run the same `./mvnw test`; useful for a team, but it adds no new checks here.
- **F, list endpoint** and **G, cancellation:** useful features, but they don't touch the correctness core. Cancel would reuse the conditional-update pattern from settle.

The tests run on H2 only. Both rules use standard SQL (a unique constraint and a conditional update), but I haven't verified them on PostgreSQL; running the tests there with Testcontainers would be the natural next step.

## AI usage

I used Claude Opus 5.5 for planning, first drafts of the code and tests, and reviews. I reviewed and adjusted every change, and verified it with the test suite and Postman runs against real Entra tokens.
- It proposed storing a SHA-256 hash of the request for idempotency. Every field is stored anyway, so I removed it and compare fields.
- It first proposed issuing tokens inside the app with its own authorization server. In production an identity provider issues tokens, not the API, so I used Microsoft Entra ID: the app only validates tokens, exactly as it would in production.
- It used a `Persistable`/`isNew()` workaround to assign UUIDs; `@GeneratedValue(strategy = GenerationType.UUID)` does the same with less code, so I switched.

