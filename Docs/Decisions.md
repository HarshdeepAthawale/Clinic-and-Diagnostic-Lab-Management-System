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
