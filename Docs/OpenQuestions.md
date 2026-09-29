# Open Questions

Unresolved design/product questions. Tracked here rather than dropped — resolve into [[Decisions]] (or [[PRD]] / [[Rules]] as appropriate) when answered, and remove from this list at that point.

## Tech / Architecture

- **SMS provider:** "Twilio or similar" — no specific provider chosen (see [[Decisions]] ADR-007). Also unclear if SMS is in scope for the initial build at all, or email-only.
- **Deployment target:** not yet decided — now two deployables (Next.js frontend + Spring Boot backend). See [[Deployment]].

## Product / Domain

- **Which 2–3 stretch features** (from [[PRD]] section 7) will actually be built? Needs a decision once core + differentiator modules are stable — feeds [[ImplementationPlan]].
- **Payment handling:** billing is "record payment" by staff at the counter (cash, card or UPI) — built in Phase 06 (ADR-023). Whether a real online payment gateway is ever needed is still open; it stays out of scope (see [[NonGoals]]).
- **QR code scope:** is the sample QR code purely visual (printed on a demo label, not scanned), or does the demo need actual scan-to-lookup capability?

## Ops

- **Environments:** how many environments are actually needed (local, staging, prod) given this is likely a course/evaluation project rather than a production deployment? Affects [[Deployment]] and [[Setup]].
- **Hosting for the app itself** (beyond Supabase for the DB) — not yet chosen.
