# Decisions Log (ADR-style)

Each entry: what was chosen, why, and what alternatives were considered. Add a new entry rather than editing history — if a decision is reversed, add a new ADR that supersedes the old one and mark the old one as superseded.

---

## ADR-001: Backend framework — Java Spring Boot

**Status:** Accepted
**Context:** Need an industry-standard, well-supported framework for a Java-based REST/service backend with routing, DI, and security built in.
**Decision:** Use Spring Boot.
**Alternatives considered:** Plain Java EE/Jakarta EE, Micronaut, Quarkus. Spring Boot chosen for ecosystem maturity and tight integration with Spring Security and Spring Data JPA.

---

## ADR-002: Database — PostgreSQL hosted on Supabase

**Status:** Accepted
**Context:** Domain data (patients, appointments, tests, results) is naturally relational, connected via foreign keys.
**Decision:** PostgreSQL, hosted on Supabase (managed instance + dashboard).
**Alternatives considered:** Self-hosted Postgres (more setup overhead), a NoSQL store (poor fit for highly relational, foreign-key-heavy domain).

---

## ADR-003: Split migration tool (Prisma Migrate) from runtime ORM (JPA/Hibernate)

**Status:** Superseded by ADR-010
**Context:** Need schema-as-code with versioned, repeatable migrations, but the runtime app is pure Java.
**Decision:** Use Prisma Migrate purely as a schema/migration authoring tool (Node.js, dev-time only). Runtime data access is exclusively Spring Data JPA + Hibernate — Prisma's client is never used at runtime.
**Alternatives considered:** Flyway or Liquibase (native Java migration tools — would avoid the Node.js dependency entirely). This is a notable deviation and worth revisiting; see [[OpenQuestions]].
**Consequence:** Adds a Node.js toolchain dependency to an otherwise all-Java project, solely for migrations.

---

## ADR-004: Frontend — Vaadin (Java) instead of a separate JS SPA

**Status:** Superseded by ADR-008
**Context:** Frontend quality carries significant evaluation weight; project favors "Java throughout."
**Decision:** Use Vaadin — UI written in Java inside the same Spring Boot project, calling backend services directly.
**Alternatives considered:** React/Vue/Angular SPA with a separate REST API layer — more standard industry pattern but doubles the codebase's language surface and requires hand-written REST contracts for every UI interaction.

---

## ADR-005: Authentication — Spring Security + JWT

**Status:** Accepted
**Context:** Need role-based access control across five distinct roles (Patient, Doctor, Receptionist, Lab Technician, Admin).
**Decision:** Spring Security with JWT-based auth.
**Alternatives considered:** Session-based auth (simpler for a monolith with server-rendered-ish Vaadin UI, but JWT keeps the door open for future API consumers, e.g. a mobile app).

---

## ADR-006: PDF generation library — undecided

**Status:** Superseded by ADR-021
**Context:** Need to generate lab reports and invoices as PDFs.
**Options:** iText7 vs Apache PDFBox.
**Decision:** Not yet made — tracked in [[OpenQuestions]].

---

## ADR-007: SMS notification provider — undecided

**Status:** Open
**Context:** Optional SMS notifications for reminders/report-ready alerts.
**Options:** Twilio "or similar" — no specific provider chosen.
**Decision:** Not yet made — tracked in [[OpenQuestions]].

---

## ADR-008: Frontend — Next.js in JavaScript, backed by a Spring Boot REST API

**Status:** Accepted (supersedes ADR-004)
**Context:** Project requirement: backend in Java, frontend in JavaScript.
**Decision:** Build the frontend as a separate Next.js app (App Router) written in plain JavaScript (no TypeScript), using Mantine for UI components/charts and TanStack Query for data fetching. Spring Boot exposes a REST/JSON API under `/api` that is the only way the frontend reads or writes data. Next.js must not access the database or hold business logic.
**Alternatives considered:** Keep Vaadin (conflicts with the JavaScript requirement); plain React + Vite SPA (lighter, but Next.js gives file-based routing and per-role layouts out of the box); Vue/Angular.
**Consequence:** [[API]] becomes a required contract; two deployables instead of one JAR; Node.js is now a core toolchain dependency.

---

## ADR-009: JWT delivery — httpOnly cookie through a same-origin proxy

**Status:** Accepted
**Context:** With a JavaScript frontend, the JWT must live in the browser. Storing it in `localStorage` exposes it to any XSS.
**Decision:** On login, Spring Boot sets the JWT as an `httpOnly`, `Secure`, `SameSite=Lax` cookie. Next.js rewrites `/api/*` to the backend, so the browser sees one origin and the cookie is first-party. The frontend never reads the token; it calls `GET /api/auth/me` to learn the current user and role. Because cookies are sent automatically, state-changing requests are protected against CSRF (SameSite cookie plus a required custom header — see ADR-014).
**Alternatives considered:** Token in `localStorage` + `Authorization` header (simpler, XSS-exposed); server-side sessions (would contradict ADR-005).

---

## ADR-010: Database migrations — Flyway

**Status:** Accepted (supersedes ADR-003)
**Context:** ADR-003 used Prisma Migrate (Node.js) for schema migrations while JPA/Hibernate handled runtime access. That meant the schema was defined twice (`schema.prisma` and JPA entities), migrations were a separate deploy step outside the backend, and a Prisma setup in a repo that also contains a Next.js app invited direct DB access from the frontend.
**Decision:** Use Flyway inside the Spring Boot backend. Migrations are versioned SQL files in `backend/src/main/resources/db/migration/` (`V1__identity_and_roles.sql`, `V2__...`), applied automatically on startup. Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate` so it never modifies the schema and fails fast on entity/schema drift. Dev/demo seed data lives in a separate location (`db/seed/`) enabled only for the `dev` profile.
**Rules:** Never edit a migration that has been applied to a shared database — add a new one. Constraints that protect domain rules (enums/check constraints, append-only triggers on `SampleStatusEvent`/`AuditLog`) belong in the SQL migrations, not only in Java.
**Alternatives considered:** Keep Prisma Migrate (nicer schema DSL, but duplicate source of truth and extra deploy step); Liquibase (similar to Flyway, but XML/YAML changelogs are more verbose than plain SQL for this project's size).
**Consequence:** Backend owns the schema end to end; deploying the backend applies its migrations; Node.js is needed only for the frontend.

---

## ADR-011: Pathologist is a separate role

**Status:** Accepted
**Context:** The verification gate ([[Rules]] §2) needs a clear answer to "who may sign off a lab result". The alternative was a boolean `is_pathologist_capable` flag on `Doctor`.
**Decision:** Add `PATHOLOGIST` as a sixth role with its own `Pathologist` profile table (qualification, registration number, signature image — all printed on the report stamp). `TestResult.verified_by_pathologist_id` references it. Only this role can call the verify endpoints; it has no access to billing, prescriptions, appointments or registration. One role per account — someone who is both a doctor and a pathologist uses two accounts. Admin creates pathologist accounts.
**Alternatives considered:** Doctor flag (fewer accounts, but the permission check becomes "role + flag", and the pathologist's report-stamp details don't belong on every doctor); multiple roles per user (more flexible, but complicates JWT claims and RBAC for little benefit here).
**Consequence:** Six role areas in the frontend (adds `app/pathology/`); separation of duties between result entry (Lab Technician) and verification (Pathologist) is enforced by role.

---

## ADR-012: Pathologists can return a result for retest

**Status:** Accepted
**Context:** A pathologist who doubts a result (implausible value, inconsistent with history, critical value) previously had only one option — verify. Real labs routinely rerun such tests before sign-off.
**Decision:** Allow one backward transition, `RESULT_ENTERED → IN_TESTING`, triggered only by a Pathologist with a required reason (enum + note for `OTHER`). The same sample is retested — no redraw. The original `TestResult` row is kept with status `RETURNED_FOR_RETEST`; the retest creates a new row with the next `attempt_number`. The transition is logged in `SampleStatusEvent` like any other. No limit on retests; the count is shown.
**Alternatives considered:** Verify-or-nothing (forces sign-off on doubtful results); treat it as a rejection + redraw (needlessly re-collects from the patient when the sample is fine); letting the technician edit the result in place (destroys the audit trail).
**Consequence:** The "forward only" rule in [[Rules]] §2 now has two explicit exceptions (rejection and retest). The patient tracker stays at "In Testing" during a retest. TAT for retested samples includes the retest time.

---

## ADR-013: Lab technicians can reject a sample during testing

**Status:** Accepted
**Context:** After ADR-012, a result can be returned for retest — but there may not be enough sample left, or it may have degraded. Rejection was only possible at "Received at Lab", leaving such samples stuck in `In Testing`.
**Decision:** Allow `IN_TESTING → REJECTED`, by a Lab Technician only, with testing-specific reasons `SAMPLE_EXHAUSTED` and `SAMPLE_DEGRADED` (or `OTHER` with a note). It reuses the existing rejection flow: `RejectionRecord` (now with `rejected_at_stage`), front-desk notification, and a redraw as a new `Sample` row. Results already entered on the rejected sample are kept.
**Alternatives considered:** Leave the sample stuck (no way forward); let the pathologist request a redraw directly (they don't handle the physical sample, so they can't know it's used up); treat it as a new state like `AWAITING_REDRAW` (duplicates what rejection already does).
**Consequence:** Rejection is allowed from two stages, each with its own reason list, enforced by a check constraint. The patient tracker shows the same "sample needs to be redrawn" message as a receipt rejection.

---

## ADR-014: CSRF protection — required custom header

**Status:** Accepted
**Context:** The JWT is sent automatically in a cookie (ADR-009), so another website could try to make a logged-in user's browser send requests (CSRF).
**Decision:** Every `POST`/`PUT`/`PATCH`/`DELETE` to `/api` — including login — must include `X-CSRF-Protection: 1`. A Spring Security filter rejects mutating requests without it (403); Spring's built-in token-based CSRF is disabled. Browsers only let another origin add custom headers after a CORS preflight, and the backend allows no cross-origin requests, so a forged request can't include the header. The frontend's `lib/api.js` adds it to every mutation. `SameSite=Lax` on the cookie stays as a second layer.
**Alternatives considered:** Spring Security's synchronizer/double-submit token (strong, but the frontend must fetch, store and echo a token, and handle expiry — more moving parts for no real gain here); relying on `SameSite` alone (not enough on its own — older browsers and same-site subdomains).
**Consequence:** CORS must stay closed — opening it to any origin later would silently remove this protection, so any such change needs a new ADR. No endpoint may change data on `GET`. Plain HTML form posts to `/api` won't work; all mutations go through `fetch`.

---

## ADR-015: Doctor access to patient records — care relationship + access log

**Status:** Accepted
**Context:** Doctors need to find any patient (e.g. a walk-in), but medical records are sensitive and shouldn't be open to every doctor.
**Decision:** Two tiers. Any doctor can search all patients and see a summary (name, patient ID, age, gender, masked phone). The full record — and ordering tests, prescribing, starting a consultation — requires a **care relationship**: a non-cancelled appointment/walk-in token with that doctor, or a past consultation by them. The front desk (or the patient) creates the relationship by booking; doctors can't grant it to themselves. Every full-record read by a Doctor or Pathologist is written to an append-only `PatientAccessLog`, reviewable by Admin.
**Alternatives considered:** All doctors see everything (simplest, but no need-to-know limit); own patients only with no search (blocks walk-ins); break-glass emergency override (useful in hospitals, unnecessary for a booked/queued clinic — can be added later as a new ADR).
**Consequence:** The relationship check depends on `Appointment`/`Consultation`, so it's fully testable from Phase 03. Access logging adds one insert per record view.

---

## ADR-016: Design language — "Calm Precision"

**Status:** Accepted; palette, fonts, dark mode and navigation superseded by ADR-017
**Context:** Frontend quality carries significant evaluation weight, and the team wants a distinctive, polished UI rather than a template admin panel — without compromising clinical clarity.
**Decision:** Adopt the design system in [[Design]]: neutral canvas + one teal brand color, strictly reserved semantic status colors, per-role accent colors for wayfinding, Inter / JetBrains Mono / Instrument Serif, full dark mode, keyboard-first staff workflows with a command palette, and a set of signature experiences (sample journey, pathologist focus mode, lab bench mode, live reception queue, doctor consult workspace). Built on Mantine + `@mantine/spotlight` + `motion` + Tabler icons.
**Alternatives considered:** Plain Mantine defaults (fast but generic); Tailwind + shadcn/ui (great look, but shadcn is TypeScript-first and we'd hand-build tables, forms and date pickers Mantine already provides).
**Consequence:** Every UI PR follows the review checklist in [[Design]] §12. Signature experiences are scheduled with their phases rather than all up front.

---

## ADR-017: Visual refresh — warm light palette, Outfit, top navbar, light mode only

**Status:** Accepted; demo-data dashboards and the top-navbar description superseded by ADR-019 (supersedes the palette, typography, dark-mode and sidebar parts of ADR-016; the principles, signature experiences and component patterns of ADR-016 stand)
**Context:** The team wants a cleaner look with fewer colors, richer animation, a top navbar after login, and no dark mode. Reference sites: Thapar Nexus (palette, fonts, navbar) and ObsidianUI (motion ideas).
**Decision:**
- Palette: warm neutral canvas (`#f5f4f1`), white surfaces, one deep-red accent (`#b42318`); status colors only for status. No per-role accent colors.
- Fonts: Outfit for all UI and headings, IBM Plex Mono for codes and numbers.
- Light mode only; no theme toggle.
- Navigation: one sticky top navbar for every role (drawer on tablets/phones for staff, bottom tabs for patients), replacing the staff sidebar.
- Motion: scroll reveal, cursor-follow card light, hover lift, sliding nav underline, login text reel, KPI count-up — all disabled under reduced motion.
- Role dashboards are fully designed now and run on a labelled demo dataset (`lib/demo`), each panel tagged "Demo data" with the phase that makes it live.
**Consequence:** Because the accent and "critical" share a red, critical states must always use the octagon icon + explicit word + tinted banner/badge ([[Design]] §2.1). Demo data must be removed panel by panel as phases connect real APIs.


---

## ADR-018: One-time registration codes for front-desk-registered patients

**Status:** Accepted
**Context:** Most patients are first registered by the front desk (walk-ins, phone bookings) and have no login. Later they want to see their own record and reports. Matching them by name/phone/date of birth at sign-up is guessable and would let anyone who knows those details take over a record.
**Decision:**
- On registration the front desk gets a one-time **registration code** printed on the patient's slip: 10 characters from an alphabet without look-alikes (no `0/O`, `1/I/L`), shown as `ABCDE-FGH23` (~49 bits of randomness).
- Only a **SHA-256 hash** is stored (`patients.claim_code_hash`); the plain code is returned exactly once. Case, spaces and dashes are ignored when the patient types it.
- Valid for **30 days**, **single use**, and only while the record has no login. The front desk can issue a fresh code (`POST /patients/{id}/registration-code`), which replaces the old one.
- `POST /auth/register/claim` (email + password + code) creates the `PATIENT` user and links it. Unknown, expired and already-used codes all return the same `400 INVALID_REGISTRATION_CODE`, so codes can't be probed.
- The `appointments` table is created in Phase 02 (not Phase 03) because the doctor care-relationship check (ADR-015) depends on it.
**Alternatives considered:** Match on phone + date of birth (guessable); email/SMS invite link (needs a mail/SMS provider — ADR-007 still open); front desk sets a temporary password (staff would know patient passwords).
**Consequence:** A lost slip means a trip to (or call with) the front desk. The claim endpoint should get the same rate limit as login once one is added (see [[Security]]).

---

## ADR-019: Server-driven widget dashboards, real data only; navbar as a clinical command bar

**Status:** Accepted (supersedes the demo-data dashboards and the top-navbar description of ADR-017)
**Context:** The Phase 01 dashboards ran on a labelled demo dataset. The team decided that no mock data should ship anywhere — every number on screen must be real — and that dashboards must scale as each phase adds modules without rewriting each role's page.
**Decision:**
- `GET /dashboard/{role}` returns `{ role, widgets: [{ type, title, span, data }] }`. The frontend keeps a **widget registry** (one component per `type`); unknown types are skipped, so a phase can add a widget server-side and ship its component independently.
- Widgets query real tables only. A module that isn't built yet appears as an honest `upcoming` widget (module name, phase, description) — never fake numbers. `lib/demo` and all "Demo data" tags are removed.
- `span` (`full`, `wide`, `narrow`) is a layout hint; the grid collapses to one column on small screens.
- Navigation becomes a **clinical command bar**: a white floating bar with the wordmark on the left, centred labelled links with a sliding ink pill for the active page, and on the right patient search (Ctrl+K), a live clock and one red primary action for the role (e.g. "Register patient").
**Consequence:** New roles' dashboards look sparse until their phases land — accepted, since an empty state is honest. Every phase that adds a module also adds or upgrades its widgets.

---

## ADR-020: Appointments & queue — slots from working hours, daily tokens, server-enforced lifecycle

**Status:** Accepted
**Context:** Phase 03 needs booking, walk-ins and a live queue that the front desk, doctors, patients and admin all see consistently, without double bookings or illegal status jumps.
**Decision:**
- **Working hours** are weekly blocks per doctor (`doctor_schedules`: day, start, end, slot length; several blocks a day allowed). Slots are cut from them on the fly; a booking must start exactly on a slot boundary, in the future, at most 60 days ahead. Doctors edit their own hours (admin can too); existing bookings are never moved.
- **No double booking** is enforced by the database: a partial unique index on `(doctor_id, scheduled_at)` for live scheduled visits. Cancelling frees the slot. One live booking per patient per doctor per day.
- **Tokens** (`T-001`, `T-002`, …) are numbered per clinic day across the whole clinic, assigned at check-in (walk-ins are checked in when the token is issued). Numbering is serialised with a transaction-scoped advisory lock. Queue order is check-in order.
- **Lifecycle** `BOOKED → CHECKED_IN → IN_CONSULTATION → COMPLETED`, plus `NO_SHOW` and `CANCELLED`, with a fixed table of which role may make each move (Rules.md §1a). A doctor has at most one patient `IN_CONSULTATION`. Every change is written to the append-only `appointment_events` history (DB trigger rejects updates/deletes).
- **Reminders:** one email per booked appointment once it is within 24 h, sent by a scheduled job that claims rows with `FOR UPDATE SKIP LOCKED` (safe with several backend instances). Patients without a login have no email and are skipped; SMS waits on ADR-007. Without a mail server the reminder is only logged. Local dev catches mail in Mailpit.
- **Live views poll** (10–15 s via TanStack Query) instead of WebSockets.
- **Waiting-room screen** shows tokens only, never patient names.
**Alternatives considered:** Fixed slot table pre-generated per day (heavy to maintain when hours change); per-doctor token numbers (confusing when a patient hears "7" for two doctors); WebSocket/SSE push (more moving parts than a small clinic needs today — can replace polling later without changing the API).
**Consequence:** Appointments created before Phase 03 have no token until checked in. Changing hours doesn't warn about bookings now outside them — a later phase can list those for the front desk.

---

## ADR-021: Consultations, e-prescriptions and PDF generation

**Status:** Accepted (resolves ADR-006)
**Context:** Phase 04 turns a visit into a medical record and a prescription the patient can download. The record must not change after the fact, and PDFs are needed again later for lab reports (Phase 08) and invoices (Phase 06).
**Decision:**
- **One consultation per appointment.** The doctor starts it on their own checked-in appointment — starting calls the patient in (`CHECKED_IN → IN_CONSULTATION`). It stays a `DRAFT` (autosaved) while the patient is in the room.
- **Finishing is one transaction:** it requires a diagnosis, issues the prescription (if it has medicines) with the next number from a sequence (`RX-000123`), marks the consultation `COMPLETED` and the appointment `COMPLETED`. If any step fails, nothing changes.
- **Locked after finishing.** Database triggers reject any update or delete of a completed consultation, an issued prescription, or its medicine lines. Corrections will be added as amendments later, never edits.
- **Structured medicines** (`prescription_items`: medicine, dose, frequency, duration, instructions) instead of free-text JSON, plus a small **formulary** of common generics for autocomplete. Free text is still allowed.
- **Doctor details on prescriptions:** doctors gain `qualification` and `registration_number`.
- **Who sees what:** the author doctor always; other doctors only completed consultations of patients they have a care relationship with; the patient only their own completed consultations, **without the doctor's working notes**. Every doctor read is written to the access log. The front desk sees no clinical content.
- **PDFs:** XHTML templates (Thymeleaf, XML mode so everything is escaped) rendered by **OpenHTMLtoPDF on Apache PDFBox** (LGPL). PDFs are generated on request behind the same access checks as the record — nothing is stored, so there are no guessable file URLs. The clinic letterhead comes from `app.clinic.*` settings.
**Alternatives considered:** iText 7 (AGPL — would force the whole app to be open-sourced or a commercial licence); drawing PDFs directly with PDFBox (more code per document and harder to style); storing generated PDFs (extra storage and an access-control surface for no benefit at this scale).
**Consequence:** The standard PDF fonts cover Latin text only; names in other scripts need an embedded font later. The same renderer is reused for reports and invoices.

---

## ADR-022: Lab test catalog and ordering — per-parameter ranges, one open order per consultation, price snapshots

**Status:** Accepted
**Context:** Phase 05 is the hinge between the clinic and the lab: a doctor orders tests during a visit and the lab sees them with no re-typing. The planned schema had one reference range per test, but most tests report several values (a CBC has haemoglobin, WBC, platelets…), and later phases need critical limits and billing needs stable prices.
**Decision:**
- **Catalog:** `lab_tests` (code, name, category, sample type, required tube/container, price, turnaround hours, patient prep, active flag) plus `lab_test_parameters` — each with its own unit, normal range and critical limits. 22 common tests ship with the schema as reference data; admins add, edit and retire tests. Tests are retired, never deleted.
- **Tube types** are an enum that also covers sterile containers: `EDTA`, `PLAIN`, `SST`, `CITRATE`, `FLUORIDE`, `HEPARIN`, `URINE_CUP`, `STOOL_CUP`, `SWAB_TUBE`.
- **Ordering from a consultation:** only the consultation id is sent — patient and doctor come from it. A consultation has **at most one open order** (partial unique index); ordering again adds tests to it, and a test can't be on it twice. Only the author may order, and only while the consultation is a draft.
- **Direct orders** (outside a visit) name the patient and need a care relationship (ADR-015).
- **Snapshots:** each line copies the test name and price at order time, so later catalog edits never change what was ordered or billed.
- **Changes:** the ordering doctor can remove a test or cancel the order while it is open; removing the last test cancels it. Nothing is deleted.
- **Who sees what:** the patient their own orders with prep, **without the doctor's note for the lab**; doctors orders they placed or for patients under their care (logged as `LAB_HISTORY`); lab technicians and pathologists all orders. Orders are numbered `LO-000123`.
**Alternatives considered:** one range per test (as first planned — can't represent multi-value tests); a new order per "Order tests" click (splits one visit's tests across several orders and several lab tickets); price looked up at billing time (a price change would silently change old bills).
**Consequence:** Phase 07 attaches samples to order lines and will stop cancellation once a sample is collected. Phase 08 checks results against `lab_test_parameters`. Phase 06 bills from the snapshot prices.

---

## ADR-023: Billing — one invoice per visit, created by the system, with accountable discounts

**Status:** Accepted
**Context:** Phase 06 turns a finished visit into a bill. The planned schema had one invoice with a single discount staff field and a three-value status. It didn't say when the invoice is created, how tests removed from an order affect it, how part payments are kept, or who may give how much discount.
**Decision:**
- **Created by the system, never typed in.** Finishing a consultation creates the visit invoice in the same transaction: the doctor's **consultation fee** (a per-doctor setting, default ₹500) plus the tests on the consultation's open lab order. A lab order placed outside a visit is billed straight away on its own invoice. Numbers are `INV-000123`; one invoice per consultation, one per direct order.
- **Lines with snapshots.** Each charge is an invoice line whose amount was copied from the order line's price at order time. A test removed from an order **voids its line** (kept, struck through); if nothing is left, the invoice becomes `VOID`. A test the patient has **already paid for** can't be removed (`409 ALREADY_PAID`) — that needs settling at the counter first.
- **Payments are separate, append-only rows** (amount, method `CASH`/`CARD`/`UPI`, optional reference, who received it). Part payments are allowed, never more than the balance. The status follows them: `UNPAID` → `PARTIALLY_PAID` → `PAID`.
- **Discounts are accountable.** A discount needs a **reason** and records **who applied it and when**; the database rejects a discount without them. The **front desk is capped** at a configurable share of the bill (`app.billing.reception-discount-cap-percent`, default 20%); an **admin can go further**. A discount can't exceed the bill or leave it below what is already paid. Every step (created, line voided, discount, payment) is also written to an append-only `invoice_events` history, so a changed discount stays traceable.
- **Who sees what:** patients their own invoices and PDFs; the front desk and admins all invoices; only the front desk takes payments; doctors, lab and pathologists see no billing.
- **PDF** through the same renderer as prescriptions. The built-in PDF fonts have no ₹ sign, so the PDF prints `Rs. 1,25,000.00` with Indian digit grouping; the web app shows ₹.
- **No payment gateway.** Payments are recorded by staff at the counter. Online payment stays out of scope ([[NonGoals]], [[OpenQuestions]]).
**Alternatives considered:** creating the invoice at first order or at check-in (would change while the visit is still going); one mutable "amount paid" number (loses who took what and when); a free discount for the front desk (no control over leakage); storing PDFs (extra storage and an access surface, as in ADR-021).
**Consequence:** Phase 07/08 don't affect billing. GST/tax and refunds are not modelled — a refund would be a new payment type with its own event, added when needed.

---

## ADR-024: Samples — one per physical tube, created at order time, rules enforced in the database

**Status:** Accepted
**Context:** Phase 07 starts the sample pipeline. The planned schema tied each sample to a single order line, but a lab draws one tube for every test that needs it (a CBC and an ESR share an EDTA tube) — one sample per line would draw the same blood twice. It also left open when samples come into being, how a redraw is linked, and how the front desk is told.
**Decision:**
- **One sample per physical tube.** All of an order's tests that need the same tube share one `Sample` (via `sample_items`). Samples are created by the system when tests are ordered, numbered `LAB-YYYYMMDD-####` from a daily counter (clinic time zone); a test ordered later joins the order's not-yet-collected sample for its tube, or gets a new one.
- **The state machine is in the database.** A trigger allows only the moves in [[Rules]] §2 — forward through the pipeline, rejection from the receipt check or testing, and return for retest. `samples` rows can't be deleted; `sample_status_events` (the chain of custody) and `rejection_records` are append-only; a rejection's reason must fit its stage. The Java enum mirrors the same table.
- **Collection** records the tube used, the body site (required for blood) and who/when. A tube other than the one the tests need is **refused unless the technician confirms**, and is then flagged on the sample, in the custody log and again at the receipt check — never accepted silently.
- **Receipt check:** accept (`RECEIVED_AT_LAB`) or reject. A rejection keeps the sample, writes a `RejectionRecord` (stage, reason, note, who), **creates a front-desk notification** with the patient's phone number, and **creates the redraw sample straight away** (status `ORDERED`, `redraw_of_sample_id` pointing back, same tests) so it is already waiting when the patient returns. Results start again at attempt 1 on the redraw. Phase 08 adds rejection during testing through the same code.
- **Order changes:** tests whose sample hasn't been drawn can still be removed (a sample left with no tests is `CANCELLED`); once it is drawn, removing a test or cancelling the order answers `409 SAMPLE_COLLECTED`.
- **Notifications:** a small `notifications` table (audience role, type, message, who handled it); rejections go to the receptionist, who marks them handled once the patient is called. The bell and dashboard show the open count.
- **Who sees what:** the lab collects and receives; lab, pathologists and admins see the full custody log with names; the front desk and doctors see status only; patients see their own samples as a journey with **no staff names and no rejection reason** — just "We need a new sample". A doctor viewing a patient's samples is logged as `LAB_HISTORY`.
- **QR codes:** the label carries a QR of the sample code, drawn as SVG from the QR module grid (no HTML injected). The bench's scan field takes a typed code or a barcode scanner (which types the code); camera scanning isn't built. The code is also printed in words under the QR.
- **Existing orders** (from Phase 05/06, before samples existed) are given samples by migration V8, once, grouped by tube.
**Alternatives considered:** one sample per order line (double draws, more labels); creating samples at collection time (nothing for the lab to see or scan until then, and no place to hang the label); a "redraw" flag on the same sample (loses the rejected sample's history — the rule is that it is permanent); enforcing transitions only in Java (a bug or a manual fix could break the chain of custody).
**Consequence:** Phase 08 attaches results to samples, moves `RECEIVED_AT_LAB → IN_TESTING`, and adds return for retest and rejection during testing. Billing is unaffected — invoices bill order lines, and a redraw is the same lines.

---

## ADR-025: Results, verification and reports — results per sample, the gate in the database, reports drawn on request

**Status:** Accepted
**Context:** Phase 08 finishes the sample pipeline. The planned `TestResult` held one value per row against a single reference range, but a sample (one tube) carries several tests and each test several parameters (a CBC has five). The verification gate must hold even if a code path is wrong, retests must never lose the first result, and a patient must not see a report before it is sent.
**Decision:**
- **Results belong to a sample, one attempt at a time.** `sample_results` is one attempt (attempt number, analyzer, who entered it, when); `result_values` holds a value for every parameter of every test on the sample. The technician must enter **all** of them. Each value keeps a copy of the parameter's name, unit and ranges, so its flag stays meaningful if the catalog changes later.
- **The server flags every number** against normal range and critical limits (critical wins and is inclusive; a missing limit is never checked): `NORMAL`, `LOW`, `HIGH`, `CRITICAL_LOW`, `CRITICAL_HIGH`. The form shows the same flag as the technician types, but the stored flag is the server's. Parameters have a **number or text** type (`value_type`); text results (Positive, blood group) carry no flag.
- **Testing starts explicitly** (`RECEIVED_AT_LAB → IN_TESTING`). Entering results moves the sample to `RESULT_ENTERED`.
- **Verification is the only route to a report.** A pathologist verifies (never the person who entered the result), which in one transaction marks the result `VERIFIED`, the sample `VERIFIED`, creates the `Report` and moves the sample to `REPORT_GENERATED`. The database enforces the gate: a trigger refuses to insert a report unless a verified result exists.
- **Return for retest** keeps the attempt (`RETURNED_FOR_RETEST`, with who, when, reason and note) and sends the same sample back to `IN_TESTING`; the retest is the next attempt. The lab's testing list pins returned samples first with the reason. A result is decided once — a trigger allows only `PENDING → VERIFIED` or `PENDING → RETURNED`, and values and attempts can't be edited or deleted.
- **Rejection during testing** reuses Phase 07's rejection (permanent record, front-desk call-back, redraw); results already entered are kept.
- **Reports are drawn on request**, not stored (as with prescriptions and invoices): letterhead, patient, each test's values with unit, range and flag, and the pathologist's stamp (name, qualification, registration number). The `reports` row records generation, dispatch and receipt.
- **Dispatch is a separate step** by the lab technician: **email** (a notice that says a report is ready and points to the patient's account — no clinical content in the email) or **download link** (available in the account). **SMS stays unavailable** until a provider is chosen (ADR-007). A patient can open a report only after it is dispatched; **the first open records receipt**, set with a single conditional update so two devices opening it together cannot conflict.
- **Who sees what:** the lab and pathologists see results and history; doctors see verified reports for tests they ordered or patients they treat (logged as `LAB_REPORT`); patients only dispatched reports of their own; the front desk and admin see none.
- **Patient tracker:** a verified sample stays at "Verified" until its report is dispatched, then reads "Report ready".
**Alternatives considered:** one value and range per result row (can't represent multi-parameter tests); letting the technician verify or enabling a "release" flag (the hard gate must not be a setting); overwriting a result on retest (destroys the audit trail); attaching the PDF to the email (clinical data in an inbox); storing generated PDFs (extra storage and an access surface — ADR-021).
**Consequence:** Phase 09 measures turnaround from the sample events. The pathologist's signature image on the stamp is deferred (the stamp uses the printed name and registration number). Critical results are surfaced by ordering (top of the queue, red banner) rather than a separate alert.

---

## ADR-026: Inventory and admin insights — levels change only through recorded movements; analytics are computed from existing records

**Status:** Accepted
**Context:** Phase 09 adds the lab's consumables and the admin's picture of the clinic. Stock levels are shared by several people at the bench, so two people adjusting at once must not lose a change or drive a level below zero. The analytics should not need a second copy of data that has to be kept in sync.
**Decision:**
- **Inventory items** have a category (tube, reagent, consumable, other), a unit, a level and a **low-stock threshold**. An item is **low** when its level is *below* its threshold and it is in use; a threshold of 0 means the item is not watched; **out of stock** means level 0 with a threshold above 0. Names are unique ignoring case. Items are retired (`is_active = false`), never deleted.
- **A level changes only through a movement.** `PATCH /inventory/{id}/stock` takes a signed change and a reason (`RESTOCK`, `USED`, `WASTAGE`, `CORRECTION`; `OPENING` is written when an item is created with stock). The direction must match the reason (a restock adds; use and wastage take away; a correction can go either way). The change is **one conditional UPDATE** (`current_stock + :delta >= 0`, item active, `RETURNING`), so concurrent adjustments neither lose an update nor go below zero — the loser gets `409 INSUFFICIENT_STOCK`. The **movement** (change, level after, reason, note, who) is append-only. The database also checks that a movement's direction matches its reason and that the level is never negative.
- **Who:** lab technicians and admins record movements and read stock; only admins add, edit or retire items. Nobody else sees inventory.
- **Low-stock warning** is computed, not stored: the dashboard card "Running low" (lab and admin, only when something is low), a count on the bell, and a filter and badges on the inventory screen. The emptiest items (level as a share of threshold) come first.
- **Analytics are computed from the records the earlier phases already write**: revenue from `payments` (money received, not billed), patients seen from completed consultations, registrations from `patients`, reports from `reports`, most-ordered tests from live order lines, staff activity from the actor recorded on each row.
- **Turnaround** is measured per sample from `sample_status_events`: collected → received at lab → result entered → verified → report generated. A sample counts towards **every test on it** (a tube carries several). The figure is the time from collection to report ready; the stages (to the lab, testing, verification) are averaged separately. For each test we report count, average, **median**, **90th percentile** and how many samples were retested. The KPI tile uses the median across all reports because a few slow samples would distort an average. Retested samples measure to the final report, so the retest wait shows as testing time.
- **Periods** are the last N clinic days including today (7, 30 or 90 in the UI; 1–180 in the API), grouped in the clinic's time zone, with zero-filled days so charts have no gaps. The previous period of equal length gives the change shown on each tile; a change against zero is not shown.
- Analytics endpoints are **admin only** and return aggregates plus staff names and counts; they never return patient details.
**Alternatives considered:** editing the level directly (loses who and why, and races); a stored "low" flag or scheduled job (can be stale; the comparison is cheap); a separate analytics table or nightly rollup (one more thing to keep correct at this scale); averaging turnaround (skewed by outliers); reagent-to-test auto-decrement (a stretch feature — see OpenQuestions).
**Consequence:** Staff account management (`POST /admin/staff`) is not part of this phase and stays off the admin menu until it is built. Reagent-to-test mapping, if built later, would write `USED` movements.
