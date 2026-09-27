# Glossary

| Term | Meaning |
|---|---|
| **TAT (Turnaround Time)** | Elapsed time for a test from sample order (or collection) to report ready, typically measured per test type |
| **NABL** | National Accreditation Board for Testing and Calibration Laboratories (India) — a real-world accreditation body whose audit-trail expectations informed this system's design; this project does not pursue actual NABL certification (see [[NonGoals]]) |
| **Phlebotomist** | Staff member trained to draw blood samples |
| **Hemolyzed sample** | A blood sample where red blood cells have ruptured, releasing hemoglobin — makes many test results unreliable and is grounds for rejection |
| **Clotted sample** | A sample that has coagulated when it shouldn't have (e.g., in an anticoagulant tube) — grounds for rejection |
| **Chain of custody** | An unbroken, timestamped record of every person who handled a sample and when — a regulatory requirement in real diagnostic labs |
| **EMR (Electronic Medical Record)** | A patient's digital history: past visits, diagnoses, known allergies |
| **RBAC (Role-Based Access Control)** | Access rules based on a user's role (Patient/Doctor/Receptionist/Lab Technician/Admin) rather than per-user grants |
| **JWT (JSON Web Token)** | A signed token used to authenticate requests after login, without server-side session state |
| **ORM (Object-Relational Mapping)** | Library that maps database rows to Java objects — here, JPA/Hibernate at runtime |
| **Next.js** | React-based JavaScript framework used for the frontend (App Router, file-based routes); here it holds no business logic or DB access (see [[Decisions]] ADR-008) |
| **REST API** | The JSON-over-HTTP interface Spring Boot exposes under `/api`; the only way the frontend reads/writes data (see [[API]]) |
| **CSRF (Cross-Site Request Forgery)** | An attack where another website makes the user's browser send a request using their login cookie; blocked here by requiring a custom header (see [[Decisions]] ADR-014) |
| **httpOnly cookie** | A cookie JavaScript cannot read; used to hold the JWT so XSS can't steal it (see [[Decisions]] ADR-009) |
| **Flyway** | Java database-migration tool; applies versioned SQL files (`V1__...sql`) to the database when the backend starts (see [[TechSpecifications]], [[Decisions]] ADR-010) |
| **Migration** | A versioned, never-edited-after-merge SQL file that changes the database schema one step forward |
| **Reference range** | The normal expected value range for a lab test result (e.g., normal blood sugar range); results are auto-checked against this |
| **Pathologist** | Separate system role: a medical specialist who reviews lab results and signs off on them before release (see [[Decisions]] ADR-011) |
| **Pathologist verification** | The required digital sign-off by a pathologist before any lab report can be released to a patient |
| **Return for retest** | A pathologist sending an entered result back to the lab to run the test again on the same sample, with a reason; the original result is kept (see [[Rules]] §2) |
| **Sample rejection** | Flagging a sample as unusable and requesting a redraw — either at receipt (hemolyzed/clotted/insufficient volume) or during testing (sample exhausted/degraded) |
| **Reagent** | A consumable chemical/substance used in running a lab test — tracked in inventory |
| **Token / queue number** | A number issued to walk-in patients to manage consultation order without a fixed appointment slot |
| **Care relationship** | A doctor has one with a patient if there is a non-cancelled appointment/walk-in token with that doctor, or a past consultation by them; required to open the full record (see [[Rules]] §1) |
| **Consultation** | A doctor visit event — the anchor record that appointments, prescriptions, and (often) lab orders attach to |
| **Formulary** | The clinic's list of common medicines (name, form, usual strength) used to suggest medicines while prescribing |
| **Prescription number** | `RX-000123` — issued in sequence when a consultation is finished; the prescription is locked from then on |
| **Lab order** | A request for one or more tests, created either directly or automatically from a doctor's consultation notes |
