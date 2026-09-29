# Test Plan

## 1. Test Levels

- **Unit tests** (JUnit + Mockito) — service-layer business logic, especially state-transition and rejection-rule enforcement in the sample lifecycle.
- **Integration tests** — repository/JPA layer against a real (test) Postgres instance; auth flow end-to-end.
- **API tests** (Spring Boot Test + MockMvc) — each endpoint's role/patient-scope checks, called over HTTP exactly as the frontend would.
- **Frontend component tests** (Vitest + React Testing Library) — forms, the sample tracker component, loading/error states.
- **End-to-end tests** (Playwright) — the full walkthrough in §2 run in a real browser against both apps.
- **Manual per-role walkthroughs** — each role's core flow (see [[Appflow]]) walked before a demo/release.

## 2. Priority Test Cases

### RBAC (see [[Rules]] §1, [[Security]])
- A patient cannot fetch/view another patient's records, samples, reports, or invoices via any path.
- A receptionist cannot access lab results or write prescriptions.
- A doctor can search any patient but gets only summary fields (masked phone, no allergies/history).
- A doctor without a care relationship gets 403 `NO_CARE_RELATIONSHIP` on history, prescriptions, reports, test orders and new consultations for that patient.
- A doctor gains access as soon as an appointment or walk-in token with them exists; a cancelled appointment alone does not grant it.
- Every full-record read by a Doctor or Pathologist writes exactly one `PatientAccessLog` row; searches write none.
- A lab technician cannot access billing.
- Calling an API endpoint directly (bypassing the UI) with another role's cookie is rejected with 403.

### CSRF (see [[Security]] §2)
- Any `POST`/`PUT`/`PATCH`/`DELETE` without `X-CSRF-Protection: 1` gets 403, even with a valid cookie — including `/auth/login`.
- A cross-origin preflight (`OPTIONS` from another origin) is not granted any `Access-Control-Allow-*` headers.
- `GET` requests work without the header.
- Only a Pathologist account can verify a result; Doctor, Lab Technician and Admin get 403.
- A pathologist cannot access billing, prescriptions, appointments or registration endpoints.

### Sample State Machine (see [[Rules]] §2, [[Appflow]] §3)
- Valid forward transitions succeed; skipping a stage is rejected.
- Every transition writes a `SampleStatusEvent` with correct staff ID + timestamp.
- Rejection at "Received at Lab" for each reason (hemolyzed, clotted, insufficient volume) correctly: sets status, creates `RejectionRecord`, notifies front desk, and does **not** delete the sample.
- Return for retest: moves `RESULT_ENTERED → IN_TESTING`, marks the old result `RETURNED_FOR_RETEST` (unchanged value), writes a `SampleStatusEvent` with the reason, and the new result gets `attempt_number + 1`.
- Return for retest without a reason, or with `OTHER` and no note, is rejected. Only a Pathologist can return; only from `RESULT_ENTERED`.
- A result marked `RETURNED_FOR_RETEST` can never be verified or used for a report.
- Rejection during testing: only from `IN_TESTING`, only by a Lab Technician, only with `SAMPLE_EXHAUSTED`, `SAMPLE_DEGRADED` or `OTHER` (+ note). Creates a `RejectionRecord` with `rejected_at_stage = IN_TESTING`, notifies the front desk, and keeps earlier results on the rejected sample.
- Receipt-only reasons (hemolyzed, clotted, insufficient volume) are refused during testing, and testing-only reasons are refused at receipt.
- A redraw after rejection creates a new `Sample` row linked to the same `LabOrderItem`, leaving the rejected one intact.
- Report generation is blocked when `TestResult.verified_at` is null — this must be enforced server-side, not just hidden in the UI.

### Billing
- Invoice total = consultation fee + sum of ordered test charges.
- Discount application requires a logged staff ID.

### Inventory
- Low-stock warning fires exactly at/below the configured threshold.
- (If reagent mapping is built) completing a test decrements the correct linked reagent by the correct quantity.

### Phase 10 (stretch features and hardening)
- A report gets a random 32-hex verification code, unique and never changeable; the PDF prints its address and a QR image (`ReportVerificationTest`).
- The public report check works without signing in, returns the report number, patient initials, tests and verifier — and no results, name or IDs; wrong, malformed and injected codes are all the same `404`.
- A report with a critical value is marked critical and listed for its ordering doctor only; the lab sees all, the admin sees a count; nobody else gets in (`CriticalAlertTest`).
- Acknowledging a critical result records who and when, only once, only by the ordering doctor; four simultaneous requests record exactly one; the database refuses to clear or edit it, or to acknowledge a non-critical report.
- Trends need at least two verified values, are oldest first, capped at twelve, and a patient sees only values from reports already sent to them; a doctor needs a care relationship and is logged; other roles get 403 (`TrendTest`).
- Five wrong passwords for an email from one address block that address (even for the right password); the real user elsewhere is unaffected; a correct sign-in resets the count; the window lets go; the per-address and claim caps hold (`AttemptLimiterTest`, `LoginRateLimitTest`).
- The API documentation endpoints are not served unless turned on (`ApiDocsTest`).

### Cross-Cutting Integration
- Doctor orders a test during consultation → a `LabOrder`/`LabOrderItem` appears with no duplicate patient data entry.
- Full walkthrough: register patient → book appointment → consult → order test → collect → receive → test → verify → report → dispatch → invoice paid. Run as `scripts/demo-walkthrough.mjs` against a dev backend: it signs in as each role and stops at the first failing step (a rejected + redrawn sample and a retest are included).

## 3. Edge Cases

- Sample rejected twice in a row for the same order item (repeated redraw failure).
- Result returned for retest several times in a row — history shows every attempt in order.
- Result returned for retest, then sample rejected as exhausted — the returned result stays on the old sample, the redraw starts at attempt 1, and the pathologist's queue no longer shows the old sample.
- Pathologist verifies and another pathologist returns the same result at the same moment — only one action wins; the other gets a conflict error.
- Doctor account attempting to verify a result (should be rejected server-side).
- A user who entered a result attempting to verify that same result (should be rejected, even if roles were misconfigured).
- Concurrent updates to the same sample's status (should not produce out-of-order or duplicate `SampleStatusEvent` rows).
- Patient with zero history (new registration) — EMR/report views should degrade gracefully, not error.

## 4. Out of Scope for Testing

Items in [[NonGoals]] (payment gateway, hardware QR scanning, etc.) have no test cases since they aren't being built.
