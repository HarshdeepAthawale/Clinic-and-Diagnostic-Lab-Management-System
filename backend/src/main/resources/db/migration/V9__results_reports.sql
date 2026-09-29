-- Phase 08: results, pathologist verification, reports. See Docs/Schema.md §3 and ADR-025.
-- Results belong to a sample (one tube, several tests, each test several parameters). Every attempt is
-- kept: a result returned for retest stays as history and the retest is a new attempt. The rules that
-- protect a report live here, not only in Java: results and their values can't be rewritten, a result
-- can only be verified or returned once, and a report can't exist until a result is verified.

-- ---------------------------------------------------------------------------------------------
-- What kind of value each parameter takes
-- ---------------------------------------------------------------------------------------------

ALTER TABLE lab_test_parameters
    ADD COLUMN value_type varchar(8) NOT NULL DEFAULT 'TEXT' CHECK (value_type IN ('NUMERIC', 'TEXT'));

-- The seeded parameters that have a normal range or critical limits are numbers; the rest
-- (blood group, Positive/Negative, titres, colony counts) are entered as text.
UPDATE lab_test_parameters SET value_type = 'NUMERIC'
WHERE ref_low IS NOT NULL OR ref_high IS NOT NULL OR critical_low IS NOT NULL OR critical_high IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Results
-- ---------------------------------------------------------------------------------------------

CREATE TABLE sample_results (
    id                          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_id                   uuid         NOT NULL REFERENCES samples (id),
    attempt_number              integer      NOT NULL CHECK (attempt_number >= 1),
    status                      varchar(24)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'RETURNED_FOR_RETEST')),
    analyzer                    varchar(60),
    entered_by_user_id          uuid         NOT NULL REFERENCES users (id),
    entered_at                  timestamptz  NOT NULL DEFAULT now(),
    verified_by_pathologist_id  uuid         REFERENCES pathologists (id),
    verified_at                 timestamptz,
    returned_by_pathologist_id  uuid         REFERENCES pathologists (id),
    returned_at                 timestamptz,
    return_reason               varchar(32)
        CHECK (return_reason IS NULL OR return_reason IN
               ('IMPLAUSIBLE_VALUE', 'INCONSISTENT_WITH_HISTORY', 'CRITICAL_VALUE_CONFIRMATION', 'QC_CONCERN', 'OTHER')),
    return_note                 varchar(500),
    UNIQUE (sample_id, attempt_number),
    -- A result is exactly one of: waiting, verified (with who and when), or returned (with who, when and why).
    CHECK ((status = 'PENDING_VERIFICATION' AND verified_at IS NULL AND returned_at IS NULL)
           OR (status = 'VERIFIED' AND verified_at IS NOT NULL AND verified_by_pathologist_id IS NOT NULL
               AND returned_at IS NULL)
           OR (status = 'RETURNED_FOR_RETEST' AND returned_at IS NOT NULL AND returned_by_pathologist_id IS NOT NULL
               AND return_reason IS NOT NULL AND verified_at IS NULL)),
    CHECK (return_reason IS DISTINCT FROM 'OTHER' OR (return_note IS NOT NULL AND length(btrim(return_note)) > 0))
);

-- At most one result per sample is waiting for, or has passed, verification (Schema.md §3).
CREATE UNIQUE INDEX sample_results_one_live_uq ON sample_results (sample_id)
    WHERE status IN ('PENDING_VERIFICATION', 'VERIFIED');
CREATE INDEX sample_results_queue_idx ON sample_results (entered_at) WHERE status = 'PENDING_VERIFICATION';
CREATE INDEX sample_results_verified_by_idx ON sample_results (verified_by_pathologist_id, verified_at DESC)
    WHERE status = 'VERIFIED';

-- The values of one attempt: every parameter of every test on the sample. The name, unit and ranges are
-- copied at entry, so later catalog edits never change what was flagged. The flag is worked out by the
-- server (numeric values only) and stored with the value.
CREATE TABLE result_values (
    id                uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_result_id  uuid          NOT NULL REFERENCES sample_results (id),
    lab_order_item_id uuid          NOT NULL REFERENCES lab_order_items (id),
    parameter_id      uuid          NOT NULL REFERENCES lab_test_parameters (id),
    position          smallint      NOT NULL CHECK (position >= 1),
    parameter_name    varchar(120)  NOT NULL,
    unit              varchar(30),
    value_type        varchar(8)    NOT NULL CHECK (value_type IN ('NUMERIC', 'TEXT')),
    numeric_value     numeric(14,4),
    text_value        varchar(200),
    ref_low           numeric(12,3),
    ref_high          numeric(12,3),
    critical_low      numeric(12,3),
    critical_high     numeric(12,3),
    flag              varchar(16)
        CHECK (flag IS NULL OR flag IN ('NORMAL', 'LOW', 'HIGH', 'CRITICAL_LOW', 'CRITICAL_HIGH')),
    UNIQUE (sample_result_id, parameter_id),
    CHECK ((value_type = 'NUMERIC' AND numeric_value IS NOT NULL AND text_value IS NULL)
           OR (value_type = 'TEXT' AND text_value IS NOT NULL AND length(btrim(text_value)) > 0 AND numeric_value IS NULL)),
    CHECK (flag IS NULL OR value_type = 'NUMERIC')
);

CREATE INDEX result_values_result_idx ON result_values (sample_result_id, position);
CREATE INDEX result_values_parameter_idx ON result_values (parameter_id);

CREATE TRIGGER result_values_append_only
    BEFORE UPDATE OR DELETE ON result_values
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- A result is decided once: waiting -> verified, or waiting -> returned. Nothing else moves, and no
-- result is ever deleted (a returned one stays as the retest's history).
CREATE FUNCTION enforce_result_transition() RETURNS trigger AS $$
BEGIN
    IF NEW.sample_id <> OLD.sample_id OR NEW.attempt_number <> OLD.attempt_number
       OR NEW.entered_by_user_id <> OLD.entered_by_user_id OR NEW.entered_at <> OLD.entered_at
       OR NEW.analyzer IS DISTINCT FROM OLD.analyzer THEN
        RAISE EXCEPTION 'An entered result cannot be edited';
    END IF;
    IF OLD.status = 'PENDING_VERIFICATION' AND NEW.status IN ('VERIFIED', 'RETURNED_FOR_RETEST') THEN
        RETURN NEW;
    END IF;
    IF NEW.status = OLD.status AND NEW.verified_at IS NOT DISTINCT FROM OLD.verified_at
       AND NEW.returned_at IS NOT DISTINCT FROM OLD.returned_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'A result cannot move from % to %', OLD.status, NEW.status;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sample_results_transition
    BEFORE UPDATE ON sample_results
    FOR EACH ROW EXECUTE FUNCTION enforce_result_transition();

CREATE TRIGGER sample_results_no_delete
    BEFORE DELETE ON sample_results
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();

-- ---------------------------------------------------------------------------------------------
-- Reports
-- ---------------------------------------------------------------------------------------------

-- One report per sample, created when its result is verified. The PDF is drawn from the verified
-- values when asked for (nothing is stored). The patient can open it once it has been dispatched.
CREATE TABLE reports (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_id              uuid         NOT NULL UNIQUE REFERENCES samples (id),
    generated_at           timestamptz  NOT NULL DEFAULT now(),
    generated_by_user_id   uuid         NOT NULL REFERENCES users (id),
    dispatched_channel     varchar(16)  CHECK (dispatched_channel IS NULL OR dispatched_channel IN ('EMAIL', 'SMS', 'DOWNLOAD_LINK')),
    dispatched_at          timestamptz,
    dispatched_by_user_id  uuid         REFERENCES users (id),
    receipt_confirmed_at   timestamptz,
    CHECK ((dispatched_channel IS NULL) = (dispatched_at IS NULL) AND (dispatched_at IS NULL) = (dispatched_by_user_id IS NULL)),
    CHECK (receipt_confirmed_at IS NULL OR dispatched_at IS NOT NULL)
);

-- The verification gate (Rules.md §2): no report without a pathologist's sign-off — enforced here so
-- no code path, bug or manual insert can skip it.
CREATE FUNCTION require_verified_result() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sample_results
                   WHERE sample_id = NEW.sample_id AND status = 'VERIFIED' AND verified_at IS NOT NULL) THEN
        RAISE EXCEPTION 'A report cannot be generated before the result has been verified by a pathologist';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reports_require_verification
    BEFORE INSERT ON reports
    FOR EACH ROW EXECUTE FUNCTION require_verified_result();

CREATE TRIGGER reports_no_delete
    BEFORE DELETE ON reports
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();

-- Dispatch and receipt are set once each; the report itself is never edited.
CREATE FUNCTION enforce_report_updates() RETURNS trigger AS $$
BEGIN
    IF NEW.sample_id <> OLD.sample_id OR NEW.generated_at <> OLD.generated_at
       OR NEW.generated_by_user_id <> OLD.generated_by_user_id THEN
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

CREATE TRIGGER reports_updates
    BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION enforce_report_updates();
