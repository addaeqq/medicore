# MediCore HMS — Web (Next.js)

Role-aware front end for the MediCore API: booking, check-in, queue, consultations,
EMR, pharmacy dispensing, billing and ITC Transflow online payment.

## Run locally
```bash
npm install
npm run dev            # http://localhost:3000
```
The app expects the API on `http://localhost:4000` (override with `API_URL`).
Demo sign-ins are on the login page (password `Password123!`).

## How it talks to the API
`next.config.mjs` rewrites `/api/*` to the backend, so the browser only ever sees
one origin. The backend's httpOnly session cookie is therefore always first-party
and `SameSite=lax` keeps working in production — no CORS, no third-party-cookie pain.
If you deploy web and API on separate origins without the proxy instead, set
`COOKIE_SAMESITE=none` (and secure cookies) on the API.

## Design notes
"Hospital paperwork, made digital": chart-paper ground `#F6F7F5`, ledger ink,
theatre-green `#0E6E5C` primary, ward-amber pending states, triage-red errors.
Serif display for headings, monospace for chart numbers (MRNs, amounts, payment
references). Signature element: the **patient band** — a wristband-style identity
strip shown wherever a patient is in context, so staff never act on the wrong record.
System font stacks only (no font downloads at build time).

## Verification record (what was actually tested here)
- `npm run build` passes: all 15 routes compile and type-check.
- Live smoke test through the proxy against the reference API + PostgreSQL:
  login → session cookie → profile → 105 slots listed.
- Playwright end-to-end in Chromium: patient signs in, books a slot from the UI,
  appointment appears as BOOKED on /appointments (screenshots in the project log).
- Payment checkout redirect and gateway callback pages are wired but were not
  exercised against ITC UAT from this environment (needs merchant credentials).
