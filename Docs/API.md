# API

The Next.js frontend talks to the Spring Boot backend **only** through this REST API (see [[TechSpecifications]], ADR-008 in [[Decisions]]). This is the contract both sides build against — every screen in [[Design]] must be served by endpoints listed here. Still a draft: refine paths and payloads as each phase is implemented, and keep springdoc/Swagger output and this doc in sync.

## Conventions

- All paths below are relative to `/api` (e.g. `POST /api/auth/login`). The frontend calls them on its own origin; Next.js rewrites forward them to Spring Boot.
- JSON request/response bodies; field names in `camelCase`; timestamps in ISO-8601 UTC.
- Auth via the JWT `httpOnly` cookie (ADR-009). All endpoints require it except `/auth/login`, `/auth/register` and `/auth/register/claim`.
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
| POST | `/auth/login` | Sets JWT cookie; returns `{ id, name, role }` |
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

| Method | Path | Role |
|---|---|---|
| POST | `/samples/{id}/collect` | Lab Technician |
| POST | `/samples/{id}/receive` | Lab Technician (may result in `RECEIVED_AT_LAB` or `REJECTED`) |
| POST | `/samples/{id}/results` | Lab Technician |
| POST | `/samples/{id}/reject` | Lab Technician — only while `IN_TESTING`; body `{ reason: SAMPLE_EXHAUSTED \| SAMPLE_DEGRADED \| OTHER, note }` |
| POST | `/samples/{id}/verify` | Pathologist |
| POST | `/samples/{id}/return-for-retest` | Pathologist — body `{ reason, note }`; moves sample back to `IN_TESTING` |
| GET | `/samples/{id}/results` | Lab Technician, Pathologist (all attempts, incl. returned ones) |
| GET | `/samples/{id}/status` | Patient (self), Doctor, Pathologist, Receptionist, Lab Technician |
| GET | `/samples/{id}/events` | Lab Technician, Pathologist, Admin (full chain-of-custody log) |
| GET | `/samples/pending-verification` | Pathologist |
| GET | `/samples/verified-by-me` | Pathologist |
| GET | `/notifications` | Receptionist (sample-rejected inbox), others as needed |

## Reports

| Method | Path | Role |
|---|---|---|
| GET | `/reports/{sample_id}` | Patient (self), Doctor, Pathologist |
| GET | `/reports/{sample_id}/pdf` | Patient (self), Doctor |

## Billing

| Method | Path | Role |
|---|---|---|
| GET | `/invoices/{id}` | Patient (self), Receptionist, Admin |
| GET | `/invoices/{id}/pdf` | Patient (self), Receptionist, Admin |
| POST | `/invoices/{id}/pay` | Receptionist |

## Inventory

| Method | Path | Role |
|---|---|---|
| GET | `/inventory` | Lab Technician, Admin |
| PATCH | `/inventory/{id}/stock` | Lab Technician, Admin |

## Admin / Analytics

| Method | Path | Role |
|---|---|---|
| GET | `/admin/dashboard` | Admin |
| GET | `/admin/analytics/tat?testId=` | Admin |
| POST | `/admin/staff` | Admin (all staff roles, incl. Pathologist with registration details) |
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

`span` is `full`, `wide` (2/3) or `narrow` (1/3). The frontend renders each `type` from its widget registry and skips unknown types. Widgets only ever carry real data; an unbuilt module is an `upcoming` widget. Phase 03 widget types: `liveQueue` (the `/queue` board), `visitsByStatus` (today's counts per status, admin), `myQueue` (patient's token, token now being seen, how many ahead — only while checked in). Phase 05: `incomingOrders` and `tubesNeeded` (lab technician — open orders, tube counts by type), `myLabOrders` (patient — open orders with prep, placed right after `myQueue`).

## Status

Draft. Update this doc in the same PR that adds or changes an endpoint, and log meaningful design changes in [[Decisions]].
