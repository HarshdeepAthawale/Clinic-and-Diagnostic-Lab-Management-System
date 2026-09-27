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
| P5-1 | `LabTest` catalog model + seed tooling | Not Started | |
| P5-2 | `LabOrder` / `LabOrderItem` models | Not Started | |
| P5-3 | "Order test" from consultation (auto-create) | Not Started | Key clinic↔lab integration point |
| P5-4 | Prep instructions surfaced to patient | Not Started | |

## Phase 06 — Billing & Invoicing

| ID | Task | Status | Notes |
|---|---|---|---|
| P6-1 | `Invoice` model + auto-generation | Not Started | |
| P6-2 | Payment status tracking | Not Started | |
| P6-3 | Discount with logged staff ID | Not Started | |
| P6-4 | Receptionist counter billing screen | Not Started | |
| P6-5 | Patient-facing invoice view | Not Started | |

## Phase 07 — Sample Lifecycle: Collection Through Rejection

| ID | Task | Status | Notes |
|---|---|---|---|
| P7-1 | `Sample` model + sample code generation | Not Started | |
| P7-2 | QR code rendering | Not Started | |
| P7-3 | `SampleStatusEvent` append-only log | Not Started | |
| P7-4 | State machine transition enforcement | Not Started | |
| P7-5 | Collection screen (tube type, body site) | Not Started | |
| P7-6 | Tube-type mismatch flag | Not Started | |
| P7-7 | Receipt/quality-check screen | Not Started | |
| P7-8 | Rejection flow + front-desk notification | Not Started | |
| P7-9 | Redraw path (new Sample row) | Not Started | |

## Phase 08 — Result Entry, Verification & Report Generation

| ID | Task | Status | Notes |
|---|---|---|---|
| P8-1 | Result entry screen | Not Started | |
| P8-2 | Reference-range auto-check | Not Started | |
| P8-3 | Pathologist verification gate + queue/review screens | Not Started | Separate `PATHOLOGIST` role (ADR-011) |
| P8-3b | Return for retest (reason, result history, technician queue flag) | Not Started | ADR-012 |
| P8-3c | Reject during testing (exhausted/degraded) → redraw | Not Started | ADR-013; reuses P7-8/P7-9 |
| P8-4 | Report PDF generation (letterhead + stamp) | Not Started | |
| P8-5 | Dispatch (channel + receipt confirmation) | Not Started | |
| P8-6 | Patient-facing visual sample tracker | Not Started | |

## Phase 09 — Inventory & Admin Analytics

| ID | Task | Status | Notes |
|---|---|---|---|
| P9-1 | `InventoryItem` model | Not Started | |
| P9-2 | Inventory management screen + low-stock warning | Not Started | |
| P9-3 | Admin dashboard (counts, revenue, most-ordered tests, staff performance) | Not Started | Analytics endpoints + Mantine Charts |
| P9-4 | TAT analytics by test type | Not Started | |

## Phase 10 — Stretch Features & Demo Readiness

| ID | Task | Status | Notes |
|---|---|---|---|
| P10-1 | Select 2–3 stretch features | Not Started | See [[OpenQuestions]] |
| P10-2 | Build selected stretch features | Not Started | |
| P10-3 | End-to-end walkthrough rehearsal | Not Started | |
| P10-4 | Seed/demo data | Not Started | |
| P10-5 | UI polish pass | Not Started | |
| P10-6 | Security/test coverage review | Not Started | |
