# MediCore HMS — API Server (Milestone 1)

Java 21 · Spring Boot 3.3 · Gradle (Kotlin DSL) · PostgreSQL 16 · Flyway

Implements the Milestone 1 vertical slice per **Design Document v1.3** and **SRS v1.2**:
authentication with sessions and lockout, the policy engine (RBAC + relationship-based
access, deny-by-default), patient registration, doctor schedules with materialised slots,
race-safe appointment booking, check-in and department queues, audit logging, and the
`PaymentGateway` port with the ITC adapter stubbed pending the API specification (OI-5).

## Requirements
- Java 21 (JDK)
- PostgreSQL 16 with a database and user (defaults: `medicore` / `medicore_dev`, db `medicore`)
- Gradle 8.x — **one-time**: this repo ships `gradle/wrapper/gradle-wrapper.properties`
  but not the binary `gradle-wrapper.jar`. Generate it once with a local Gradle install
  (`sdk install gradle 8.10.2` via SDKMAN, then `gradle wrapper`), commit the wrapper,
  and use `./gradlew` from then on. Alternatively, skip local builds entirely and use Docker.

## Run
```bash
createdb medicore   # or use an existing database

# migrations run automatically (Flyway) on startup
MEDICORE_SEED=true ./gradlew bootRun
# API on http://localhost:4000
```

Demo logins after seeding (password `Password123!`):
`admin@` `doctor@` `reception@` `pharmacist@` `billing@` `management@` `patient@medicore.test`

## Test
```bash
./gradlew test                    # unit tests: slot generator + policy engine (no DB needed)
MEDICORE_IT=true ./gradlew test   # + integration tests against the configured PostgreSQL:
                                  #   booking race (FR-APT-04), signed-consultation immutability
                                  #   (FR-EMR-03), append-only audit log (NFR-SEC-04)
```

## Docker (no local Gradle or JDK needed)
```bash
docker build -t medicore-api .
docker run -p 4000:4000 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/medicore \
  -e DB_USER=medicore -e DB_PASS=medicore_dev -e MEDICORE_SEED=true medicore-api
```

## Configuration (env vars)
| Var | Default | Purpose |
|---|---|---|
| `PORT` | 4000 | HTTP port |
| `DB_URL` | jdbc:postgresql://localhost:5432/medicore | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASS` | medicore / medicore_dev | DB credentials |
| `CORS_ORIGIN` | http://localhost:3000 | Front-end origin (credentials allowed) |
| `COOKIE_SECURE` | false | Set `true` behind HTTPS in production |
| `MEDICORE_SEED` | false | Seed demo data on startup (idempotent) |

## Architecture notes (traceability to Design v1.3)
- **Policy engine (DD-03):** `policy/PolicyEngine.decide()` is a pure function over the
  SRS §4 matrix (`PolicyMatrix`) and injected `RelationshipResolver` — the single
  authorisation path (AC-06). `PolicyService` wraps it for Spring, audits clinical
  actions allowed-or-denied (FR-EMR-06), and maps denials to 401/403.
- **Race-safe booking (DD-04):** slots are materialised rows; `appointments.slot_id UNIQUE`
  makes the database the arbiter. `SchedulingService.bookAppointment` uses `saveAndFlush`
  and converts the unique violation into a 409.
- **Sessions (DD-02):** Spring Session JDBC — httpOnly, SameSite=lax cookies stored in
  PostgreSQL; session id rotated on login.
- **Payments (DD-07):** `payments/PaymentGateway` is the port; `ItcGatewayAdapter`
  returns 501 until the ITC API specification arrives (SRS OI-5, Milestone 3).
- **Schema:** Flyway owns all DDL (V1–V4), including the integrity triggers; Hibernate
  runs with `ddl-auto: none`. Status columns are TEXT + CHECK (not PG enums) for clean
  JPA mapping — constraint-equivalent.

## Milestone 2 (this increment)
- **EMR:** consultation lifecycle — start from a checked-in appointment (queue moves to
  `in_consultation`), notes, **sign-and-lock** (service check + V4 trigger backstop),
  post-signature **addendums** (author-only; action `emr.addendum`), allergies, and the
  patient EMR read model.
- **Pharmacy:** drug catalogue, batch intake, pharmacist worklist, and **FEFO dispensing**
  (Design Fig. 7): `FefoAllocator` is a pure, unit-tested function; the service locks batch
  rows (`SELECT ... FOR UPDATE`), applies guarded decrements
  (`... AND qty_on_hand >= ?`), and the schema `CHECK (qty_on_hand >= 0)` is the final
  backstop. Low-stock report per reorder level.
- **Tests added:** `FefoAllocatorTest` (6 pure), `ClinicalPharmacyIT` (sign-lock + FEFO
  order + concurrent-dispense race, gated by `MEDICORE_IT=true`).

## Milestone 3 — gateway-independent half (this increment)
- **Invoicing (DD-05):** draft -> issued lifecycle; append-only line items; charges posted
  from the consultation fee and dispensed medication (idempotent per source); totals,
  balance and status always computed from the durable record (`BillingMath`, pure).
- **Payments:** manual cash/POS capture by billing clerks; online payments initialised
  through the `PaymentGateway` port. **Crediting is gated by `PaymentVerifier`
  (NFR-SEC-06, pure): the callback body never credits — only a successful, amount-exact
  independent `verifyStatus` round-trip does.** Replayed callbacks are idempotent.
- **Void (FR-BIL-07):** management-only, mandatory reason, blocked once money is captured.
- **Still pending for M3 completion (OI-5):** the `ItcGatewayAdapter` implementation and
  the exact callback path/shape, once the ITC API specification is shared; plus email
  notifications. `BillingIT` proves the flow end-to-end against a conformant stub gateway.

## ITC Transflow Checkout (SRS OI-5 — resolved)
The `ItcGatewayAdapter` implements the received API Definition:
`POST /request-payments` -> redirect the payer to `data.checkoutUrl`; ITC calls back to
`/api/payments/callback` (`refNo` = our stored `transactionReference`; the endpoint always
answers HTTP 200 per the spec); crediting happens only via `POST /check-transaction-status`
(success = outer 200 **and** `data.responseCode == "01"`) plus the exact-amount rule in
`PaymentVerifier`. If a callback never arrives, `POST /api/payments/{id}/verify` re-runs the
same verification (spec §3). Response-shape mapping is pure (`ItcResponseMapper`) and
unit-verified against the sample payloads in the vendor document.

Configuration (all via env):
| Var | Purpose |
|---|---|
| `ITC_BASE_URL` | defaults to UAT `https://apisuat.itcsrvc.com/checkout`; LIVE is `https://apis.itcsrvc.com/checkout` |
| `ITC_API_KEY`, `ITC_MERCHANT_PRODUCT_ID`, `ITC_TRANSFLOW_ID` | merchant credentials (required; adapter answers 503 until set) |
| `ITC_CALLBACK_URL` | public URL of `/api/payments/callback` |
| `ITC_SUCCESS_URL` / `ITC_FAILURE_URL` | front-end redirect landing pages |

UAT test data (from the vendor document): mobile money — any registered number with small
amounts (e.g. GHS 0.10); card success — PAN 5123450000000008, CVV 100, expiry 01/39;
card failure — PAN 3528000000000007, CVV 101, expiry 05/39.

## Verification status (honest record for the testing report)
Authored in a sandbox where Maven Central is unreachable, so the Spring layer could not
be compiled there. What **was** machine-verified at authoring time:
- Flyway V1–V4 executed against a real PostgreSQL 16 — 30 tables, both integrity triggers
  confirmed firing (tamper attempts rejected).
- The framework-free core (`PolicyEngine`, `PolicyMatrix`, `SlotGenerator`, context types)
  compiled with `javac` and passed a 20-check harness mirroring the unit tests.
- The Node.js reference implementation of this same design (in `../server`) passed its
  full 16-test suite, including the live booking race — it defines expected behaviour.

First `./gradlew test` on a networked machine is the verification gate for the Spring
wiring; any fixes belong in the technical-debt ledger with this note as the cause.
