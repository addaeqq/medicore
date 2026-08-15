# MediCore HMS

Hospital Management System — Advanced Software Engineering capstone.

| Path | Contents |
|---|---|
| `web/` | Next.js front end (role-aware UI; verified: build + live booking E2E) |
| `medicore-backend-server/` | **Active backend** — Java 21 / Spring Boot 3.3 / Gradle (Design v1.3). Milestones 1–3 complete: auth/policy, scheduling, clinical, pharmacy, billing + ITC payments, notifications outbox (DD-08). |
| `server/` | Node.js/Express reference implementation of the same M1 design (pre-platform-change baseline, Design change record v1.1). Its 16-test suite documents expected behaviour. **Not deployed** — it defines the same tables as the Flyway schema, so it never shares a database with the active backend; run it via `npm test` / CI only. |

```bash
docker compose up --build   # web :3000, api :4000, postgres :5433 — see DEPLOYMENT.md
```

Documents: SRS v1.3 · Effort Estimation (UCP) v1.0 · Design v1.6 · Testing Report v1.3 · Debt Ledger v1.3 · User Manual v1.1 · Maintenance Plan v1.1.
