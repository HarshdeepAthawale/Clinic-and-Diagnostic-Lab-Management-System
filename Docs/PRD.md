# Product Requirements Document — Clinic and Diagnostic Lab Management System

## 1. Problem

Most small-to-mid-size clinics and diagnostic labs run on a patchwork of disconnected tools: a paper register or spreadsheet for appointments, a separate process for patient history, manual tracking for lab samples, and a third (or no) system for billing. None of these talk to each other.

This creates recurring, everyday problems:

- **Redundant data entry** — receptionist, doctor, and lab tech each re-type the same patient info.
- **Lost or delayed reports** — no one can tell a patient where their sample is in the process; staff fall back to phone calls or manual logbook checks.
- **No visibility into sample status** — a sample can sit untested for hours before anyone notices.
- **No audit trail** — a wrong report has no record of who entered what and when, which is a real problem for lab accreditation (e.g., NABL in India).
- **Poor patient experience** — patients can't self-serve: no tracking, no visit history, reports only by phone or physical pickup.

The clinic side (doctor visits) and the lab side (diagnostics) are two workflows that constantly need to exchange information but usually have no shared system to do it in.

## 2. Solution

A single web application combining:

1. A **clinic management system** — appointments, doctor visits, patient records, prescriptions.
2. A **diagnostic lab management system** — sample collection, testing, report generation.

Both sides share the same patient database. When a doctor orders a blood test during a consultation, it automatically creates a lab order — no re-typing.

## 3. User Roles

| Role | What they do in the system |
|---|---|
| **Patient** | Book appointments, view medical history, download prescriptions, track lab sample status live, view/download reports, pay bills |
| **Doctor** | See appointment schedule, write consultation notes, prescribe medicine, order lab tests directly from the patient's record |
| **Receptionist / Front Desk** | Register new patients, manage appointment queue, handle counter billing |
| **Lab Technician** | Receive test orders, log sample collection, enter test results, flag rejected/bad samples |
| **Pathologist** | Review entered lab results and digitally sign off (verify) before a report can be released |
| **Admin** | Manage staff accounts, view business analytics (revenue, patient counts, most-ordered tests), manage inventory |

Role-based access is enforced throughout — see [[Rules]] and [[Security]].

## 4. Differentiator: Blood Sample Lifecycle Tracking

Instead of a lab sample being a row that just says "pending" or "done," the system models its full real-world journey through a state machine:

```
Ordered → Collected → Received at Lab → In Testing → Result Entered → Verified by Pathologist → Report Generated → Dispatched to Patient
```

Every stage is timestamped and tied to the responsible staff member, giving a full chain-of-custody audit trail for free. Patients see this as a visual, Amazon-style order tracker. Full flow detail lives in [[Appflow]]; data model in [[Schema]]; business rules (rejection criteria, verification gate) in [[Rules]].

This feature demonstrates database design, workflow/state management, and real domain knowledge simultaneously, which is why it's the headline feature for evaluation/demo purposes.

## 5. Core Modules (4.1 — required for a complete, demoable system)

- **Appointment & Queue Management** — online booking, doctor calendar view, token/queue numbers for walk-ins, automatic reminders.
- **Electronic Medical Records (EMR)** — digital history per patient: past visits, diagnoses, known allergies.
- **Lab Test Management** — test catalog with pricing, order-to-result tracking, automatic reference-range checking.
- **E-Prescriptions** — digital prescription linked to the visit, downloadable as PDF.
- **Billing & Invoicing** — combined consultation + test charges into one invoice, payment status, discounts.
- **Report Generation** — auto-generated PDF report with letterhead and digital verification stamp once results are verified.
- **Inventory Management** — tracks lab consumables (reagents, tubes, etc.), low-stock warnings.
- **Admin Dashboard** — daily patient count, revenue, most-ordered tests, staff performance.

## 6. Differentiator Feature Detail (4.2)

See [[Appflow]] for the full stage-by-stage flow and [[Schema]] for the data captured at each stage. Summary of what's recorded:

- **Ordered:** tests requested, ordering doctor, prep instructions (e.g., fasting).
- **Collected:** phlebotomist, timestamp, tube type, body site.
- **Received at lab:** condition check — rejected if hemolyzed, clotted, or insufficient volume.
- **In testing:** can also be rejected if the sample runs out (e.g. after a retest) or degrades — same redraw flow.
- **Testing:** analyzer/machine used, technician.
- **Verification:** pathologist digital sign-off required before report release.
- **Dispatch:** delivery channel (email/SMS/download link) and confirmation of receipt.

Additional derived capabilities: unique sample IDs (`LAB-YYYYMMDD-####`) with QR code, sample rejection flow with permanent record + front-desk notification, and Turnaround Time (TAT) analytics per test type.

## 7. Stretch Goals (4.3 — nice-to-have, time permitting)

- No-show prediction
- Critical value alerts
- Test panel bundling (e.g., "Full Body Checkup")
- Trend graphs across visits (e.g., cholesterol over time)
- Family account linking
- QR-verified reports (authenticity check)
- Prescription templates
- Reagent-to-test inventory mapping (auto stock decrement)
- Full audit log on record edits
- Digital consent tracking for sensitive tests

**Built in Phase 10:** critical value alerts, trend graphs across visits, and QR-verified reports (ADR-027, ADR-028, ADR-029). The rest stay out of scope for this iteration.

Items explicitly excluded from scope entirely are tracked in [[NonGoals]].

## 8. Priority / Roadmap

1. Build all of section 6 (core modules) — this alone is a complete, demoable system.
2. Add Blood Sample Lifecycle Tracking (differentiator).
3. Pick 2–3 items from stretch goals based on remaining time.

Detailed phased breakdown: [[ImplementationPlan]]. Live task status: [[Tracker]].

## 9. Success Criteria

- A doctor can order a test during a consultation and it appears as a lab order with zero re-entry.
- A patient can track a single sample through every real-world stage with timestamps and responsible staff visible.
- A report cannot be released without pathologist verification.
- Admin dashboard reflects real TAT and revenue numbers derived from logged data, not manual entry.
