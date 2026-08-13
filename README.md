# MediCore HMS — Server (Milestone 1)

Hospital Management System API. Companion docs: SRS v1.0, Effort Estimation (UCP) v1.0, System Design Document v1.0.

**Milestone 1 scope (per estimation §8.3):** full 27-table schema as versioned migrations, session auth with
lockout, the Policy Engine (RBAC + ReBAC, deny-by-default, audited clinical access), schedule/slot generation,
transactional booking with a database-enforced double-booking guard, check-in and live queue.

## Stack
Node 22 · Express · PostgreSQL 16 · Knex (migrations/queries) · express-session + connect-pg-simple (DD-02) ·
bcryptjs · zod · helmet · express-rate-limit. Tests: node:test (zero test deps).

## Setup
```bash
cd server
cp .env.example .env          # set DATABASE_URL + SESSION_SECRET
npm install
npm run migrate               # 4 migrations → 27 tables + integrity triggers
npm run seed                  # demo departments, staff, patient, schedules, drugs, ward
npm run dev                   # API on :4000
npm test                      # 16 tests incl. booking race + immutability triggers
```

Demo logins (password `Password123!`): admin@ / doctor@ / reception@ / pharmacist@ / billing@ /
management@ / patient@medicore.test — all synthetic data (NFR-PRV-03).

## Architecture in 30 seconds
Routes do HTTP only → `policy/engine.js` is the **single** authorisation path (AC-06): a data-driven matrix
(`policy/matrix.js`, mirroring SRS §4) plus relationship resolvers (`policy/relationships.js`) for
doctor-patient, nurse-ward, and family-grant scopes. Clinical actions are audit-logged allowed-or-denied
(FR-EMR-06). Services own transactions; integrity that carries requirements lives in the database:
`UNIQUE(appointments.slot_id)`, partial unique index for one-active-admission-per-bed, signed-note
immutability trigger, append-only audit trigger.

## Requirement traceability
Every migration column, service function and test names its FR/NFR/AC anchor in comments —
grep `FR-APT-04` to jump from requirement to guard to test.

## Deployment target (per Design Doc §2)
API + PostgreSQL on Render, front end on Vercel. Set `NODE_ENV=production` (secure cookies), point
`DATABASE_URL` at the managed instance, run `npm run migrate && npm run seed` once via a Render job.

## Known debt carried (ledger TD-01, TD-02)
Slot materialisation is synchronous in the schedule-save request; email/notification service not yet wired.
Both are logged in the technical-debt ledger with resolutions scheduled for v1.1.

## Next milestones
M2: consultation + prescription + FEFO dispensing. M3: invoicing engine + Paystack sandbox + notifications → v0.9 live.
