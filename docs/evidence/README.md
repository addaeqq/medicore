# Browser end-to-end evidence

Screen captures backing Testing Report §4.4 (*Web application — build, smoke, and
browser end-to-end*). Taken against the full stack running under
`docker compose` — Next.js → Spring Boot → PostgreSQL, with the current demo seed
(`db/seed/V900__demo_seed.sql`). Nothing here is mocked or staged: each screen is
the application answering a real signed-in session.

| File | Shows |
|---|---|
| `ui-patient-dashboard.png` | Patient overview for Kwame Owusu, `MRN-2021-0043` — the wristband identity band and the four patient entry points. Role-aware navigation: a patient sees Overview / Book appointment / My appointments and nothing else. |
| `ui-booking.png` | Open clinic slots across five departments — General Medicine, Pediatrics, A&E, Obstetrics & Gynaecology — each carrying its doctor and the consultation fee (GHS 60–120) that later becomes the invoice line. Filters for department and doctor. |
| `ui-booked-confirmation.png` | The same patient's appointment list: an upcoming `BOOKED` visit with a Cancel action, and a past `COMPLETED` one. The cancellation cutoff (FR-APT-05) is enforced server-side. |
| `ui-rx-sent.png` | Dr. Nii Armah Quaye working an open A&E consultation for Musah Alhassan — complaint, findings and diagnosis with **Sign & lock**, and the prescription panel confirming *"Prescription sent to pharmacy (1 item)"*. |
| `ui-pharmacy-worklist.png` | Pharm. Kojo Asante's dispensing worklist, with **that same prescription** for Musah Alhassan (`MRN-2026-0021`) at the bottom, minutes old. Alongside it: the 23-line formulary with `open` and `partially dispensed` states, and the low-stock `reorder` flag firing in red on Azithromycin (32) and Insulin (6). |

## What the pair proves

`ui-rx-sent.png` and `ui-pharmacy-worklist.png` are the same prescription seen
from two sides. A doctor writes it against a consultation he is authorised to
edit (RELATIONSHIP scope), and it appears on a pharmacist's worklist under a
different session and a different role — one live system, one database, the
policy engine deciding both accesses. The partially-dispensed rows above it show
FEFO allocation having already drawn from the earliest-expiry batches.

## Reproducing

With the stack up (`docker compose up --build`) these are reproducible from the
seed: sign in as `n.quaye@medicore.test`, open the A&E consultation for Musah
Alhassan, prescribe, then sign in as `pharmacist@medicore.test` and look at the
worklist. All demo accounts use `Password123!`.
