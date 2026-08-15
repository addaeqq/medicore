# MediCore HMS — Deployment Guide

Two deployable services: **`web`** (Next.js) and **`medicore-backend-server`**
(Spring Boot), against one PostgreSQL. `server/` — the frozen Node M1 reference —
is **not deployed**: it implements the same M1 contract over the same table names,
so it would collide with the Flyway schema. It stays in the repo as the executable
specification and runs only through its test suite (`npm test`) and CI.

## 1. Local full stack (graders, demos)
```bash
docker compose up --build        # web :3000, api :4000, postgres :5433 (host)
```
Demo data is seeded automatically (`FLYWAY_LOCATIONS` includes `db/seed`).
Sign in at http://localhost:3000 — the seven accounts on the login page are the
quickest way in; every account below shares the password `Password123!`.

The seed builds a working day at MediCore Teaching Hospital, Accra: 10
departments, 26 staff, 20 patients, six wards, a 23-line formulary, four weeks
of clinic history behind today and four weeks of bookable slots ahead.

| Sign in as | Account | Sees |
|---|---|---|
| Patient — Kwame Owusu | `patient@medicore.test` | Two past visits, a booked appointment, a paid bill, a void duplicate and **GHS 125 outstanding** to pay online |
| Doctor — Dr. Abena Mensah | `doctor@medicore.test` | General Medicine queue: three patients waiting; full records for those in her care |
| Doctor — Dr. Nii Armah Quaye | `n.quaye@medicore.test` | A&E: one patient mid-consultation (triaged ahead of the queue), one waiting |
| Receptionist — Mercy Dartey | `reception@medicore.test` | Patient lookup, booking, check-in |
| Nurse — Sister Comfort Adjei | `c.adjei@medicore.test` | Female Medical Ward board: one occupied bed, the observation chart, and charting of new observations |
| Lab technician — Mr. Daniel Ofori | `d.ofori@medicore.test` | Bench worklist across all four stages, with result entry |
| Pharmacist — Pharm. Kojo Asante | `pharmacist@medicore.test` | Five prescriptions on the worklist (two written this morning, two part-dispensed) and three lines below reorder level |
| Billing clerk — Gifty Owusu-Ansah | `billing@medicore.test` | 19 invoices across draft / issued / partially paid / paid / void |
| Management — Bright Agyeman | `management@medicore.test` | Invoice review and void-with-reason |
| Sys admin — Nana Kwaku Antwi | `admin@medicore.test` | Publishes weekly clinics |

Other staff use the same password: doctors `k.boateng@`, `e.sarpong@`, `a.owusu@`,
`a.frimpong@`, `y.antwi@`, `s.agbeko@`; nurses `c.adjei@`, `g.amponsah@`,
`a.nyarko@`, `v.asante@`, `i.mohammed@`, `m.tetteh@`; lab `d.ofori@`,
`h.boakye@`; pharmacy `n.lamptey@` — all `@medicore.test`. Patient portal
accounts are `a.boakye@`, `k.mensah@`, `e.baidoo@`, `c.mensah@`
`@patients.medicore.test`.

**Family accounts sign in but have no screens yet** — the role carries
consent-scoped policy permissions (FR-FAM-01) and a seeded grant, but its UI is
deferred to M4 (TD-12), so it lands on an empty overview.

`web` waits on the API's `/api/health` healthcheck, so the first page load never
races Flyway's migration. Copy `.env.example` to `.env` to override any default
(ports, credentials, ITC keys, SMTP); compose falls back to demo values without it.

| URL | What it is |
|---|---|
| http://localhost:3000 | The application |
| http://localhost:4000/api/health | API liveness — `{"ok":true,"service":"medicore-api"}` |
| `postgres://medicore:medicore_dev@localhost:5433/medicore` | Database (host port 5433 avoids a local PostgreSQL) |

Useful: `docker compose logs -f api` · `docker compose down -v` (wipes the DB and
re-seeds on next start — do this if integration-test runs have left synthetic
`Race Doc` / `M2 Doc` rows in the demo directory).

## 2. Development (hot reload)
```bash
# terminal 1 — database only
docker compose up db
# terminal 2 — API (needs JDK 21; there is no wrapper jar in the tree, use system Gradle 8.x)
cd medicore-backend-server && MEDICORE_SEED=true gradle bootRun
# terminal 3 — web
cd web && npm install && npm run dev
```
The API defaults to `jdbc:postgresql://localhost:5432/medicore`; point `DB_URL` at
`localhost:5433` to use the compose database.

## 3. Cloud — Railway

One project, three services: **Postgres**, **api**, **web**. Railway builds from a
GitHub repo, so push this tree first (the working copy ships as `medicore.bundle`;
`git clone medicore.bundle medicore && cd medicore && git remote add origin …`).

### 3.1 Database
New → Database → **Add PostgreSQL**. Nothing to configure; it exposes `PGHOST`,
`PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` as reference variables.

### 3.2 API service
New → GitHub Repo → this repo, then in **Settings**:

| Setting | Value |
|---|---|
| Root Directory | `medicore-complete/medicore-backend-server` (drop the prefix if the repo root *is* `medicore-complete`) |
| Builder | Dockerfile (auto-detected) |
| Healthcheck Path | `/api/health` |
| Networking → Generate Domain, target port | `4000` |

**Variables** — the `${{Postgres.*}}` forms are Railway reference variables; paste
them literally and Railway resolves them:

| Variable | Value |
|---|---|
| `PORT` | `4000` (matches the domain's target port; `application.yml` reads `${PORT:4000}`) |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASS` | `${{Postgres.PGPASSWORD}}` |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/seed` for a demo deploy; **omit for real data** |
| `COOKIE_SECURE` | `true` (Railway serves HTTPS — NFR-SEC-01) |
| `COOKIE_SAMESITE` | `lax` (see 3.4) |
| `CORS_ORIGIN` | the web service's public URL |
| `ITC_API_KEY`, `ITC_MERCHANT_PRODUCT_ID`, `ITC_TRANSFLOW_ID` | from IT Consortium — see the field mapping below |
| `ITC_BASE_URL` | omit for the UAT default; `https://apis.itcsrvc.com/checkout` for LIVE |
| `ITC_CALLBACK_URL` | `https://YOUR-API.up.railway.app/api/payments/callback` |
| `ITC_SUCCESS_URL` / `ITC_FAILURE_URL` | `https://YOUR-WEB.up.railway.app/payments/success` / `.../failure` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `MAIL_FROM` | optional (DD-08); unset marks outbox rows `skipped` |

### 3.3 Web service
New → GitHub Repo → same repo, second service:

| Setting | Value |
|---|---|
| Root Directory | `medicore-complete/web` |
| Builder | Dockerfile |
| Networking → Generate Domain, target port | `3000` |

| Variable | Value |
|---|---|
| `PORT` | `3000` |
| `API_URL` | `https://YOUR-API.up.railway.app` |

> **`API_URL` is a build-time value.** Next resolves the `/api/*` rewrite into
> `.next/routes-manifest.json` during `next build`, so setting it only at run time
> leaves the proxy pointing at the compiled-in `http://localhost:4000` and every
> `/api/*` call 500s with `ECONNREFUSED`. `web/Dockerfile` takes it as
> `ARG API_URL`, and Railway forwards service variables to the Docker build, so
> setting it as a normal variable works — but **changing it requires a redeploy
> that rebuilds**, not a restart. Locally the same rule applies:
> `docker compose up --build web`.

Deploy order: Postgres → api → web. Then set `CORS_ORIGIN` and the three `ITC_*`
URLs to the now-known domains and redeploy the API.

Smoke test in this order — each step isolates one hop:
```bash
curl https://YOUR-API.up.railway.app/api/health    # API + Flyway
curl https://YOUR-WEB.up.railway.app/api/health    # the proxy hop (500 here = API_URL missing at build)
```
then log in through the UI.

### 3.4 Why the API's *public* URL, not Railway private networking
`API_URL` is consumed by `next.config.mjs`'s rewrite, which runs **server-side** in
the web container. Railway's private network is IPv6-only and Spring Boot binds
IPv4 by default, so `http://api.railway.internal:4000` would need a
`server.address: ::` change; the public URL avoids it and you want the API
reachable for testing anyway. Either way the browser only ever talks to the web
origin, so the session cookie stays first-party and `SameSite=lax` + `Secure=true`
is correct (DD-02). Only if you bypassed the proxy and called the API origin from
the browser would you need `COOKIE_SAMESITE=none`.

### First-deploy checklist
- [ ] API `/api/health` returns ok
- [ ] Log in as `patient@medicore.test`, see slots on **Book appointment**
- [ ] Reception: search `Kwame`, book + check-in; Doctor: start, sign
- [ ] Pharmacist: open Adwoa Asantewaa's prescription and dispense — FEFO draws
      the nearest-expiry batch (`AMX-2409-A` before `AMX-2602-B`, `PCM-2408-A`
      before `PCM-2511-B`)
- [ ] Nurse (`c.adjei@`): open the occupied bed on the ward board, file observations,
      and confirm a patient on another ward is refused (AC-03)
- [ ] Doctor: order tests from a consultation → Lab (`d.ofori@`) collects, processes
      and enters results → doctor releases → the patient sees values only then (AC-04)
- [ ] Billing: post charges, issue; Patient: **Pay online** → ITC UAT checkout
      (momo: any number, GHS 0.10 · card success: 5123450000000008 / 100 / 01-39)
- [ ] After paying, /payments/success shows **paid** (server-side verified)
- [ ] If a callback is missed: invoice page stays pending → the success page's
      verify call (or `POST /api/payments/{id}/verify`) settles it

### ITC credentials — field mapping
The adapter authenticates body-level with all three (DD-07). IT Consortium issues
them either under the API's own names or under app-config names:

| Vendor field (either form) | Environment variable |
|---|---|
| `apiKey` · `appTransFlowApiKey` | `ITC_API_KEY` |
| `merchantProductId` · `appProductId` | `ITC_MERCHANT_PRODUCT_ID` |
| `transflowId` · `appTransFlowId` | `ITC_TRANSFLOW_ID` |

A working set returns a `checkoutuat.itcsrvc.com/<merchantProductId>?req=…` link
from `POST /api/payments/init`; credentials that are well-formed but not entitled
to the service fail as described below.

Keep them out of the repository: put them in a gitignored `.env` locally and in
the platform's variable store in the cloud (NFR-SEC-05).

If a checkout fails, read the API log rather than the browser: the client-facing
message is deliberately generic (NFR-SEC-02), while the server logs the vendor's
own status and body, e.g.

```
ERROR ItcGatewayAdapter : ITC request-payments rejected the call: HTTP 400 Bad Request
  body={"responseCode":400,"data":"Vendor not authorized to access this service"}
```
`Vendor not authorized` means the credentials are well-formed but the merchant is
not entitled to the Transflow Checkout service on that host — a merchant-account
question for IT Consortium, not a code fault.

### Notes
- ITC callbacks need a publicly reachable `ITC_CALLBACK_URL`, which a local
  compose run does not have — settle those invoices with the verify endpoint.
- Disable the seed (`FLYWAY_LOCATIONS=classpath:db/migration`) before any
  real-data use; the demo accounts all share one published password.
- CI (`.github/workflows/ci.yml`) runs the Gradle suite with `MEDICORE_IT=true`
  against a PostgreSQL service, `next build`, and the Node reference tests on
  every push — your deployment gate.

### Alternative: the web tier on Vercel
The front end deploys to Vercel unchanged — it is a stock Next.js App Router app
with no server-side filesystem use. Vercel builds it from source and ignores
`web/Dockerfile`; `output: "standalone"` is skipped there automatically
(`next.config.mjs` checks `VERCEL`), since that setting only exists for the
container image.

1. Import the repo, **Root Directory = `web`** (framework auto-detected).
2. Add one environment variable, `API_URL = https://YOUR-API…`, to **every**
   environment you build (Production, Preview). It must exist at *build* time —
   the `/api/*` rewrite destination is baked into the routes manifest by
   `next build`, so adding it later needs a **redeploy**, not just a restart.
3. Deploy, then point the API at the new origin: `CORS_ORIGIN` and
   `ITC_SUCCESS_URL` / `ITC_FAILURE_URL` to `https://YOUR-APP.vercel.app`,
   `COOKIE_SECURE=true`. `ITC_CALLBACK_URL` stays on the **API** host.

The rewrite proxies `/api/*` through Vercel to the backend, so the browser still
only ever talks to one origin and the session cookie stays first-party with
`SameSite=lax` (DD-02) — the same reason the cookie works under compose.

The backend cannot go on Vercel: it is a long-lived Spring Boot server with a
JDBC connection pool, a Spring Session store and a scheduled outbox worker
(DD-08), none of which fit a serverless function. Host it on Railway (§3) or
Render — Render: Web Service, root `medicore-backend-server`, runtime Docker,
health check `/api/health`, plus a Render/Neon PostgreSQL. The free Render tier
sleeps, so the first request after idle takes ~30s.
