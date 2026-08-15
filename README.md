# MediCore HMS

Hospital Management System — Advanced Software Engineering capstone.

| Path | Contents |
|---|---|
| `web/` | Next.js front end (role-aware UI; verified: build + live booking E2E) |
| `medicore-backend-server/` | **Active backend** — Java 21 / Spring Boot 3.3 / Gradle (Design v1.3). Milestones 1–3 complete: auth/policy, scheduling, clinical, pharmacy, billing + ITC payments, notifications outbox (DD-08). |

```bash
docker compose up --build   # web :3000, api :4000, postgres :5433 — see DEPLOYMENT.md
```

Documents: SRS v1.3 · Effort Estimation (UCP) v1.0 · Design v1.7 · Testing Report v1.4 · Debt Ledger v1.3 · User Manual v1.1 · Maintenance Plan v1.2.
