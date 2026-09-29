# Phase 07 — Sample Lifecycle: Collection Through Rejection

**Depends on:** Phase 05 (Lab Test Catalog & Ordering) — needs a real `LabOrderItem` to attach samples to
**Feeds into:** Phase 08 (Result Entry, Verification & Reporting)
**Status:** Done (ADR-024)

This phase is the start of the project's differentiator feature — replaces any placeholder status field with the real state machine.

## Goal

Model a sample's journey from order through collection and the lab's quality check, including the rejection-and-redraw path, with a full chain-of-custody log.

## Scope

- [x] `Sample` model + `sample_code` generation (`LAB-YYYYMMDD-####`, see `Schema.md` §3) — one sample per physical tube
- [x] QR code rendering for the sample code
- [x] `SampleStatusEvent` append-only log (staff ID + timestamp per transition — never update/delete)
- [x] State machine enforcement: `ORDERED → COLLECTED → RECEIVED_AT_LAB → ...`, no skipping, no going backward except via rejection
- [x] Collection screen (Lab Technician): tube type, body site, timestamp
- [x] Tube-type mismatch flag against `LabTest.required_tube_type` (see `Rules.md`)
- [x] Receipt/quality-check screen: accept → `RECEIVED_AT_LAB`, or reject
- [x] Rejection flow: `RejectionRecord` (hemolyzed / clotted / insufficient volume / other), automatic front-desk notification, sample kept as permanent record. Build it with `rejected_at_stage` from the start — Phase 08 adds rejection during testing on top of it
- [x] Redraw path: new `Sample` row linked to the same `LabOrderItem`, rejected sample untouched

## Exit Criteria

A sample can be walked from order through collection to a lab receipt check; a deliberately "bad" sample can be rejected, notifies the front desk, and is preserved — and a redraw creates a clean new sample instance without losing the rejection history.

## Related Docs

- `Docs/PRD.md` §4, §6 — why this feature matters for evaluation
- `Docs/Appflow.md` §3 — full state machine diagram
- `Docs/Rules.md` §2 — rejection criteria, tube-type rule, chain-of-custody rule
- `Docs/Schema.md` §3 — `Sample`, `SampleStatusEvent`, `RejectionRecord`
