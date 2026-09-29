# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/): grouped by version/date, entries tagged as **Added**, **Changed**, **Fixed**, or **Breaking**. Nothing has shipped yet — this file starts tracking once the first module is functional.

## [Unreleased]

Phase 01 is complete except creating the shared Supabase project; Phases 02–07 are complete. See [[Tracker]] for current status.

### Added — Phase 07 (Sample Lifecycle: Collection Through Rejection)
- Backend: Flyway `V7__samples.sql` — samples (one per physical tube, coded `LAB-YYYYMMDD-####`), append-only chain of custody, permanent rejection records and front-desk notifications; a database trigger allows only legal status moves and blocks deletes. `V8__backfill_samples.sql` gives existing orders their samples. Samples are created when tests are ordered; collection needs the tube and (for blood) the body site, and a wrong tube must be confirmed; the receipt check accepts or rejects — a rejection keeps the sample, notifies the front desk with the patient's phone number and creates the redraw at once. Tests can't be removed once their sample is drawn (ADR-024).
- Frontend: lab Samples bench with a scan-first field and to-collect / to-receive lists; sample page with the collection form (56px tube targets, amber mismatch confirmation), the receipt check with rejection, a printable label with a QR code and the chain of custody; the patient's sample journey on Lab tests (kind message on rejection); samples on the order page; the front desk's live bell, "Patients to call back" card and Samples to redraw inbox; lab dashboard sample bench and tube counts.
- Tests: 156 backend, 67 frontend.

### Changed — Phase 07
- The lab dashboard's tiles and tube counts now count samples (physical tubes), not order lines; the incoming-orders widget is replaced by the sample bench.

### Fixed — Phase 07
- Tube-mismatch messages read "the EDTA tube" instead of "a EDTA tube".

### Added — Phase 06 (Billing & Invoicing)
- Backend: Flyway `V6__billing.sql` — consultation fee per doctor; invoices numbered `INV-000123` with snapshot lines, append-only payments and invoice history; the database rejects a discount without who applied it and why. Finishing a consultation bills the visit (fee plus its tests); a direct lab order is billed at once; removing a test voids its line unless already paid. Part payments (cash / card / UPI), discounts with a reason and a front-desk cap (20%, admins higher), invoice PDF with Indian digit grouping (ADR-023).
- Frontend: billing counter (to collect / paid / all, searchable) with an invoice page that has Take payment and Discount panels; admin Billing pages; patient Bills pages with PDF view and download; dashboard widgets for bills to collect, today's takings by method and the patient's unpaid bills.
- Tests: 127 backend, 53 frontend.

### Fixed — Phase 06
- The payment amount now follows the balance after a discount instead of keeping the old amount.

### Added — Phase 05 (Lab Test Catalog & Ordering)
- Backend: Flyway `V5__lab_catalog_orders.sql` — lab test catalog with 22 seeded tests (price, tube/container, turnaround, patient prep) and per-parameter normal and critical ranges; lab orders numbered `LO-000123` with priority, the doctor's note, one open order per consultation and price-snapshot lines. Order from a consultation or directly, remove tests, cancel, patient/doctor/lab access rules with `LAB_HISTORY` logging, the lab's urgent-first queue, admin catalog editing (ADR-022).
- Frontend: Order tests drawer (search, categories, one-tap panels, live tubes / prep / turnaround / total, Routine or Urgent, Ctrl+Enter) in the consult workspace and on the patient record; patient Lab tests page with a "Before your test" checklist; lab Orders queue and order detail; admin Test catalog with a range editor; dashboard widgets for incoming orders, tubes to set out and the patient's tests to get done; tube chips for urine, stool and swab containers.
- Tests: 113 backend, 44 frontend.

### Fixed — Phase 05
- Prescription rows no longer nest the PDF link inside the row link (hydration error on the patient dashboard and Prescriptions page).

### Added — Phase 04 (Consultations & E-Prescriptions)
- Backend: Flyway `V4__consultations_prescriptions.sql` (consultations, prescriptions, medicine lines, formulary; triggers lock completed consultations and issued prescriptions; RX number sequence; doctor qualification and registration number). Start / autosave / finish workflow that issues the prescription and completes the appointment in one transaction; visit history; patient prescriptions; formulary search; prescription PDF via OpenHTMLtoPDF with the clinic letterhead (ADR-021).
- Frontend: consult workspace (patient rail with allergies and previous visits, vitals, notes, diagnosis, prescription builder with formulary autocomplete, advice and follow-up, autosave, Ctrl+S / Ctrl+Enter, finish confirmation); read-only consultation summary with View PDF / Download; doctor Consultations list; patient Prescriptions pages; visit history in the medical record; "Resume consultation" and "Recent prescriptions" dashboard widgets. Queue and agenda actions now start or open the consultation instead of changing the status directly.

### Added — Phase 03 (Appointments & Queue)
- Backend: Flyway `V3__appointments_queue.sql` (doctor working hours, walk-in tokens, status timestamps, append-only `appointment_events`, no-double-booking index). Booking into slots, daily tokens, role-checked status lifecycle, live queue, working-hours API, `GET /doctors/me`, reminder emails 24 h ahead (ADR-020). Malformed parameters answer 400.
- Frontend: patient booking (doctor → two-week date strip → slot grid) and "My appointments"; reception appointments day view with check-in, walk-in tokens with a printable token, and book-for-patient; animated live queue board with a names-free waiting-room screen; doctor schedule with week strip and working-hours editor; dashboard widgets for the live queue, today's visits by status and the patient's place in the queue. Admin gets a Live queue page.
- Dev: Mailpit in `docker-compose.yml`; demo working hours and a second demo doctor.
- Tests: 79 backend, 29 frontend.

### Fixed
- Navbar: the `Ctrl K` hint, clock and actions no longer wrap or overflow at laptop widths.

### Added — Phase 02 (Patient Registration & EMR)
- Backend: Flyway `V2__patient_records.sql` — patient code (`PID-000123`), blood group, medical history, emergency contact, searchable phone digits, hashed one-time registration codes, `appointments` table, append-only `patient_access_log` (DB trigger). Patient search / register / update / clinical-edit endpoints, `GET /patients/me`, `POST /auth/register/claim` (ADR-018), admin access log, server-driven dashboards `GET /dashboard/{role}` (ADR-019).
- Frontend: reception registration with printable slip, patient search and record pages for reception and doctors (locked view without a care relationship), clinical edit, patient "My record", "link my record" sign-up, admin access log, patient search in the Ctrl+K palette, widget-registry dashboards.
- Tests: 48 backend, 19 frontend.

### Changed — Phase 02
- All mock/demo dashboard data removed; dashboards show real data only, unbuilt modules appear as "upcoming" (ADR-019).
- Navbar redesigned as a floating clinical command bar with centred labelled links and a sliding ink pill (ADR-019).

### Added — Phase 01 (Foundation & Access Control)
- Backend: Spring Boot 4.1 / Java 21 REST API; Flyway `V1__identity_and_roles.sql` (users, patients, doctors, pathologists, staff); JWT in httpOnly cookie; `X-CSRF-Protection` header filter; register / login / logout / me; role-gated dashboard endpoints; dev-only demo seed.
- Frontend: Next.js 16 + Mantine 9 "Calm Precision" design system (tokens, light/dark, fonts); split-screen login and two-step patient registration; staff shell with Ctrl+K palette, shortcuts and collapsible sidebar; mobile-first patient shell; per-role dashboards; animated sample journey preview.
- Tests: 33 backend (Testcontainers Postgres) and 16 frontend (Vitest).

### Changed — visual refresh (ADR-017)
- Warm light-only palette with one deep-red accent, Outfit + IBM Plex Mono, sticky top navbar for all roles.
- Motion: scroll reveal, cursor-follow card light, button lift/press, sliding nav underline, login text reel, KPI count-up.
- Fully designed dashboards for all six roles on a labelled demo dataset: doctor schedule and current patient, pathologist critical-first queue, reception live token board, lab scan bench, admin revenue / TAT heatmap / access log, patient live journey.

### Changed (planning)
- Frontend switched from Vaadin (Java) to Next.js (JavaScript); backend stays Java Spring Boot and now exposes a REST API. See ADR-008 and ADR-009 in [[Decisions]].
- Database migrations switched from Prisma Migrate to Flyway (ADR-010).
- Pathologist is now a separate sixth role (ADR-011).
- Pathologists can return a result for retest (ADR-012).
- Lab technicians can reject a sample during testing if it is used up or degraded (ADR-013).
- CSRF protection via a required custom header (ADR-014).
- Doctors see basic details for all patients, the full record only with a care relationship; all record opens are logged (ADR-015).
- Full design spec "Calm Precision" in Design.md (ADR-016).
