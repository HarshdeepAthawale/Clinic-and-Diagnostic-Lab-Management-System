# Task Tracker

Living status board, one section per file in `/phases`. Update status as work progresses; this file is the source of truth for "what's actually done" — [[Changelog]] records what shipped, this records what's in flight. Task text mirrors each phase file's scope checklist — if they drift, update both in the same change.

Status values: `Not Started`, `In Progress`, `Blocked`, `Done`.

## Phase 01 — Foundation & Access Control

| ID | Task | Status | Notes |
|---|---|---|---|
| P1-1 | Spring Boot + Maven scaffolding (`backend/`, `/api`, Swagger) | Done | Spring Boot 4.1, Java 21, Maven Wrapper |
| P1-1b | Next.js (JavaScript) scaffolding (`frontend/`, Mantine, TanStack Query, `/api` rewrite) | Done | Next 16, Mantine 9, design system per ADR-016 |
| P1-2 | Supabase Postgres project + Flyway config (`ddl-auto=validate`, dev seed profile) | In Progress | Flyway + local Docker Postgres done; Supabase project still to be created by the team |
| P1-3 | `V1__identity_and_roles.sql` (User, Patient, Doctor, Pathologist, Staff) + JPA entities | Done | Composite FKs keep profile role = account role |
| P1-4 | Spring Security + JWT auth end-to-end (httpOnly cookie, `/auth/me`, CSRF header filter) | Done | 33 backend tests |
| P1-5 | Login/register pages, `proxy.js`, per-role layouts (empty dashboards) | Done | 16 frontend tests; verified in browser |

## Phase 02 — Patient Registration & EMR

| ID | Task | Status | Notes |
|---|---|---|---|
| P2-1 | Patient registration form (Receptionist) | Done | Printable slip with one-time registration code (ADR-018) |
| P2-2 | Patient self-registration | Done | New sign-up, or link an existing record with the registration code |
| P2-3 | EMR fields (demographics, allergies, history) | Done | `V2__patient_records.sql`; blood group, emergency contact |
| P2-4 | EMR read views (Doctor full with care relationship, Patient self) | Done | ADR-015; locked view without a care relationship |
| P2-5 | Patient search (summary, masked phone) + `PatientAccessLog` | Done | Ctrl+K search; append-only log with DB trigger; admin log view |

## Phase 03 — Appointments & Queue Management

| ID | Task | Status | Notes |
|---|---|---|---|
| P3-1 | `Appointment` model + booking flow | Done | V3 migration; slots from working hours; DB-enforced no double booking (ADR-020) |
| P3-2 | Doctor calendar view | Done | Day agenda + week strip; working-hours editor |
| P3-3 | Walk-in queue token issuance | Done | Daily tokens `T-001…`; live queue board + waiting-room screen |
| P3-4 | Appointment status lifecycle | Done | Role table in Rules §1a; append-only history |
| P3-5 | Automatic reminder email | Done | 24 h ahead, once; Mailpit in dev |

## Phase 04 — Consultations & E-Prescriptions

| ID | Task | Status | Notes |
|---|---|---|---|
| P4-1 | `Consultation` model from appointment | Done | One per appointment; starting calls the patient in |
| P4-2 | Consultation notes + diagnosis entry | Done | Consult workspace with vitals, autosave and finish confirmation |
| P4-3 | `Prescription` model | Done | Structured medicine lines, RX numbers, formulary autocomplete |
| P4-4 | Prescription PDF export | Done | OpenHTMLtoPDF on PDFBox (ADR-021) |
| P4-5 | Patient-facing prescription list/download | Done | Prescriptions page, dashboard widget, visit history in My record |

## Phase 05 — Lab Test Catalog & Ordering

| ID | Task | Status | Notes |
|---|---|---|---|
| P5-1 | `LabTest` catalog model + seed tooling | Done | 22 seeded tests with per-parameter ranges; admin Test catalog (ADR-022) |
| P5-2 | `LabOrder` / `LabOrderItem` models | Done | LO numbers, priority, price snapshots, cancel/remove |
| P5-3 | "Order test" from consultation (auto-create) | Done | Order tests drawer with panels; direct orders from the record; lab queue |
| P5-4 | Prep instructions surfaced to patient | Done | Lab tests page and "Tests to get done" dashboard card |

## Phase 06 — Billing & Invoicing

| ID | Task | Status | Notes |
|---|---|---|---|
| P6-1 | `Invoice` model + auto-generation | Done | Created when a visit is finished or a direct lab order is placed (ADR-023) |
| P6-2 | Payment status tracking | Done | Append-only payments; part payments; UNPAID → PARTIALLY_PAID → PAID |
| P6-3 | Discount with logged staff ID | Done | Mandatory reason, who and when, front-desk cap, DB check |
| P6-4 | Receptionist counter billing screen | Done | Billing counter with take-payment and discount panels; dashboard widgets |
| P6-5 | Patient-facing invoice view | Done | Bills pages, PDF view/download, dashboard card |

## Phase 07 — Sample Lifecycle: Collection Through Rejection

| ID | Task | Status | Notes |
|---|---|---|---|
| P7-1 | `Sample` model + sample code generation | Done | One sample per tube; `LAB-YYYYMMDD-####` from a daily counter (ADR-024); V8 backfills existing orders |
| P7-2 | QR code rendering | Done | On the printable label, drawn as SVG |
| P7-3 | `SampleStatusEvent` append-only log | Done | Trigger-enforced; shown as the chain of custody |
| P7-4 | State machine transition enforcement | Done | In the database (trigger) and in Java |
| P7-5 | Collection screen (tube type, body site) | Done | Sample page with tube chips and body site |
| P7-6 | Tube-type mismatch flag | Done | Needs confirmation; flagged on the sample, log and receipt check |
| P7-7 | Receipt/quality-check screen | Done | Accept or reject with a reason |
| P7-8 | Rejection flow + front-desk notification | Done | Permanent record, notification with phone number, bell + dashboard + inbox |
| P7-9 | Redraw path (new Sample row) | Done | Created at rejection, linked back to the rejected sample |

## Phase 08 — Result Entry, Verification & Report Generation

| ID | Task | Status | Notes |
|---|---|---|---|
| P8-1 | Result entry screen | Done | Per-parameter entry with live flags, analyzer, retest context (ADR-025) |
| P8-2 | Reference-range auto-check | Done | Server-side flags, ranges copied onto each value; numeric or text parameters |
| P8-3 | Pathologist verification gate + queue/review screens | Done | Gate enforced by a DB trigger; queue, focus mode (V/R/J/K/Esc) and history |
| P8-3b | Return for retest (reason, result history, technician queue flag) | Done | ADR-012; attempts kept, retests pinned first |
| P8-3c | Reject during testing (exhausted/degraded) → redraw | Done | ADR-013; reuses P7-8/P7-9 |
| P8-4 | Report PDF generation (letterhead + stamp) | Done | Drawn on request; signature image deferred |
| P8-5 | Dispatch (channel + receipt confirmation) | Done | Email and download link; SMS waits on a provider (ADR-007) |
| P8-6 | Patient-facing visual sample tracker | Done | Journey line through to "Report ready"; Reports pages |

## Phase 09 — Inventory & Admin Analytics

| ID | Task | Status | Notes |
|---|---|---|---|
| P9-1 | `InventoryItem` model | Done | Items and append-only movements; levels change only through a movement (ADR-026) |
| P9-2 | Inventory management screen + low-stock warning | Done | List, side panel with adjust form and history, admin add/edit/retire; dashboard card and bell |
| P9-3 | Admin dashboard (counts, revenue, most-ordered tests, staff performance) | Done | `/admin/insights` with KPI tiles, area and bar charts, staff table, data-table toggles |
| P9-4 | TAT analytics by test type | Done | Median / p90 from the sample event log, stage breakdown, test × day heatmap |
| P9-5 | Staff account management | Not Started | Not part of Phase 09; decide in Phase 10 (see [[OpenQuestions]]) |

## Phase 10 — Stretch Features & Demo Readiness

| ID | Task | Status | Notes |
|---|---|---|---|
| P10-1 | Select 2–3 stretch features | Not Started | See [[OpenQuestions]] |
| P10-2 | Build selected stretch features | Not Started | |
| P10-3 | End-to-end walkthrough rehearsal | Not Started | |
| P10-4 | Seed/demo data | Not Started | |
| P10-5 | UI polish pass | Not Started | |
| P10-6 | Security/test coverage review | Not Started | |
