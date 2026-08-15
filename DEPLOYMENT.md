# MediCore HMS — Deployment Guide

Three ways to run it, cheapest-effort first.

## 1. Local full stack (graders, demos)
```bash
docker compose up --build        # web :3000, api :4000, postgres :5432
```
Demo data is seeded automatically (`FLYWAY_LOCATIONS` includes `db/seed`).
Sign in at http://localhost:3000 — accounts are listed on the login page,
password `Password123!`.

## 2. Development (hot reload)
```bash
# terminal 1 — database
docker compose up db
# terminal 2 — API (needs JDK 21; first run downloads Gradle deps)
cd server-java && ./gradlew bootRun
# terminal 3 — web
cd web && npm install && npm run dev
```
If the Gradle wrapper jar is missing (fresh clone), run `gradle wrapper` once
or build via Docker as in option 1.

## 3. Cloud (Render API + Vercel web)

### API on Render
1. New → Web Service → connect the GitHub repo, root directory `server-java`,
   runtime **Docker** (the Dockerfile is multi-stage; no build config needed).
2. Create a Render PostgreSQL instance (or Neon); note the connection details.
3. Environment variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://HOST:5432/DBNAME` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | from the database instance |
| `SESSION_SECRET` | long random string |
| `CORS_ORIGIN` | your Vercel URL (belt-and-braces; the proxy makes CORS moot) |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/seed` for a demo deploy; **omit for real data** |
| `ITC_API_KEY`, `ITC_MERCHANT_PRODUCT_ID`, `ITC_TRANSFLOW_ID` | from IT Consortium |
| `ITC_BASE_URL` | omit for UAT default; `https://apis.itcsrvc.com/checkout` for LIVE |
| `ITC_CALLBACK_URL` | `https://YOUR-API.onrender.com/api/payments/callback` |
| `ITC_SUCCESS_URL` / `ITC_FAILURE_URL` | `https://YOUR-WEB.vercel.app/payments/success` / `.../failure` |

4. Health check path: `/api/health`.

### Web on Vercel
1. Import the repo, set **Root Directory = `web`** (framework auto-detected).
2. One environment variable: `API_URL = https://YOUR-API.onrender.com`.
3. Deploy. The `/api/*` rewrite proxies through Vercel, so the session cookie
   stays first-party and `SameSite=lax` just works — no `COOKIE_SAMESITE`
   changes needed. (Only if you later bypass the proxy and call the API's
   origin directly from the browser would you set `COOKIE_SAMESITE=none`.)

### First-deploy checklist
- [ ] API `/api/health` returns ok
- [ ] Log in as `patient@medicore.test`, see slots on **Book appointment**
- [ ] Reception: search `Kwame`, book + check-in; Doctor: start, sign
- [ ] Pharmacist: dispense (watch FEFO pick the `*-EARLY` batch)
- [ ] Billing: post charges, issue; Patient: **Pay online** → ITC UAT checkout
      (momo: any number, GHS 0.10 · card success: 5123450000000008 / 100 / 01-39)
- [ ] After paying, /payments/success shows **paid** (server-side verified)
- [ ] If a callback is missed: invoice page stays pending → the success page's
      verify call (or `POST /api/payments/{id}/verify`) settles it

### Notes
- The free Render tier sleeps; first request after idle takes ~30s.
- Rotate `SESSION_SECRET` and disable the seed before any real-data use.
- CI (`.github/workflows/ci.yml`) runs the full Gradle test suite with a
  PostgreSQL service on every push — your deployment gate.
