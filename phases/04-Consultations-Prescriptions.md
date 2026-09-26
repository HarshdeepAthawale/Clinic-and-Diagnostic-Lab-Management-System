# Phase 04 — Consultations & E-Prescriptions

**Depends on:** Phase 03 (Appointments & Queue Management)
**Feeds into:** Phase 05 (Lab Test Catalog & Ordering) — a lab order is created from a consultation

## Goal

Turn a checked-in appointment into a real consultation record: notes, diagnosis, and a digital prescription. This is also the entry point for ordering lab tests (next phase), so the consultation record needs to exist and be linkable before that phase starts.

## Scope

- [ ] `Consultation` model, created from an appointment (see `Schema.md` §2)
- [ ] Doctor-facing consultation notes + diagnosis entry
- [ ] `Prescription` model: medicines (name/dosage/duration), linked to the consultation
- [ ] Prescription PDF export (iText7 or PDFBox — see `Decisions.md` ADR-006, currently open)
- [ ] Patient-facing prescription list + download

## Exit Criteria

A doctor can complete a consultation, write notes, and issue a prescription that the patient can immediately view and download as a PDF from their own login.

## Related Docs

- `Docs/Schema.md` §2 — `Consultation`, `Prescription`
- `Docs/Appflow.md` — Doctor flow
- `Docs/Decisions.md` ADR-006 — PDF library choice (resolve before this phase needs it)
