# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/): grouped by version/date, entries tagged as **Added**, **Changed**, **Fixed**, or **Breaking**. Nothing has shipped yet — this file starts tracking once the first module is functional.

## [Unreleased]

Phase 01 is complete except creating the shared Supabase project; Phases 02–09 are complete. See [[Tracker]] for current status.

### Added — Phase 09 (Inventory & Admin Analytics)
- Backend: Flyway `V10__inventory.sql` — inventory items (category, unit, level, low-stock threshold, retire instead of delete) and append-only movements. A level changes only through a recorded movement (restock, used, wastage, correction; opening stock when an item is added): one conditional update that refuses to go below zero, so two people adjusting at once cannot lose a change; the database checks the direction against the reason. Lab and admin record movements; only admins add, edit or retire items. A computed "Running low" card on the lab and admin dashboards (ADR-026).
- Backend: admin-only `GET /api/admin/dashboard?days=` and `GET /api/admin/analytics/tat` — KPIs with the previous period for comparison, zero-filled daily series (clinic time zone), most-ordered tests, staff activity counted from who recorded each row, and turnaround per test (median, 90th percentile, stage breakdown, retests) measured from the sample event log, plus a test × day heatmap.
- Frontend: inventory list with search, category and low-stock filters, level bars and badges; a side panel to record a change (reason, quantity, direction for corrections, note) with the history and who made each change; admin add / edit / retire; the running-low card and a low-stock count on the bell. **Insights** page (`/admin/insights`): KPI tiles with change and sparklines, revenue area chart, patients bar chart, most-ordered tests, turnaround table and heatmap, staff activity table, a 7 / 30 / 90-day picker and a data-table toggle on every chart. Admin menu: "Overview" (today) and "Insights".
- Not built: staff account management and reagent-to-test stock decrement (see OpenQuestions).
- Tests: 209 backend, 95 frontend.

### Added — Phase 08 (Result Entry, Verification & Reporting)
- Backend: Flyway `V9__results_reports.sql` — per-attempt sample results with a value for every parameter (name, unit and ranges copied so flags stay meaningful), a number-or-text type per parameter, and reports. Database triggers: a result is decided once and never edited or deleted, values are append-only, and **a report cannot be created without a verified result**. Testing starts explicitly; the server flags each number (normal, low, high, critical); the pathologist verifies (never the person who entered it) — which creates the report — or returns for retest, keeping the attempt; rejection during testing reuses Phase 07. Report PDF drawn on request with the pathologist's stamp; dispatch by email (a notice with no clinical content, Mailpit locally) or download link, SMS unavailable; receipt recorded by one atomic update on the patient's first open (ADR-025).
- Frontend: result entry with live flags, Enter-to-advance, remembered analyzer and last-time values on a retest; start testing, awaiting-verification view, attempt history and reject-in-testing on the sample page; the bench's "To test" tab with retests pinned; pathologist queue, **focus mode** (large values, range bars, the patient's trend, retest history, V / R / J / K / Esc), and history; report reader and PDF for patients and doctors; the lab's "Reports to send" with a Send panel; dashboards for the pathologist, lab, patient and doctor; the patient's journey now runs to "Report ready"; number/text choice per parameter in the catalog editor.
- Tests: 190 backend, 76 frontend.

### Changed — Phase 08
- The lab dashboard's tiles are now to collect / to receive / to test / reports to send; the pathologist's dashboard shows a real queue instead of placeholders. The pathologist navbar drops "Profile" for now.

### Fixed — Phase 08
- Typing in a result field crashed the page (the event target was read after React had cleared it).
- Opening a report on two devices at once could return an error, because both tried to write the receipt time; it is now a single conditional update.
- A collect request without `confirmMismatch` was refused; leaving it out now means "not confirmed".

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
