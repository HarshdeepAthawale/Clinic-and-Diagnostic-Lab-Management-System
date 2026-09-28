-- Phase 06: billing. See Docs/Schema.md §4 and ADR-023.
-- One invoice per visit: the doctor's consultation fee plus the tests ordered in that consultation,
-- created when the consultation is finished. A lab order placed outside a visit gets its own
-- invoice straight away. Lines copy amounts at billing time; payments and billing events are
-- append-only; a discount can't exist without who applied it and why.

-- ---------------------------------------------------------------------------------------------
-- Consultation fee per doctor
-- ---------------------------------------------------------------------------------------------

ALTER TABLE doctors
    ADD COLUMN consultation_fee numeric(10,2) NOT NULL DEFAULT 500 CHECK (consultation_fee >= 0);

-- ---------------------------------------------------------------------------------------------
-- Invoices
-- ---------------------------------------------------------------------------------------------

CREATE SEQUENCE invoice_code_seq START 1;

CREATE TABLE invoices (
    id                     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_code           varchar(12)   NOT NULL UNIQUE,
    patient_id             uuid          NOT NULL REFERENCES patients (id),
    -- The visit this bills (one invoice per consultation), or null for a lab-only invoice.
    consultation_id        uuid          UNIQUE REFERENCES consultations (id),
    -- For a lab-only invoice: the direct lab order it bills.
    lab_order_id           uuid          UNIQUE REFERENCES lab_orders (id),
    consultation_fee       numeric(10,2) NOT NULL DEFAULT 0 CHECK (consultation_fee >= 0),
    test_charges_total     numeric(10,2) NOT NULL DEFAULT 0 CHECK (test_charges_total >= 0),
    discount               numeric(10,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    discount_reason        varchar(300),
    discount_by_user_id    uuid          REFERENCES users (id),
    discount_at            timestamptz,
    amount_paid            numeric(10,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    status                 varchar(16)   NOT NULL DEFAULT 'UNPAID'
        CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    paid_at                timestamptz,
    CHECK (consultation_id IS NOT NULL OR lab_order_id IS NOT NULL),
    -- Accountability (Rules.md §3): no discount without who applied it and why.
    CHECK (discount = 0 OR (discount_by_user_id IS NOT NULL AND discount_reason IS NOT NULL AND discount_at IS NOT NULL)),
    CHECK (discount <= consultation_fee + test_charges_total),
    CHECK (amount_paid <= consultation_fee + test_charges_total - discount)
);

CREATE INDEX invoices_patient_idx ON invoices (patient_id, created_at DESC);
CREATE INDEX invoices_outstanding_idx ON invoices (created_at) WHERE status IN ('UNPAID', 'PARTIALLY_PAID');

CREATE TABLE invoice_items (
    id                 uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id         uuid          NOT NULL REFERENCES invoices (id),
    kind               varchar(16)   NOT NULL CHECK (kind IN ('CONSULTATION', 'LAB_TEST')),
    description        varchar(200)  NOT NULL,
    amount             numeric(10,2) NOT NULL CHECK (amount >= 0),
    lab_order_item_id  uuid          UNIQUE REFERENCES lab_order_items (id),
    voided_at          timestamptz,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    CHECK ((kind = 'LAB_TEST') = (lab_order_item_id IS NOT NULL))
);

CREATE INDEX invoice_items_invoice_idx ON invoice_items (invoice_id);

-- ---------------------------------------------------------------------------------------------
-- Payments and billing history (append-only)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE payments (
    id                   uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id           uuid          NOT NULL REFERENCES invoices (id),
    amount               numeric(10,2) NOT NULL CHECK (amount > 0),
    method               varchar(8)    NOT NULL CHECK (method IN ('CASH', 'CARD', 'UPI')),
    reference            varchar(60),
    received_by_user_id  uuid          NOT NULL REFERENCES users (id),
    received_at          timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX payments_invoice_idx ON payments (invoice_id);
CREATE INDEX payments_received_idx ON payments (received_at);

CREATE TABLE invoice_events (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id    uuid          NOT NULL REFERENCES invoices (id),
    type          varchar(20)   NOT NULL
        CHECK (type IN ('CREATED', 'LINE_VOIDED', 'DISCOUNT_APPLIED', 'PAYMENT_RECORDED', 'VOIDED')),
    amount        numeric(10,2),
    note          varchar(300),
    actor_user_id uuid          REFERENCES users (id),
    created_at    timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX invoice_events_invoice_idx ON invoice_events (invoice_id, created_at);

-- forbid_modification() comes from V2 (patient_access_log).
CREATE TRIGGER payments_append_only
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

CREATE TRIGGER invoice_events_append_only
    BEFORE UPDATE OR DELETE ON invoice_events
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
