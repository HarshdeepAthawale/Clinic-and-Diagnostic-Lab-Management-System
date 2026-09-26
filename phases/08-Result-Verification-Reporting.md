# Phase 08 — Result Entry, Verification & Report Generation

**Depends on:** Phase 07 (Sample Lifecycle: Collection Through Rejection)
**Feeds into:** Phase 09 (Inventory & Admin Analytics) — TAT analytics need completed samples to measure

## Goal

Complete the sample state machine through result entry, the hard pathologist-verification gate, and PDF report generation/dispatch — including the patient-facing tracker UI.

## Scope

- [ ] Result entry screen (Lab Technician): value, analyzer/machine used
- [ ] Auto reference-range check against `LabTest.reference_range_*`
- [ ] `TestResult` model (see `Schema.md` §3)
- [ ] Pathologist verification action — **hard server-side gate**: no report without `verified_at` set (see `Rules.md` §2)
- [ ] Pathologist verification queue + review screen (see `Design.md` Pathologist); verify endpoint restricted to the `PATHOLOGIST` role, and the verifier must not be the user who entered the result
- [ ] Return for retest (Pathologist): reason + note, `RESULT_ENTERED → IN_TESTING`, old result kept as `RETURNED_FOR_RETEST`, new result row with `attempt_number + 1` (see `Rules.md` §2, ADR-012)
- [ ] Lab technician queue shows returned samples at the top with the reason; result history shows all attempts
- [ ] Reject during testing (Lab Technician): `IN_TESTING → REJECTED` with `SAMPLE_EXHAUSTED` / `SAMPLE_DEGRADED` / `OTHER`, reusing Phase 07's `RejectionRecord`, front-desk notification and redraw path (ADR-013)
- [ ] `Report` PDF generation: letterhead + digital verification stamp (pathologist name, qualification, registration number, signature)
- [ ] Dispatch: channel selection (email/SMS/download link) + delivery, receipt confirmation tracking
- [ ] Patient-facing visual sample tracker (Amazon-order-style, per `Design.md` §3)

## Exit Criteria

A verified result produces a report PDF that cannot be generated before verification exists; the patient sees their sample progress through every stage on a visual tracker and can download the final report once dispatched.

## Related Docs

- `Docs/Rules.md` §2 — verification gate as a hard rule, not a UI suggestion
- `Docs/Schema.md` §3 — `TestResult`, `Report`
- `Docs/Design.md` §3 — sample tracker UI detail
- `Docs/Decisions.md` ADR-006 — PDF library choice
