-- QR-verified reports (ADR-027). Every report gets a random verification code, printed on the PDF as a QR
-- code. Anyone holding the printout can scan it and see that the clinic really issued the report, without
-- signing in and without seeing any results.
--
-- 32 hex characters = 128 bits from the database's random generator, so a code cannot be guessed.
-- Existing reports are given a code by the default; new ones get one from the application.
ALTER TABLE reports ADD COLUMN verification_code varchar(32) NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', '');
ALTER TABLE reports ALTER COLUMN verification_code DROP DEFAULT;
CREATE UNIQUE INDEX reports_verification_code_uq ON reports (verification_code);

-- The code is fixed once issued, like the rest of the report (V9 rules stay as they were).
CREATE OR REPLACE FUNCTION enforce_report_updates() RETURNS trigger AS $$
BEGIN
    IF NEW.sample_id <> OLD.sample_id OR NEW.generated_at <> OLD.generated_at
       OR NEW.generated_by_user_id <> OLD.generated_by_user_id OR NEW.verification_code <> OLD.verification_code THEN
        RAISE EXCEPTION 'A generated report cannot be edited';
    END IF;
    IF OLD.dispatched_at IS NOT NULL AND (NEW.dispatched_at IS DISTINCT FROM OLD.dispatched_at
       OR NEW.dispatched_channel IS DISTINCT FROM OLD.dispatched_channel) THEN
        RAISE EXCEPTION 'A report that has been dispatched cannot be re-dispatched';
    END IF;
    IF OLD.receipt_confirmed_at IS NOT NULL AND NEW.receipt_confirmed_at IS DISTINCT FROM OLD.receipt_confirmed_at THEN
        RAISE EXCEPTION 'Receipt has already been confirmed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
