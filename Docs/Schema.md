# Data Schema

Initial draft schema derived from the domain described in [[PRD]] and [[Appflow]]. This is the starting point for the Flyway SQL migrations and matching JPA entities (see ADR-010 in [[Decisions]]) — expect it to evolve once implementation starts; keep this file in sync with actual migrations (see [[Contributing]] PR checklist).

## 1. Identity & Roles

### `User`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| email | string, unique | login identifier |
| password_hash | string | |
| role | enum | `PATIENT`, `DOCTOR`, `PATHOLOGIST`, `RECEPTIONIST`, `LAB_TECHNICIAN`, `ADMIN` — one role per account (see [[Decisions]] ADR-011) |
| created_at | timestamp | |
| is_active | boolean | for admin-driven deactivation |

### `Patient`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_code | string, unique | human-readable ID `PID-000123`, from a sequence (V2) |
| user_id | UUID (FK → User), nullable | null until the patient links a login (front desk registers walk-ins first) |
| full_name | string | |
| dob | date | |
| gender | string | |
| phone | string | |
| phone_digits | string, generated | digits of `phone`, indexed for search (V2) |
| address | string | |
| emergency_contact_name | string, nullable | (V2) |
| emergency_contact_phone | string, nullable | (V2) |
| blood_group | string, nullable | `A+` … `O-` (V2) |
| known_allergies | text | |
| medical_history | text, nullable | (V2) |
| claim_code_hash | string(64), unique, nullable | SHA-256 of the one-time registration code; cleared once used (ADR-018, V2) |
| claim_code_expires_at | timestamp, nullable | 30 days after issue (V2) |
| registered_by_user_id | UUID (FK → User), nullable | front-desk user who registered the record (V2) |
| created_at | timestamp | |
| updated_at | timestamp | (V2) |

### `Doctor`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User) | |
| full_name | string | |
| specialization | string | |
| qualification | string, nullable | printed on prescriptions (V4) |
| registration_number | string, unique, nullable | medical council number, printed on prescriptions (V4) |

### `Pathologist`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User), unique | user must have role `PATHOLOGIST` |
| full_name | string | printed on the report's verification stamp |
| qualification | string | e.g. MD Pathology — printed on the report |
| registration_number | string | medical council registration — printed on the report |
| signature_image_url | string, nullable | digital signature image for the report stamp |

### `Staff`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User) | |
| full_name | string | |
| staff_type | enum | `RECEPTIONIST`, `LAB_TECHNICIAN`, `ADMIN` |

## 2. Clinic Side

### `Appointment`
Created in V2 (Phase 02) because the doctor care-relationship check depends on it (ADR-015, ADR-018); extended in V3 (Phase 03, ADR-020).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_id | UUID (FK → Patient) | |
| doctor_id | UUID (FK → Doctor) | |
| kind | enum | `SCHEDULED`, `WALK_IN` |
| scheduled_at | timestamp | slot start; for walk-ins, when the token was issued |
| duration_minutes | smallint | slot length at booking time |
| reason | string, nullable | |
| status | enum | `BOOKED`, `CHECKED_IN`, `IN_CONSULTATION`, `COMPLETED`, `NO_SHOW`, `CANCELLED` ([[Rules]] §1a) |
| queue_date / queue_number / queue_token | date / int / string, nullable | set together at check-in; `T-007`; unique per `queue_date` |
| booked_by_user_id | UUID (FK → User), nullable | who booked or issued the token |
| checked_in_at / started_at / completed_at / cancelled_at | timestamp, nullable | stamped by the matching status change |
| cancellation_reason | string, nullable | |
| reminder_sent_at | timestamp, nullable | reminder email sent (at most once) |
| created_at / updated_at | timestamp | |

Unique partial index `(doctor_id, scheduled_at)` where `kind = 'SCHEDULED' AND status <> 'CANCELLED'` — the database refuses double bookings.

### `DoctorSchedule` (V3)
Weekly working hours; several blocks a day allowed.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| doctor_id | UUID (FK → Doctor) | |
| day_of_week | smallint | ISO, 1 = Monday |
| start_time / end_time | time | end after start |
| slot_minutes | smallint | 5–120 |

### `AppointmentEvent` (V3)
Append-only status history (DB trigger rejects `UPDATE`/`DELETE`).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| appointment_id | UUID (FK → Appointment) | |
| from_status | enum, nullable | null for the creating event |
| to_status | enum | |
| changed_by_user_id | UUID (FK → User) | |
| note | string, nullable | e.g. cancellation reason |
| created_at | timestamp | |

### `Consultation` (V4)
One per appointment. Read-only once `COMPLETED` — a trigger rejects updates and deletes (ADR-021).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| appointment_id | UUID (FK → Appointment), unique | |
| doctor_id | UUID (FK → Doctor) | the author |
| patient_id | UUID (FK → Patient) | |
| status | enum | `DRAFT`, `COMPLETED` |
| chief_complaint | string, nullable | |
| notes | text, nullable | doctor's working notes — never shown to the patient |
| diagnosis | string, nullable | required to complete (check constraint) |
| advice | text, nullable | |
| follow_up_date | date, nullable | |
| bp_systolic / bp_diastolic / pulse_bpm / spo2_percent | smallint, nullable | range-checked |
| temperature_c / weight_kg | numeric, nullable | range-checked |
| created_at / updated_at / completed_at | timestamp | |

### `Prescription` (V4)
At most one per consultation. Numbered and read-only once issued (trigger). PDFs are generated on request, not stored.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| consultation_id | UUID (FK → Consultation), unique | |
| prescription_code | string, unique, nullable | `RX-000123`, from `prescription_code_seq` when issued |
| issued_at | timestamp, nullable | set together with the code |
| created_at | timestamp | |

### `PrescriptionItem` (V4)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| prescription_id | UUID (FK → Prescription) | |
| position | smallint | 1, 2, 3 … unique per prescription |
| medicine | string | |
| dosage | string, nullable | e.g. `500 mg` |
| frequency | string | e.g. `1-0-1`, `SOS` |
| duration | string | e.g. `5 days` |
| instructions | string, nullable | e.g. `After food` |

### `Formulary` (V4)
Reference list of common generic medicines for autocomplete; doctors can still type any medicine.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| name | string | unique with `form` |
| form | string | Tablet, Syrup, Capsule, … |
| default_strength | string, nullable | pre-fills the dose |

## 3. Lab Side

### `LabTest` (catalog, V5)
What the lab offers. Seeded with 22 common tests; maintained by admins. Retired (`is_active = false`), never deleted (ADR-022).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| code | string, unique | e.g. `CBC`, `LIPID`; fixed once created |
| name | string | e.g. "Complete Blood Count" |
| category | string | Haematology, Biochemistry, … |
| sample_type | enum | `BLOOD`, `URINE`, `STOOL`, `SWAB` |
| required_tube_type | enum | `EDTA`, `PLAIN`, `SST`, `CITRATE`, `FLUORIDE`, `HEPARIN`, `URINE_CUP`, `STOOL_CUP`, `SWAB_TUBE` — see [[Rules]] tube-type rule |
| price | decimal | ≥ 0 |
| turnaround_hours | smallint | 1–720 |
| prep_instructions | string, nullable | shown to the patient, e.g. "Fast for 10–12 hours" |
| is_active | boolean | only active tests can be ordered |
| created_at / updated_at | timestamp | |

### `LabTestParameter` (V5)
What a test reports — replaces the single reference range first planned on `LabTest`.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_test_id | UUID (FK → LabTest) | |
| position | smallint | unique per test |
| name | string | e.g. "Haemoglobin" |
| unit | string, nullable | e.g. `g/dL` |
| ref_low / ref_high | decimal, nullable | normal range; empty for qualitative results (Positive/Negative) |
| critical_low / critical_high | decimal, nullable | critical limits (Phase 08) |

### `LabOrder` (V5)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| order_code | string, unique | `LO-000123`, from `lab_order_code_seq` |
| patient_id | UUID (FK → Patient) | taken from the consultation when ordered there |
| ordering_doctor_id | UUID (FK → Doctor) | |
| consultation_id | UUID (FK → Consultation), nullable | set when ordered during a consultation — see [[Appflow]] §1; at most one open order per consultation (partial unique index) |
| priority | enum | `ROUTINE`, `URGENT` |
| clinical_notes | string, nullable | the doctor's note for the lab — not shown to the patient |
| status | enum | `ORDERED`, `CANCELLED` (Phase 07 adds sample progress) |
| created_at / updated_at | timestamp | |
| cancelled_at / cancelled_by_user_id / cancellation_reason | nullable | |

### `LabOrderItem` (V5)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_order_id | UUID (FK → LabOrder) | |
| lab_test_id | UUID (FK → LabTest) | a test is live at most once per order (partial unique index) |
| test_name | string | snapshot at order time |
| price_at_order | decimal | snapshot at order time — billing uses this |
| status | enum | `ORDERED`, `CANCELLED` |
| created_at / cancelled_at | timestamp | |

### `Sample` (V7)
One row per physical tube or cup (ADR-024). It covers every test of one order that needs that tube. A rejected sample is **not reused** — a redraw creates a new `Sample` row pointing back at it, preserving the rejected one as a permanent record (see [[Rules]] §2). Rows are never deleted (trigger), and status changes are checked by a trigger against the allowed moves.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_code | string, unique | `LAB-YYYYMMDD-####`, from the daily `sample_code_counters` |
| lab_order_id | UUID (FK → LabOrder) | |
| patient_id | UUID (FK → Patient) | denormalised for access checks and lists |
| required_tube_type | enum | the tube the tests need (`EDTA`, `PLAIN`, `SST`, `CITRATE`, `FLUORIDE`, `HEPARIN`, `URINE_CUP`, `STOOL_CUP`, `SWAB_TUBE`) |
| status | enum | `ORDERED`, `COLLECTED`, `RECEIVED_AT_LAB`, `IN_TESTING`, `RESULT_ENTERED`, `VERIFIED`, `REPORT_GENERATED`, `DISPATCHED`, `REJECTED`, `CANCELLED` (all tests removed before collection) |
| tube_type_used | enum, nullable | set at collection |
| tube_mismatch | boolean | true when the tube used differs from the required one and the technician confirmed |
| body_site | string, nullable | set at collection; required for blood tubes |
| collected_at / collected_by_user_id | nullable | set together with `tube_type_used` (check constraint) |
| received_at / received_by_user_id | nullable | set at an accepted receipt check |
| redraw_of_sample_id | UUID (FK → Sample), nullable | the rejected sample this replaces |
| created_at / updated_at | timestamp | |

### `SampleItem` (V7)
Links a sample to the order lines it covers: `(sample_id, lab_order_item_id)`. A rejected sample keeps its rows; the redraw gets its own for the same lines.

### `SampleStatusEvent` (V7)
Append-only log — this table **is** the chain of custody. Never updated or deleted, only inserted (trigger).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| status | enum | same values as `Sample.status` |
| actor_user_id | UUID (FK → User) | who performed this transition (doctor when ordered, technician, pathologist, etc.) |
| occurred_at | timestamp | |
| detail | text, nullable | e.g., site and tube used, mismatch note, rejection reason, retest reason, analyzer/machine ID used |

### `RejectionRecord` (V7)
Permanent (trigger). One per rejected sample.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample), unique | |
| rejected_at_stage | enum | `RECEIVED_AT_LAB`, `IN_TESTING` |
| reason | enum | `HEMOLYZED`, `CLOTTED`, `INSUFFICIENT_VOLUME` (receipt only); `SAMPLE_EXHAUSTED`, `SAMPLE_DEGRADED` (testing only); `OTHER` (either). Stage/reason pairing enforced by a check constraint |
| note | text, nullable | required when `reason = OTHER` |
| flagged_by_user_id | UUID (FK → User) | the technician who rejected it |
| flagged_at | timestamp | |
| front_desk_notified_at | timestamp, nullable | set when the rejection is recorded — the notification is created in the same transaction |

### `Notification` (V7)
Something a role needs to act on. Today: a rejected sample tells the front desk to call the patient back.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| audience_role | enum | who sees it (`RECEPTIONIST` for rejections) |
| type | enum | `SAMPLE_REJECTED` |
| title / message | string | includes the patient's phone number |
| patient_id / sample_id | UUID, nullable | for the "Open record" link |
| created_at | timestamp | |
| handled_at / handled_by_user_id | nullable | set together when the front desk marks it done |

### `SampleResult` (V9)
One attempt at the results of a sample (ADR-025) — replaces the planned single-value `TestResult`. Each return for retest keeps its row (status `RETURNED_FOR_RETEST`) and the retest adds a new one. At most one row per sample is `PENDING_VERIFICATION` or `VERIFIED` (partial unique index). A trigger lets a result move only `PENDING_VERIFICATION → VERIFIED` or `→ RETURNED_FOR_RETEST`, and forbids edits to the entered fields and deletes.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| attempt_number | int | 1 for the first result, +1 per retest; unique with `sample_id` |
| status | enum | `PENDING_VERIFICATION`, `VERIFIED`, `RETURNED_FOR_RETEST` |
| analyzer | string, nullable | machine used |
| entered_by_user_id / entered_at | | the lab technician |
| verified_by_pathologist_id / verified_at | nullable | pathologist sign-off, see [[Rules]] verification gate; required together for `VERIFIED` (check constraint) |
| returned_by_pathologist_id / returned_at | nullable | set when returned for retest |
| return_reason | enum, nullable | `IMPLAUSIBLE_VALUE`, `INCONSISTENT_WITH_HISTORY`, `CRITICAL_VALUE_CONFIRMATION`, `QC_CONCERN`, `OTHER` |
| return_note | string, nullable | required when `return_reason = OTHER` |

### `ResultValue` (V9)
One value of one attempt: a parameter of a test on the sample. Append-only (trigger).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_result_id | UUID (FK → SampleResult) | |
| lab_order_item_id / parameter_id | UUID (FK) | the test and the parameter; unique per attempt |
| position | smallint | order on the sheet |
| parameter_name / unit | string | copied when entered |
| value_type | enum | `NUMERIC` or `TEXT` |
| numeric_value / text_value | decimal / string | exactly one, matching `value_type` (check constraint) |
| ref_low / ref_high / critical_low / critical_high | decimal, nullable | the ranges it was flagged against, copied when entered |
| flag | enum, nullable | `NORMAL`, `LOW`, `HIGH`, `CRITICAL_LOW`, `CRITICAL_HIGH`; numeric values only, worked out by the server |

`LabTestParameter` gains `value_type` (`NUMERIC` / `TEXT`, V9). It defaults to numeric when a range or limit is set.

### `Report` (V9)
One per verified sample. The PDF is drawn from the verified values when asked for — nothing is stored. A trigger refuses to insert a report unless a verified result exists (the hard gate); reports can't be deleted, and dispatch and receipt are each set once.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample), unique | |
| generated_at / generated_by_user_id | | set when the pathologist verifies |
| dispatched_channel | enum, nullable | `EMAIL`, `SMS` (not available yet), `DOWNLOAD_LINK` |
| dispatched_at / dispatched_by_user_id | nullable | set together with the channel |
| receipt_confirmed_at | timestamp, nullable | the patient's first open after dispatch |

## 4. Billing

### `Invoice` (V6)
One per finished consultation, or one per lab order placed outside a visit (ADR-023). Created by the system. `Doctor` also gains `consultation_fee` (default 500).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| invoice_code | string, unique | `INV-000123`, from `invoice_code_seq` |
| patient_id | UUID (FK → Patient) | |
| consultation_id | UUID (FK → Consultation), nullable, unique | the visit this bills |
| lab_order_id | UUID (FK → LabOrder), nullable, unique | set for a lab-only invoice; one of the two is required |
| consultation_fee / test_charges_total | decimal | sums of the live lines |
| discount | decimal, default 0 | ≤ fee + tests |
| discount_reason / discount_by_user_id / discount_at | nullable | **all required when discount > 0** (check constraint) — [[Rules]] §3 |
| amount_paid | decimal, default 0 | ≤ fee + tests − discount |
| status | enum | `UNPAID`, `PARTIALLY_PAID`, `PAID`, `VOID` |
| created_at / updated_at / paid_at | timestamp | |

### `InvoiceItem` (V6)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| invoice_id | UUID (FK → Invoice) | |
| kind | enum | `CONSULTATION`, `LAB_TEST` |
| description | string | e.g. "Consultation — Dr. Kabir Mehta", the test name |
| amount | decimal | copied at order time |
| lab_order_item_id | UUID (FK → LabOrderItem), unique, nullable | set for `LAB_TEST` lines |
| voided_at | timestamp, nullable | set when the test was removed from the order |

### `Payment` (V6)
Append-only — a trigger rejects updates and deletes.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| invoice_id | UUID (FK → Invoice) | |
| amount | decimal | > 0, never more than the balance |
| method | enum | `CASH`, `CARD`, `UPI` |
| reference | string, nullable | UPI transaction id, card receipt |
| received_by_user_id | UUID (FK → User) | |
| received_at | timestamp | |

### `InvoiceEvent` (V6)
Append-only history of an invoice.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| invoice_id | UUID (FK → Invoice) | |
| type | enum | `CREATED`, `LINE_VOIDED`, `DISCOUNT_APPLIED`, `PAYMENT_RECORDED`, `VOIDED` |
| amount / note | nullable | e.g. the discount and its reason |
| actor_user_id | UUID (FK → User), nullable | |
| created_at | timestamp | |

## 5. Inventory

### `InventoryItem` (V10)
A consumable the lab tracks (ADR-026). Never deleted (trigger); retire with `is_active = false`.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| name | string | e.g. "EDTA tubes (purple cap)"; unique ignoring case |
| category | enum | `TUBE`, `REAGENT`, `CONSUMABLE`, `OTHER` |
| unit | string | "tubes", "packs" |
| current_stock | integer, `>= 0` | changes only through a movement (one conditional UPDATE) |
| low_stock_threshold | integer, `>= 0` | low = stock below this while active; 0 = not watched |
| is_active | boolean | retired items keep their history and raise no alerts |
| created_at / updated_at | timestamp | |

### `InventoryMovement` (V10)
Append-only (trigger): one row per change to a level. Corrections are new rows.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| item_id | UUID (FK → InventoryItem) | |
| delta | integer, `<> 0` | signed change |
| stock_after | integer, `>= 0` | the level once applied |
| reason | enum | `OPENING`, `RESTOCK`, `USED`, `WASTAGE`, `CORRECTION` — the database checks the sign: restock `> 0`, used and wastage `< 0` |
| note | string, nullable | batch number, who used it, why |
| actor_user_id | UUID (FK → User) | |
| created_at | timestamp | |

### `ReagentTestMapping` (only if the reagent-mapping stretch feature is built)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_test_id | UUID (FK → LabTest) | |
| inventory_item_id | UUID (FK → InventoryItem) | |
| quantity_used_per_test | integer | |

## 6. Audit

### `PatientAccessLog`
Append-only — inserted on every full-record read by a Doctor or Pathologist (see [[Rules]] §1, [[Security]] §6). Never updated or deleted: a `BEFORE UPDATE OR DELETE` trigger (`forbid_modification()`) raises an error (V2).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_id | UUID (FK → Patient) | |
| user_id | UUID (FK → User) | who opened the record |
| resource | enum | `EMR`, `CONSULTATIONS`, `PRESCRIPTION`, `LAB_REPORT`, `LAB_HISTORY` |
| resource_id | UUID, nullable | e.g. which prescription/report |
| accessed_at | timestamp | |

### `AuditLog` (if full audit-on-edit stretch feature is built — otherwise `SampleStatusEvent` already covers the sample-specific audit trail)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| entity_type | string | e.g., `Patient`, `TestResult`, `Report` |
| entity_id | UUID | |
| changed_by_user_id | UUID (FK → User) | |
| changed_at | timestamp | |
| change_summary | text / JSON diff | |

## 7. Relationship Summary

```
User ──1:1── Patient / Doctor / Staff (by role)

Patient ──1:N── Appointment ──1:1── Consultation ──1:N── Prescription
Consultation ──0:1── LabOrder ──1:N── LabOrderItem ──1:1── LabTest (catalog)
LabOrderItem ──1:N── Sample (N because a rejected sample spawns a new one)
Sample ──1:N── SampleStatusEvent (append-only chain of custody)
Sample ──0:1── RejectionRecord
Sample ──0:1── TestResult ──0:1── Report

Patient ──1:N── Invoice
```

This mirrors the state machine in [[Appflow]] §3 and the hard rules in [[Rules]] — `Sample.status` should never be mutated directly without a corresponding `SampleStatusEvent` row; treat the event log as the source of truth and `Sample.status` as a denormalized "current state" cache for query convenience.
