# Business & Domain Rules

Rules the system must enforce regardless of UI. If code and this doc disagree, treat it as a bug in one of the two — reconcile, don't silently pick one.

## 1. Access Control (RBAC)

- A **Patient** may only view/modify their own records, appointments, samples, reports, and invoices. Never another patient's.
- A **Doctor** can search all patients and see their **basic details** (name, patient ID, age, gender, masked phone) — enough to identify a walk-in. The **full medical record** (allergies, history, consultations, prescriptions, lab reports) opens only for patients the doctor has a **care relationship** with:
  - a non-cancelled appointment or walk-in token with that doctor (past, today or upcoming), or
  - a past consultation by that doctor.

  Ordering tests and writing prescriptions follow the same rule. There is no self-service override: to treat a new patient, the front desk (or the patient) books an appointment or issues a token with that doctor, which creates the relationship.
- Every time a Doctor or Pathologist opens a patient's full record, it is logged (who, which patient, what, when) in an append-only access log that Admin can review.
- A **Receptionist** can register patients, manage queue/appointments, and handle billing, but cannot view lab results or write prescriptions.
- A **Lab Technician** can manage samples/results but cannot access billing or prescriptions.
- Only a **Pathologist** (a separate role — not a Doctor permission) can verify results and release a report. A pathologist cannot verify a result they entered themselves, and has no access to billing, prescriptions, appointments or patient registration.
- **Admin** has full read access for analytics and staff/inventory management, but is not a substitute for clinical roles (should not write prescriptions or verify reports).
- Each account has exactly one role. A person who works as both a doctor and a pathologist gets two accounts.

Full permission enforcement details belong in [[Security]].

## 2. Sample Lifecycle Rules

- A sample's state must always move forward through the pipeline: `Ordered → Collected → Received at Lab → In Testing → Result Entered → Verified by Pathologist → Report Generated → Dispatched`. No skipping stages, no backward transitions except the two explicit paths below: **rejection** (new sample) and **return for retest** (same sample).
- Every state transition must record: timestamp, responsible staff member ID, and (where applicable) the reason/detail for that stage.
- **Rejection rule:** a sample can be rejected at two points, each with its own allowed reasons:
  - At "Received at Lab" (quality check): hemolyzed, clotted, insufficient volume, or other.
  - During "In Testing": sample exhausted (not enough left to run or rerun the test — typically after a return for retest) or sample degraded (too old/unstable to give a reliable result), or other. Only a Lab Technician can do this.

  A rejected sample, at either point:
  - Is never deleted — kept as a permanent record.
  - Triggers an automatic notification to the front desk to call the patient in for a redraw.
  - Records the rejection reason, the stage it was rejected at, and the technician who flagged it.
  - Keeps any results already entered for it (including ones returned for retest) as permanent history. The redraw is a new sample whose results start again at attempt 1.
- **Return-for-retest rule:** at "Result Entered," a pathologist may return the result for retesting instead of verifying it. This:
  - Moves the sample back to `In Testing` — the only allowed backward transition. The same physical sample is retested; no redraw.
  - Requires a reason from a fixed list (implausible value, inconsistent with patient history, critical value needs confirmation, QC concern, other) plus a note when the reason is "other".
  - Keeps the original result as a permanent record marked `RETURNED_FOR_RETEST` — it is never overwritten or deleted. The retest produces a new result row, which goes back to the verification queue.
  - Puts the sample at the top of the lab technician's queue, flagged with the pathologist's reason.
  - Has no limit on repeats, but the retest count is shown to the pathologist and technician.
- **Tube type rule:** each test type has a required tube type; sample collection must record which tube was used, and a mismatch should be flagged (not silently accepted).
- **Verification gate:** a report can never be generated or released before a pathologist has digitally signed off on the result. This is a hard gate, not a UI suggestion.
- **Chain of custody:** every stage's timestamp + staff ID combination is immutable once written (append-only log), forming the audit trail.

## 3. Billing Rules

- One invoice combines consultation fee + all ordered test charges for a visit.
- Invoices track paid/unpaid/partially-paid status.
- Discounts must be logged with who applied them (ties into audit requirements).

## 4. Inventory Rules

- Low-stock warning triggers when a consumable (reagent, tube, etc.) drops below its configured threshold.
- If reagent-to-test mapping (stretch feature) is implemented, completing a test automatically decrements the linked reagent's stock — never manual-only in that case.

## 5. Reporting & Audit Rules

- Any edit to a patient's medical record or a report must be logged with who made the change and when (required for accreditation-style audit trails, e.g. NABL-style expectations referenced in [[PRD]]).
- Report PDFs must carry the lab's letterhead and a digital verification stamp tied to the verifying pathologist.
- Unique sample IDs follow the format `LAB-YYYYMMDD-####` (date + daily sequence number) and must be unique system-wide, not just per day per device.

## 6. Consent (if Digital Consent Tracking stretch feature is built)

- Sensitive tests (e.g., genetic testing) require explicit patient digital consent before the order can proceed to collection. Consent event must be logged (who, when, what was consented to).
