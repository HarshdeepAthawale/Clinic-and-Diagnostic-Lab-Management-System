# Phase 02 — Patient Registration & EMR

**Depends on:** Phase 01 (Foundation & Access Control)
**Feeds into:** Phase 03 (Appointments), Phase 04 (Consultations) — both need a real patient record to attach to

## Goal

Build the single shared patient record that both the clinic side and the lab side read/write — this is what eliminates the redundant-data-entry problem described in the PRD.

## Scope

- [x] Patient registration form (Receptionist-facing)
- [x] Patient self-registration (optional patient-facing signup, if in scope)
- [x] EMR fields: demographics, known allergies, history (see `Schema.md` §1 `Patient`)
- [x] Patient search (Doctor, Receptionist) returning summary fields only, phone masked
- [x] EMR read view for Doctor (full history, **care relationship required** — see `Rules.md` §1) and Patient (self, read-only)
- [x] `PatientAccessLog` written on every full-record read by a Doctor/Pathologist (see `Schema.md` §6)
- [x] RBAC enforcement: a patient can only ever see their own record (see `Rules.md` §1)

## Exit Criteria

A receptionist can register a new patient once; that same patient record is immediately visible to the patient's own login and searchable by doctors, with no re-entry anywhere else in the system. A doctor without a care relationship sees only basic details; the care-relationship check itself is completed in Phase 03 once appointments exist (until then, test it with seeded appointments).

## Related Docs

- `Docs/PRD.md` §1 — the redundant-data-entry problem this phase solves
- `Docs/Schema.md` §1 — `Patient` table
- `Docs/Rules.md` §1 — RBAC rules for patient data
- `Docs/Appflow.md` — Patient and Receptionist flows
