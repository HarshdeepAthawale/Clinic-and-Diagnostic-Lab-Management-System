# Phase 09 — Inventory & Admin Analytics

**Depends on:** Phase 08 (Result Entry, Verification & Reporting) — TAT analytics require completed sample lifecycles to measure against
**Feeds into:** Phase 10 (Stretch Features & Demo Readiness)

## Goal

Give the Admin role its dashboard and give the lab a way to track consumables — both are "free" analytics on top of data already being logged by earlier phases.

## Scope

- [x] `InventoryItem` model: stock level, low-stock threshold, and append-only movements (see `Schema.md` §5, ADR-026)
- [x] Inventory management screen (Lab Technician, Admin): view, restock, use, wastage, correction; admins add, edit and retire
- [x] Low-stock warning: computed, shown as a dashboard card, a bell count and list filters
- [x] Admin dashboard: daily patient count, revenue, most-ordered tests, staff performance
- [x] TAT (turnaround time) analytics by test type, computed from `SampleStatusEvent` timestamps (see `Appflow.md` §3)
- [x] Admin analytics endpoints returning chart-ready JSON (see `API.md`)
- [x] Charts (Mantine Charts / Recharts) for the above — see `Design.md` on avoiding bare/templated screens
- [ ] Staff account management — not part of this phase (see `OpenQuestions.md`)

## Exit Criteria

An admin can open the dashboard and see real numbers (not placeholders) derived from data produced in earlier phases, including a TAT breakdown per test type; a lab technician sees a low-stock warning fire correctly.

## Related Docs

- `Docs/Schema.md` §5 — `InventoryItem`
- `Docs/PRD.md` §6 — TAT analytics as a "free" byproduct of the sample lifecycle logging
- `Docs/Design.md` — Admin dashboard screen inventory
- `Docs/Rules.md` §4 — inventory rules
