# API

The Next.js frontend talks to the Spring Boot backend **only** through this REST API (see [[TechSpecifications]], ADR-008 in [[Decisions]]). This is the contract both sides build against — every screen in [[Design]] must be served by endpoints listed here. Still a draft: refine paths and payloads as each phase is implemented, and keep springdoc/Swagger output and this doc in sync.

## Conventions

- All paths below are relative to `/api` (e.g. `POST /api/auth/login`). The frontend calls them on its own origin; Next.js rewrites forward them to Spring Boot.
- JSON request/response bodies; field names in `camelCase`; timestamps in ISO-8601 UTC.
- Auth via the JWT `httpOnly` cookie (ADR-009). All endpoints require it except `/auth/login` and `/auth/register`.
- Every `POST`/`PUT`/`PATCH`/`DELETE` must send `X-CSRF-Protection: 1` (ADR-014), including login and register; missing header → `403` with code `CSRF_HEADER_MISSING`. `GET` requests never change data.
- List endpoints are paginated: `?page=0&size=20`, response `{ "content": [...], "page": n, "size": n, "totalElements": n }`.
- PDF endpoints return `application/pdf` and run the same authorization check as the record they belong to (see [[Security]] §4).
- Role checks are enforced server-side per [[Security]] — an endpoint below being "reachable" does not mean every caller can use it.
- Error responses: standard shape `{ "error": string, "code": string }` (exact code list TBD).

## Auth

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | Patient self-registration (staff accounts created by Admin, not self-registered) |
| POST | `/auth/login` | Sets JWT cookie; returns `{ id, name, role }` |
| POST | `/auth/logout` | Clears the cookie |
| GET | `/auth/me` | Current user + role — used by the frontend to pick the role area |

## Patients

| Method | Path | Role |
|---|---|---|
| GET | `/patients?q=` | Doctor, Receptionist — search by name/phone/patient ID; returns **summary only** (name, patient ID, age, gender, masked phone), plus `hasCareRelationship` for doctors |
| POST | `/patients` | Receptionist |
| GET | `/patients/{id}` | Patient (self), Receptionist, Admin — demographics/contact, no clinical fields |
| GET | `/patients/{id}/history` | Patient (self), Doctor with care relationship — full EMR (allergies, history, consultations, prescriptions, reports). Doctor without one → `403` code `NO_CARE_RELATIONSHIP`. Logged in `PatientAccessLog` |

## Appointments

| Method | Path | Role |
|---|---|---|
| POST | `/appointments` | Patient, Receptionist |
| GET | `/appointments/mine` | Patient |
| POST | `/queue/tokens` | Receptionist (walk-in token) |
| GET | `/appointments?doctorId=&date=` | Doctor, Receptionist |
| PATCH | `/appointments/{id}/status` | Receptionist, Doctor |

## Consultations & Prescriptions

| Method | Path | Role |
|---|---|---|
| POST | `/consultations` | Doctor (care relationship required) |
| POST | `/consultations/{id}/prescriptions` | Doctor |
| GET | `/patients/{id}/prescriptions` | Patient (self), Doctor |
| GET | `/prescriptions/{id}/pdf` | Patient (self), Doctor |

## Lab Tests & Orders

| Method | Path | Role |
|---|---|---|
| GET | `/lab-tests` | All authenticated (catalog + pricing) |
| POST | `/lab-orders` | Doctor (direct or auto-created from consultation) |
| GET | `/lab-orders/{id}` | Patient (self), Doctor, Lab Technician |
| GET | `/lab-orders?status=` | Lab Technician (incoming orders queue) |

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

## Status

Draft. Update this doc in the same PR that adds or changes an endpoint, and log meaningful design changes in [[Decisions]].
