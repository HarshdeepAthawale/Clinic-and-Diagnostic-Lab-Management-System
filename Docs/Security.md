# Security

## 1. Threat Model Summary

Patient medical data is sensitive by nature (diagnoses, allergies, test results) — treated as PHI-equivalent even though this project doesn't pursue formal HIPAA/NABL certification (see [[NonGoals]]). The main risks this design targets:

- A user in one role accessing data belonging to another patient, or performing an action outside their role.
- A report being released without proper clinical sign-off (pathologist verification gate — see [[Rules]] §2).
- Loss of accountability for who changed what (chain-of-custody / audit trail requirements — see [[Rules]] §5, [[Schema]] `SampleStatusEvent`/`AuditLog`).
- Credential compromise (weak password storage, leaked JWT secret).

## 2. Authentication

- Spring Security + JWT (see ADR-005 in [[Decisions]]).
- Passwords stored hashed (BCrypt or equivalent) — never plaintext, never reversibly encrypted.
- JWT carries the user's role claim; every protected endpoint checks role server-side, not just hides UI elements client-side. **A hidden button — or a Next.js `proxy.js` redirect — is not access control.**
- JWT is stored in an `httpOnly`, `Secure`, `SameSite=Lax` cookie set by the backend, never in `localStorage` or anywhere JS can read it (ADR-009 in [[Decisions]]). Short expiry (e.g. 8h for staff shifts); logout clears the cookie.
- CSRF (ADR-014 in [[Decisions]]): every `POST`/`PUT`/`PATCH`/`DELETE` to `/api` — including `/auth/login` — must carry the header `X-CSRF-Protection: 1`; the backend returns 403 without it. This works because browsers only let another site send a custom header after a CORS preflight, and the backend allows **no** cross-origin requests. Rules that keep it working:
  - CORS stays disabled on the backend (no allowed origins). Enabling CORS for any origin would break this protection.
  - No state-changing `GET` endpoints.
  - The frontend sends every mutation through `lib/api.js`, which adds the header; never submit a plain HTML `<form>` directly to `/api`.
  - The `SameSite=Lax` cookie is a second layer, not the main defence.
- `JWT_SECRET` is an environment variable, never committed (see [[Setup]]).
- Registration codes (ADR-018): patients registered by the front desk link their own login with a one-time code from their slip. 10 random characters (~49 bits), only a SHA-256 hash stored, valid 30 days, single use, and only for records without a login. Unknown, expired and used codes return the same error so codes can't be probed. A rate limit on `/auth/login` and `/auth/register/claim` is still to do (see [[Tracker]]).

## 3. Authorization (RBAC)

Enforced at the service layer, matching [[Rules]] §1:

| Role | Can access |
|---|---|
| Patient | Only their own patient_id-scoped records |
| Doctor | Basic details of any patient (search); full EMR, orders and prescriptions only with a care relationship (see [[Rules]] §1). Every full-record view is logged |
| Receptionist | Registration, appointments, billing (taking payments, discounts up to the front-desk cap) — not lab results or prescriptions |
| Lab Technician | Lab orders, sample and result data — not billing, cannot verify results |
| Pathologist | Results pending verification + the related sample/patient lab history; verify action — not billing, prescriptions, appointments, or registration |
| Admin | Analytics, staff, inventory, invoices and larger discounts — not clinical write actions (prescriptions, verification) |

Every query that returns patient-scoped data must filter by the requesting user's permitted patient_id(s) at the query level, not just filter the response after fetching.

## 4. Data Handling

- No plaintext secrets in source control or docs (see [[Contributing]] checklist).
- Database connection uses TLS (Supabase default).
- `SampleStatusEvent`, `PatientAccessLog` and (if built) `AuditLog` rows are append-only — no update/delete path should exist in the application layer, even for admins. `patient_access_log` also has a database trigger that rejects `UPDATE`/`DELETE`. If a correction is needed, insert a new event; don't rewrite history.
- Prescription PDFs (and later reports and invoices) are generated on request behind the same checks as the record and sent with `Cache-Control: private, no-store`; nothing is stored, so there are no file URLs to leak.
- The doctor's clinical notes are never returned to patients; completed consultations and issued prescriptions can't be edited or deleted (database triggers).
- Billing: payments and billing events are append-only (database triggers); a discount without who applied it and why is rejected by the database. The front-desk discount cap is enforced on the server, not only in the form. Invoice PDFs are generated on request behind the same checks as the invoice.
- Lab orders: the doctor's note for the lab is left out of the patient's view; ordered tests keep their name and price at order time; orders and lines are cancelled, never deleted. A doctor reading an order or a patient's order history writes a `LAB_HISTORY` access-log row.
- Report PDFs and other exported documents should only be servable to a caller who is authorized to view the underlying record — a guessable/sequential URL to a PDF is a data leak even if the "screen" is protected.

## 5. Input Validation

- Next.js forms validate on the client for UX, but the backend must re-validate every request (Bean Validation on DTOs) — client-side validation is not a security boundary.
- Never render user-supplied content with `dangerouslySetInnerHTML`; rely on React's default escaping.
- No secrets in frontend code or `NEXT_PUBLIC_*` variables — anything shipped to the browser is public.
- Backend responses use DTOs, never raw JPA entities, so internal fields aren't leaked to the frontend.
- Sample rejection reasons, tube types, etc. should be enums, not free text, wherever the value drives logic (see [[Schema]]).

## 6. Record Access Logging

- Every full-record read by a Doctor or Pathologist writes a `PatientAccessLog` row (see [[Schema]] §6) in the same request — if the log write fails, the record is not returned.
- The care-relationship check runs inside the database query (join on `Appointment`/`Consultation`), not as a filter after loading the record.
- Patient search returns a summary DTO only; the phone number is masked (e.g. `98******21`). Search results are not logged, record opens are.
- Admin can view the access log (who looked at what, when) but not the clinical content itself.
