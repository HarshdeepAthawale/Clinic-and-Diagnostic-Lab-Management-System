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

## 1a. Appointments & Queue

- Bookings go into a doctor's **working hours** only: on a slot boundary, in the future, at most 60 days ahead. A slot holds one live booking; cancelling frees it. A patient can hold one live booking per doctor per day.
- **Tokens** (`T-001`…) restart every clinic day and are given at check-in; a walk-in gets one immediately. The queue is served in check-in order.
- Status moves and who may make them:

  | From | To | Who | Extra condition |
  |---|---|---|---|
  | Booked | Checked in | Receptionist | Only on the day of the appointment |
  | Booked | Cancelled | Receptionist, the patient (own) | Patient: only before the start time |
  | Booked | No-show | Receptionist | Only after the start time |
  | Checked in | In consultation | Doctor (own) | The doctor has nobody else in consultation |
  | Checked in | No-show | Receptionist, doctor (own) | Left without being seen |
  | Checked in | Cancelled | Receptionist | |
  | In consultation | Completed | Doctor (own) | |

  Completed, No-show and Cancelled are final. Every move is recorded (who, from, to, when, note) and never edited.
- Doctors see only their own appointments and queue. Front desk and admin see all; admin only watches.
- One reminder email per booked appointment, sent once it is within 24 hours — only to patients with a login.

## 1b. Consultations & Prescriptions

- A consultation belongs to exactly one appointment and is written by that appointment's doctor. Starting it calls a checked-in patient in.
- While the patient is in the room it is a **draft** the doctor can keep changing (autosaved). **Finishing** needs a diagnosis; it issues the prescription and completes the visit together.
- After finishing, the consultation and its prescription are **read-only** — enforced by the database. Prescriptions are numbered `RX-000001`, `RX-000002`, … in issue order; a visit with no medicines gets no prescription.
- A medicine line needs the medicine, how often and for how long; dose and instructions are optional. At most 30 lines.
- **Who can read:** the doctor who wrote it; other doctors only once it's finished and only for patients they are treating (logged); the patient their own finished consultations, without the doctor's clinical notes. The front desk and admin can't read consultations or prescriptions.

## 2a. Lab Test Catalog & Orders

- Only **active** catalog tests can be ordered. Admins add and edit tests and retire them; tests are never deleted, and retiring one never changes existing orders.
- Each test has one **required tube or container** and may have **patient preparation** (fasting, first-morning urine…). Its parameters carry the normal range and critical limits results are checked against.
- A doctor orders **from their own consultation while it is open** — the patient comes from the consultation, nothing is typed again. A consultation has **at most one open order**; ordering again adds to it, and the same test can't be on it twice.
- Outside a visit, a doctor can order **directly** for a patient they are treating (care relationship).
- Each ordered test keeps the **name and price it was ordered at**.
- Orders are `ROUTINE` or `URGENT`; the lab sees urgent orders first, then oldest first. Orders are numbered `LO-000001`, `LO-000002`, …
- The ordering doctor can remove a test or cancel the order while it is open; removing the last test cancels it.
- **Who can read:** the patient their own orders and prep, without the doctor's note for the lab; doctors orders they placed or for patients they are treating (logged); lab technicians and pathologists all orders. The front desk can't read orders yet (Phase 06 adds billing).

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
  - Puts the sample at the top of the lab technician's queue, flagged with the pathologist's reason. The retest is entered as the next attempt and goes back to the verification queue.
  - Has no limit on repeats, but the retest count is shown to the pathologist and technician.
- **Samples are per tube:** tests of one order that need the same tube share one sample; the system creates the samples when the tests are ordered and gives each a code like `LAB-20260929-0007`. Tests whose sample hasn't been drawn can still come off the order; once it has, they can't (the order can't be cancelled either).
- **Collection** records the tube used, where the blood was drawn from (blood only) and who/when.
- **Tube type rule:** each test type has a required tube type; sample collection must record which tube was used, and a mismatch is flagged, not silently accepted — the technician must confirm it, and it is marked on the sample, in the custody log and at the receipt check.
- **Rejection creates the redraw:** a rejected sample is kept and marked rejected; the front desk gets a notification with the patient's phone number, and a new sample for the same tests is created straight away for the redraw. The patient is told only that a new sample is needed — not the reason.
- **The database holds the line:** samples can't be deleted, can't skip stages or go backwards except as described, and the custody log and rejection records can't be changed.
- **Results:** testing starts on an accepted sample. The technician enters a value for **every** parameter of every test on the sample; the server flags each number against its normal range and critical limits (critical wins and includes the limit itself). Text results (Positive, blood group) have no flag. Entering results sends the sample to the pathologist; the technician can't verify.
- **Verification gate:** a report can never be generated or released before a pathologist has digitally signed off on the result. This is a hard gate, not a UI suggestion — the database refuses to create a report without a verified result. **Whoever entered a result can't verify it.**
- **Reports and dispatch:** verifying creates the report at once (with the pathologist's name, qualification and registration number). The lab technician then **dispatches** it — by email or a download link (SMS isn't available yet). A patient can open a report only after it is dispatched; the first time they do is recorded as receipt. Doctors can see verified reports for their patients straight away. A report is never edited.
- **Rejection during testing** (used up or degraded, by the lab technician) works like a receipt rejection: permanent record, call-back to the front desk, redraw — and any results already entered stay on record.
- **Chain of custody:** every stage's timestamp + staff ID combination is immutable once written (append-only log), forming the audit trail.

## 3. Billing Rules

- **One invoice per visit**, created by the system when the consultation is finished: the doctor's consultation fee plus every test ordered in that consultation. A lab order placed outside a visit gets its own invoice at once. Invoices are numbered `INV-000001`, `INV-000002`, …
- Each test is billed at the price it was ordered at; later catalog changes never change an invoice.
- A test removed from an order comes off its invoice. A test the patient **has already paid for can't be removed** until the front desk settles it. An invoice with nothing left to bill is void.
- Payments are recorded at the counter by the front desk — **cash, card or UPI**, part payments allowed, **never more than the balance**. An invoice is `UNPAID`, `PARTIALLY_PAID` or `PAID`. Payments can't be edited or deleted.
- **Discounts must be logged with who applied them, and why.** A reason is mandatory; the front desk may discount up to **20% of the bill**, an admin more; a discount can't be more than the bill or leave it below what was already paid. Every discount change is kept in the invoice's history.
- **Who can see billing:** the patient their own invoices; the front desk and admins all invoices. Doctors, the lab and pathologists see no billing.

## 4. Inventory Rules

- Low-stock warning triggers when a consumable (reagent, tube, etc.) drops **below** its configured threshold. A threshold of 0 means the item isn't watched; retired items never raise a warning.
- **A level changes only by recording a movement** — restock, used, wastage or correction, with who did it and an optional note. The direction must match the reason, and a level can never go below zero (two people adjusting at once can't break this). Movements are never edited or deleted.
- Lab technicians and admins record movements; **only admins add, edit or retire items**. Nobody else sees inventory.
- If reagent-to-test mapping (stretch feature) is implemented, completing a test automatically decrements the linked reagent's stock — never manual-only in that case.

## 5. Reporting & Audit Rules

- Any edit to a patient's medical record or a report must be logged with who made the change and when (required for accreditation-style audit trails, e.g. NABL-style expectations referenced in [[PRD]]).
- Report PDFs must carry the lab's letterhead and a digital verification stamp tied to the verifying pathologist, and a **QR code** that opens a public page confirming the clinic issued the report (report number, patient initials, tests, verifier — never results).
- **A critical result must be acknowledged.** When a verified report has a value at a critical limit, its ordering doctor sees it pinned on their dashboard and on the bell until they acknowledge it (with an optional note). The acknowledgement records who and when, happens once, and can't be edited. It never blocks sending the report to the patient. The lab sees every waiting one; the admin only how many and for how long.
- **Trends** use only verified results: a patient sees values from reports already sent to them, a doctor needs a care relationship (and the view is logged), a pathologist sees all; nobody else.
- **Repeated failed sign-ins are refused for a while** (5 per email from one address, 30 per address, in 15 minutes) — see [[Security]].
- Unique sample IDs follow the format `LAB-YYYYMMDD-####` (date + daily sequence number) and must be unique system-wide, not just per day per device.

## 6. Consent (if Digital Consent Tracking stretch feature is built)

- Sensitive tests (e.g., genetic testing) require explicit patient digital consent before the order can proceed to collection. Consent event must be logged (who, when, what was consented to).
