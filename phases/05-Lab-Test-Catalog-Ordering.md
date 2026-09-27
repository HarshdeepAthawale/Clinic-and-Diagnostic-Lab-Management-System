# Phase 05 — Lab Test Catalog & Ordering

**Depends on:** Phase 04 (Consultations & E-Prescriptions)
**Feeds into:** Phase 06 (Billing), Phase 07 (Sample Lifecycle) — this is the hinge between the clinic side and the lab side
**Status:** Done (ADR-022)

## Goal

This is the key clinic↔lab integration point described in the PRD: a doctor orders a test during a consultation and a lab order appears with zero re-typing.

## Scope

- [x] `LabTest` catalog: name, price, required tube type, reference ranges (per parameter), prep instructions (see `Schema.md` §3)
- [x] Admin/seed tooling to populate the catalog — 22 tests seeded by V5; admin Test catalog page
- [x] `LabOrder` + `LabOrderItem` models
- [x] "Order test" action from within a consultation (Doctor) — auto-creates `LabOrder`/`LabOrderItem`, no manual re-entry of patient info
- [x] Direct lab order creation path — from the patient's record, for patients under the doctor's care
- [x] Prep instructions surfaced to the patient (e.g., "fast 12 hours")

## Exit Criteria

A doctor, mid-consultation, selects one or more catalog tests; a `LabOrder` with the correct `LabOrderItem`s exists immediately, correctly linked to the patient and consultation, with prep instructions visible to the patient — no data re-entered by anyone.

## Related Docs

- `Docs/PRD.md` §2, §6 — the "no re-typing" integration this phase delivers
- `Docs/Schema.md` §3 — `LabTest`, `LabOrder`, `LabOrderItem`
- `Docs/Appflow.md` §1 — cross-cutting consultation → lab order flow
- `Docs/Rules.md` §2 — tube-type rule tied to the catalog
