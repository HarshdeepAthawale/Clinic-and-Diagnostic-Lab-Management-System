# API

The Next.js frontend talks to the Spring Boot backend **only** through this REST API (see [[TechSpecifications]], ADR-008 in [[Decisions]]). This is the contract both sides build against — every screen in [[Design]] must be served by endpoints listed here. Still a draft: refine paths and payloads as each phase is implemented, and keep springdoc/Swagger output and this doc in sync.

## Conventions

- All paths below are relative to `/api` (e.g. `POST /api/auth/login`). The frontend calls them on its own origin; Next.js rewrites forward them to Spring Boot.
- JSON request/response bodies; field names in `camelCase`; timestamps in ISO-8601 UTC.
- Auth via the JWT `httpOnly` cookie (ADR-009). All endpoints require it except `/auth/login`, `/auth/register`, `/auth/register/claim` and the `/public/*` report check.
- Every `POST`/`PUT`/`PATCH`/`DELETE` must send `X-CSRF-Protection: 1` (ADR-014), including login and register; missing header → `403` with code `CSRF_HEADER_MISSING`. `GET` requests never change data.
- List endpoints are paginated: `?page=0&size=20`, response `{ "content": [...], "page": n, "size": n, "totalElements": n }`.
- PDF endpoints return `application/pdf` and run the same authorization check as the record they belong to (see [[Security]] §4).
- Role checks are enforced server-side per [[Security]] — an endpoint below being "reachable" does not mean every caller can use it.
- Error responses: standard shape `{ "error": string, "code": string }` (exact code list TBD). A malformed query or path parameter (e.g. `?date=tomorrow`) is `400 INVALID_PARAMETER`.

## Auth

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | Patient self-registration (staff accounts created by Admin, not self-registered) |
| POST | `/auth/register/claim` | `{ email, password, registrationCode }` — creates a Patient login linked to a record the front desk registered (ADR-018). Unknown/expired/used code → `400 INVALID_REGISTRATION_CODE`; email in use → `409 EMAIL_TAKEN` |
| POST | `/auth/login` | Sets JWT cookie; returns `{ id, name, role }`. After 5 failures for an email from one address (or 30 from any address) in 15 minutes → `429 TOO_MANY_ATTEMPTS` with the wait; a correct sign-in clears the email's count (ADR-030) |
| POST | `/auth/logout` | Clears the cookie |
| GET | `/auth/me` | Current user + role — used by the frontend to pick the role area |

## Patients

| Method | Path | Role |
|---|---|---|
| GET | `/patients?q=` | Doctor, Receptionist — search by name/phone/patient ID (`PID-000123`); returns **summary only** `{ id, patientCode, fullName, age, gender, maskedPhone }`, plus `hasCareRelationship` for doctors. Paginated |
| POST | `/patients` | Receptionist — register; returns `{ patient, registrationCode, registrationCodeExpiresAt }`. The plain code is only ever returned here (ADR-018) |
| GET | `/patients/me` | Patient — own full record (EMR) |
| GET | `/patients/{id}` | Patient (self), Receptionist, Admin — demographics/contact, `hasLogin`, `registrationCodeExpiresAt`; no clinical fields |
| PATCH | `/patients/{id}` | Receptionist — update demographics/contact (name, dob, gender, phone, address, emergency contact) |
| POST | `/patients/{id}/registration-code` | Receptionist — issue a fresh registration code (replaces the old one). Record already has a login → `409 ALREADY_LINKED` |
| GET | `/patients/{id}/history` | Patient (self), Doctor with care relationship — full EMR (blood group, allergies, medical history; consultations, prescriptions and reports as those phases land). Doctor without one → `403` code `NO_CARE_RELATIONSHIP`. Logged in `PatientAccessLog` |
| PATCH | `/patients/{id}/clinical` | Doctor with care relationship — `{ knownAllergies, medicalHistory, bloodGroup }` |

## Appointments

| Method | Path | Role |
|---|---|---|
| GET | `/doctors` | Any signed-in role except lab/pathology — `[{ id, fullName, specialization, hasWorkingHours }]` |
| GET | `/doctors/me` | Doctor — own profile |
| GET | `/doctors/{id}/slots?date=YYYY-MM-DD` | Patient, Receptionist — `{ doctorId, date, working, slotMinutes, slots: [{ startsAt, available }] }` |
| GET | `/doctors/{id}/working-hours` | Patient, Doctor, Receptionist, Admin — `[{ dayOfWeek (1 = Mon), startTime, endTime, slotMinutes }]` |
| PUT | `/doctors/{id}/working-hours` | The doctor themself, Admin — `{ blocks: [...] }` replaces the week. Overlap → `400 OVERLAPPING_HOURS` |
| POST | `/appointments` | Patient (for self), Receptionist (`patientId` required) — `{ doctorId, scheduledAt, patientId?, reason? }`. Errors: `SLOT_IN_PAST`, `TOO_FAR_AHEAD`, `NOT_A_SLOT` (400); `SLOT_TAKEN`, `ALREADY_BOOKED` (409) |
| GET | `/appointments?from=&to=&doctorId=` | Doctor (always own), Receptionist, Admin — dates inclusive, default today, max 42 days. Doctors don't see cancelled ones |
| GET | `/appointments/mine` | Patient — `{ upcoming: [...], past: [...] }` |
| GET | `/appointments/{id}` | Patient (own), Doctor (own), Receptionist, Admin — `{ appointment, history: [{ fromStatus, toStatus, changedBy, note, at }] }`. Someone else's → `404` |
| PATCH | `/appointments/{id}/status` | Per the table in [[Rules]] §1a — `{ status, note? }`. Wrong role → `403`; impossible move → `409 INVALID_TRANSITION`; also `NOT_TODAY`, `TOO_EARLY`, `TOO_LATE`, `ALREADY_IN_CONSULTATION` (409) |
| POST | `/queue/tokens` | Receptionist — `{ patientId, doctorId, reason? }`; creates a checked-in walk-in with the next token |
| GET | `/queue` | Doctor (own column), Receptionist, Admin — `{ date, generatedAt, doctors: [{ doctor, nowServing, waiting: [...], seen, noShows }] }` |

An appointment is `{ id, kind (SCHEDULED/WALK_IN), status, scheduledAt, durationMinutes, reason, queueToken, patient: { id, patientCode, fullName, age, gender }, doctor: { id, fullName, specialization }, checkedInAt, startedAt, completedAt, cancelledAt, cancellationReason, createdAt }` (null fields omitted).

## Consultations & Prescriptions

| Method | Path | Role |
|---|---|---|
| POST | `/consultations` | Doctor — body `{ appointmentId }`; starts (calling a checked-in patient in) or reopens the consultation for their own appointment |
| GET | `/consultations?today=&page=&size=` | Doctor — own consultations, open drafts first |
| GET | `/consultations/{id}` | Doctor (author; others only completed + care relationship, logged), Patient (own completed, without `notes`) |
| PUT | `/consultations/{id}` | Doctor (author, draft only) — autosave; replaces complaint, notes, diagnosis, advice, follow-up, vitals and medicines. Locked → `409 CONSULTATION_LOCKED` |
| POST | `/consultations/{id}/complete` | Doctor (author) — same body; needs a diagnosis (`400 DIAGNOSIS_REQUIRED`); issues the prescription and completes the appointment |
| GET | `/patients/{id}/consultations` | Patient (self), Doctor with care relationship (logged) — completed visits |
| GET | `/prescriptions/mine` | Patient — issued prescriptions |
| GET | `/prescriptions/{id}` | Patient (own), Doctor (author or care relationship, logged) — the consultation behind it |
| GET | `/prescriptions/{id}/pdf?download=` | Same as above — `application/pdf`, generated on request, `Cache-Control: private, no-store` |
| GET | `/formulary?q=` | Doctor — medicine suggestions |

## Lab Tests & Orders

| Method | Path | Role |
|---|---|---|
| GET | `/lab-tests?q=&includeInactive=` | All authenticated — active tests with price, tube, turnaround and prep; `includeInactive` only for Admin |
| GET | `/lab-tests/{id}` | All authenticated — one test with its parameters and ranges (retired tests: Admin only) |
| POST | `/lab-tests` | Admin — add a test; duplicate code → `409 CODE_TAKEN` |
| PUT | `/lab-tests/{id}` | Admin — edit price, tube, prep, parameters; `active: false` retires it |
| POST | `/lab-orders` | Doctor — body `{ consultationId }` (own draft consultation; adds to its open order) or `{ patientId }` (care relationship), plus `testIds`, `priority`, `clinicalNotes`. `201` with the order. Errors: `409 CONSULTATION_LOCKED`, `409 ALREADY_ORDERED`, `400 UNKNOWN_TEST`, `403 NO_CARE_RELATIONSHIP` |
| GET | `/lab-orders` | Lab Technician, Pathologist — open orders, urgent first then oldest (`page`, `size`) |
| GET | `/lab-orders/mine` | Patient — own orders with each test's prep (no `clinicalNotes`) |
| GET | `/lab-orders/{id}` | Patient (own, no `clinicalNotes`), Doctor (ordered it or care relationship, logged), Lab Technician, Pathologist |
| DELETE | `/lab-orders/{id}/items/{itemId}` | Doctor (who ordered) — remove a test; the last one cancels the order |
| POST | `/lab-orders/{id}/cancel` | Doctor (who ordered) — body `{ reason }` optional; `409 ORDER_CANCELLED` if already cancelled |
| GET | `/consultations/{id}/lab-order` | Doctor (author) — the consultation's open order, or `204` if none |
| GET | `/patients/{id}/lab-orders` | Patient (self), Doctor with care relationship (logged) — order history rows |

## Samples (core differentiator — see [[Appflow]] §3, [[Rules]] §2)

Samples are created by the system when tests are ordered (ADR-024) — one per tube — never through the API.

| Method | Path | Role |
|---|---|---|
| GET | `/samples?status=&page=&size=` | Lab Technician, Pathologist — samples at one step: `ORDERED` (to collect) or `COLLECTED` (to receive); urgent orders first, then redraws, then oldest |
| GET | `/samples/by-code/{code}` | Lab Technician, Pathologist — look a sample up by its label code (any case) |
| GET | `/samples/mine` | Patient — own samples with their journey |
| GET | `/samples/{id}/status` | Patient (own), Doctor (ordered it or care relationship, logged), Pathologist, Receptionist, Lab Technician, Admin — patients and doctors get the journey without staff names or detail; lab staff also get the rejection |
| GET | `/samples/{id}/events` | Lab Technician, Pathologist, Admin (full chain-of-custody log with names) |
| GET | `/lab-orders/{id}/samples` | Same audiences as the order — the order's samples with their journeys |
| POST | `/samples/{id}/collect` | Lab Technician — body `{ tubeTypeUsed, bodySite?, confirmMismatch? }`. `400 BODY_SITE_REQUIRED` (blood), `409 TUBE_MISMATCH` unless `confirmMismatch`, `409 NOT_WAITING_FOR_COLLECTION` |
| POST | `/samples/{id}/receive` | Lab Technician — body `{ accepted, reason?, note? }`; accepting gives `RECEIVED_AT_LAB`, rejecting gives `REJECTED` (permanent record, front-desk notification, redraw sample created). `400 REASON_REQUIRED` / `REASON_NOT_ALLOWED` / `NOTE_REQUIRED`, `409 NOT_WAITING_FOR_RECEIPT` |
| GET | `/samples/to-test?page=&size=` | Lab Technician — samples at the lab waiting to be tested; returned-for-retest first (with the reason), then urgent, then the longest waiting |
| POST | `/samples/{id}/start-testing` | Lab Technician — `RECEIVED_AT_LAB → IN_TESTING`; returns the entry sheet. `409 NOT_READY_FOR_TESTING` |
| POST | `/samples/{id}/results` | Lab Technician — body `{ analyzer?, values: [{ parameterId, value }] }`: a value for **every** parameter of every test on the sample. Numbers are flagged by the server. `400 MISSING_VALUES` / `INVALID_VALUE` / `UNKNOWN_PARAMETER` / `DUPLICATE_VALUE`, `409 NOT_IN_TESTING` |
| POST | `/samples/{id}/reject` | Lab Technician — only while `IN_TESTING`; body `{ reason: SAMPLE_EXHAUSTED \| SAMPLE_DEGRADED \| OTHER, note }`. Same effects as a receipt rejection (permanent record, front-desk call-back, redraw); results entered so far are kept |
| POST | `/samples/{id}/verify` | Pathologist — signs off the result and creates the report. `409 CANNOT_VERIFY_OWN`, `409 NOT_AWAITING_VERIFICATION` |
| POST | `/samples/{id}/return-for-retest` | Pathologist — body `{ reason, note }` (`note` required for `OTHER`); moves the sample back to `IN_TESTING`, keeping the attempt |
| GET | `/samples/{id}/results` | Lab Technician, Pathologist — the entry sheet, all attempts (returned ones too), retest count, last return reason; for a pathologist also the patient's earlier values per parameter (`trend`) and the read is logged |
| GET | `/samples/pending-verification?page=&size=` | Pathologist — critical first, then out-of-range, then the longest waiting |
| GET | `/samples/verified-by-me?page=&size=` | Pathologist — what they have signed off, newest first |
| GET | `/notifications` | Receptionist — open items (sample-rejected inbox), newest first, with the open count |
| POST | `/notifications/{id}/handle` | Receptionist — mark an item handled; returns the updated list |

Removing or cancelling a lab test whose sample has been collected answers `409 SAMPLE_COLLECTED` (see Lab Tests & Orders).

## Reports

A report is created when a pathologist verifies a result (ADR-025) and is keyed by its sample. Patients can open it only after it has been dispatched.

| Method | Path | Role |
|---|---|---|
| GET | `/reports/mine` | Patient — reports dispatched to them, newest first |
| GET | `/reports/ordered-by-me?page=&size=` | Doctor — verified reports for tests they ordered |
| GET | `/reports/to-dispatch?page=&size=` | Lab Technician — verified reports not yet sent, critical first |
| GET | `/reports/{sample_id}` | Patient (own, once dispatched — the first open records receipt), Doctor (ordered it or care relationship, logged), Pathologist (logged), Lab Technician — values with unit, ranges and flags, the verifier's name, qualification and registration number |
| GET | `/reports/{sample_id}/pdf?download=` | Same audiences — `application/pdf`, drawn on request, `Cache-Control: private, no-store` |
| POST | `/reports/{sample_id}/dispatch` | Lab Technician — body `{ channel }`: `EMAIL` (a notice pointing to the patient's account; `400 NO_EMAIL` if they have none) or `DOWNLOAD_LINK`. `SMS` → `400 CHANNEL_UNAVAILABLE`. Once only: `409 ALREADY_DISPATCHED` |

Critical results and trends (ADR-028, ADR-029):

| Method | Path | Role |
|---|---|---|
| GET | `/reports/critical` | Doctor — their own unacknowledged critical results, longest waiting first: `{ open, items: [{ sampleId, sampleCode, patientId, patientCode, patientName, orderingDoctor, testNames, parameters: [{ name, flag }], verifiedAt }] }`. The numbers are on the report |
| GET | `/reports/critical/all` | Lab Technician — every unacknowledged critical result |
| GET | `/reports/critical/summary` | Admin — `{ open, oldestVerifiedAt }` only |
| POST | `/reports/{sample_id}/acknowledge-critical` | Doctor who ordered it — body `{ note? }` (max 300). Once only: `409 ALREADY_ACKNOWLEDGED`; not critical → `409 NOT_CRITICAL`; someone else's order → `404`. Logged as `LAB_REPORT` |
| GET | `/patients/{id}/trends` | Patient (self; values from reports already sent to them), Doctor with a care relationship (`403 NO_CARE_RELATIONSHIP` otherwise; logged as `LAB_HISTORY`), Pathologist (logged) — `{ patientId, series: [{ parameterId, name, unit, testCode, testName, refLow, refHigh, criticalLow, criticalHigh, points: [{ sampleId, sampleCode, verifiedAt, value, flag }] }] }`: numeric parameters measured at least twice, latest 12 values, oldest first |

The report itself (`GET /reports/{sample_id}`) also carries `verificationCode`, `critical`, and — once acknowledged — `criticalAcknowledgedAt` and `criticalAcknowledgedBy`.

## Public

No sign-in. Nothing here returns results.

| Method | Path | Notes |
|---|---|---|
| GET | `/public/reports/{code}` | The report's QR check (ADR-027): `{ authentic, clinicName, reportNumber, patientInitials, tests, verifiedAt, verifier: { name, qualification, registrationNumber } }`. A wrong or unknown code → `404`. `Cache-Control: no-store` |

## Billing

Invoices are created by the system (ADR-023) — when a consultation is finished, or a lab order is placed outside a visit — never through the API.

| Method | Path | Role |
|---|---|---|
| GET | `/invoices?status=&q=&page=&size=` | Receptionist, Admin — the counter list. `status` is `OUTSTANDING` (default, oldest first), `PAID` or `ALL`; `q` matches patient name, patient ID or invoice number |
| GET | `/invoices/mine` | Patient — own invoices |
| GET | `/invoices/{id}` | Patient (own), Receptionist, Admin — lines, discount (who, why, when), totals, balance and payments |
| GET | `/invoices/{id}/pdf?download=` | Same as above — `application/pdf`, generated on request, `Cache-Control: private, no-store` |
| POST | `/invoices/{id}/payments` | Receptionist — body `{ amount, method, reference? }`; part payments allowed. `400 AMOUNT_TOO_HIGH`, `409 INVOICE_CLOSED` |
| POST | `/invoices/{id}/discount` | Receptionist (up to the front-desk cap), Admin — body `{ amount, reason }`; `0` removes it. `400 REASON_REQUIRED`, `400 DISCOUNT_TOO_HIGH`, `403 DISCOUNT_OVER_LIMIT` |
| GET | `/patients/{id}/invoices` | Receptionist, Admin — a patient's invoices |

Removing or cancelling a lab test that was already paid for answers `409 ALREADY_PAID` (see Lab Tests & Orders).

## Inventory

Consumables and their levels (ADR-026). An item is `{ id, name, category, unit, currentStock, lowStockThreshold, lowStock, outOfStock, active, updatedAt }`.

| Method | Path | Role |
|---|---|---|
| GET | `/inventory?q=&category=&lowOnly=&includeInactive=` | Lab Technician, Admin — out-of-stock first, then low, then by name; retired items only with `includeInactive` |
| GET | `/inventory/alerts` | Lab Technician, Admin — `{ lowCount, outCount, items }`, the emptiest few |
| GET | `/inventory/{id}/movements` | Lab Technician, Admin — the last 30 changes, newest first, with who made each |
| POST | `/inventory` | Admin — `{ name, category, unit, openingStock?, lowStockThreshold }`. `201`. `409 NAME_TAKEN` |
| PUT | `/inventory/{id}` | Admin — `{ name, category, unit, lowStockThreshold, active? }`; the level is not editable here |
| PATCH | `/inventory/{id}/stock` | Lab Technician, Admin — `{ delta, reason (RESTOCK/USED/WASTAGE/CORRECTION), note? }`. Wrong direction for the reason → `400`; would go below zero → `409 INSUFFICIENT_STOCK`; retired item → `409 ITEM_RETIRED` |

## Admin / Analytics

| Method | Path | Role |
|---|---|---|
| GET | `/admin/dashboard?days=` | Admin — the last `days` clinic days (default 30, 1–180, else `400`): `{ days, from, to, kpis, series, topTests, staff, tat, heatmap }`. `series` has one point per day with zeros filled in (`date, patients, registrations, revenue, reports`); `kpis` carry the previous period's figures for comparison, and the median turnaround is omitted until a report exists; `staff` counts what each person recorded |
| GET | `/admin/analytics/tat?testId=&days=` | Admin — turnaround per test (`samples, avg/median/p90 minutes, toLab/testing/verification minutes, retested`) and the daily median per test for the heatmap; a `testId` that doesn't exist → `404` |
| POST | `/admin/staff` | Admin (all staff roles, incl. Pathologist with registration details) — not built yet |
| GET | `/admin/access-log?patientId=&userId=&from=&to=` | Admin — record access log, paginated |

## Dashboards

| Method | Path | Role |
|---|---|---|
| GET | `/dashboard/{role}` | The matching role only (`patient`, `doctor`, `pathologist`, `receptionist`, `lab-technician`, `admin`) |

Response (ADR-019):

```json
{ "role": "RECEPTIONIST",
  "widgets": [
    { "type": "stats", "title": null, "span": "full",
      "data": [{ "key": "registeredToday", "label": "Registered today", "value": 12, "hint": "..." }] },
    { "type": "upcoming", "title": "Coming next", "span": "full",
      "data": [{ "module": "Billing", "phase": 6, "description": "..." }] } ] }
```

`span` is `full`, `wide` (2/3) or `narrow` (1/3). The frontend renders each `type` from its widget registry and skips unknown types. Widgets only ever carry real data; an unbuilt module is an `upcoming` widget. Phase 03 widget types: `liveQueue` (the `/queue` board), `visitsByStatus` (today's counts per status, admin), `myQueue` (patient's token, token now being seen, how many ahead — only while checked in). Phase 05: `incomingOrders` and `tubesNeeded` (lab technician — open orders, tube counts by type), `myLabOrders` (patient — open orders with prep, placed right after `myQueue`). Phase 08: `verificationQueue` and stats for awaiting / critical / verified / returned (pathologist); the lab's stats change to to collect / receive / test / reports to send and gain `testQueue` and `dispatchQueue`; `myReports` (patient — dispatched reports, right after the queue and tests cards) and `reportsReady` (doctor — verified reports for their orders). Phase 07: `sampleQueue` and `tubesNeeded` (lab technician — samples to collect and receive, tubes still to draw; replace `incomingOrders`), `sampleAlerts` (receptionist — patients to call back, shown only when there are open ones). Phase 06: `outstandingBills` and `collections` (receptionist, admin — bills to collect, today's takings by method), `myBills` (patient — unpaid bills).

## Status

Draft. Update this doc in the same PR that adds or changes an endpoint, and log meaningful design changes in [[Decisions]].
