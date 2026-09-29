-- Critical value alerts (ADR-028). A report with a critical value has to be acknowledged by the doctor who
-- ordered the test, and the acknowledgement is recorded: who, when and an optional note.
--
-- is_critical is copied onto the report when it is created. The values behind a verified result never
-- change, so it never goes stale, and "critical and not yet acknowledged" becomes a cheap indexed lookup.
ALTER TABLE reports ADD COLUMN is_critical boolean NOT NULL DEFAULT false;

UPDATE reports r SET is_critical = true
WHERE EXISTS (SELECT 1 FROM sample_results sr JOIN result_values v ON v.sample_result_id = sr.id
              WHERE sr.sample_id = r.sample_id AND sr.status = 'VERIFIED' AND v.flag IN ('CRITICAL_LOW', 'CRITICAL_HIGH'));

ALTER TABLE reports
    ADD COLUMN critical_acknowledged_at         timestamptz,
    ADD COLUMN critical_acknowledged_by_user_id uuid REFERENCES users (id),
    ADD COLUMN critical_ack_note                varchar(300),
    ADD CONSTRAINT reports_ack_has_actor
        CHECK ((critical_acknowledged_at IS NULL) = (critical_acknowledged_by_user_id IS NULL)),
    ADD CONSTRAINT reports_ack_only_when_critical
        CHECK (critical_acknowledged_at IS NULL OR is_critical);

-- The doctor's and the lab's "waiting for acknowledgement" lists.
CREATE INDEX reports_critical_open_idx ON reports (generated_at) WHERE is_critical AND critical_acknowledged_at IS NULL;

-- Adds two rules to the V11 function: whether a report is critical is fixed, and an acknowledgement is set once.
CREATE OR REPLACE FUNCTION enforce_report_updates() RETURNS trigger AS $$
BEGIN
    IF NEW.sample_id <> OLD.sample_id OR NEW.generated_at <> OLD.generated_at
       OR NEW.generated_by_user_id <> OLD.generated_by_user_id OR NEW.verification_code <> OLD.verification_code
       OR NEW.is_critical <> OLD.is_critical THEN
        RAISE EXCEPTION 'A generated report cannot be edited';
    END IF;
    IF OLD.dispatched_at IS NOT NULL AND (NEW.dispatched_at IS DISTINCT FROM OLD.dispatched_at
       OR NEW.dispatched_channel IS DISTINCT FROM OLD.dispatched_channel) THEN
        RAISE EXCEPTION 'A report that has been dispatched cannot be re-dispatched';
    END IF;
    IF OLD.receipt_confirmed_at IS NOT NULL AND NEW.receipt_confirmed_at IS DISTINCT FROM OLD.receipt_confirmed_at THEN
        RAISE EXCEPTION 'Receipt has already been confirmed';
    END IF;
    IF OLD.critical_acknowledged_at IS NOT NULL AND (NEW.critical_acknowledged_at IS DISTINCT FROM OLD.critical_acknowledged_at
       OR NEW.critical_acknowledged_by_user_id IS DISTINCT FROM OLD.critical_acknowledged_by_user_id
       OR NEW.critical_ack_note IS DISTINCT FROM OLD.critical_ack_note) THEN
        RAISE EXCEPTION 'A critical result has already been acknowledged';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
