-- Phase 07: give lab orders placed before samples existed their samples (ADR-024).
-- Every live test on an open order that no sample covers gets one, grouped by tube like new orders
-- are: one sample per (order, tube). Codes come from the same daily counter, numbered by the day the
-- order was placed in the clinic's time zone (Asia/Kolkata, the app default); each sample starts
-- ORDERED with a custody event credited to the ordering doctor. Safe to run again: covered tests
-- are skipped.

DO $$
DECLARE
    g   record;
    sid uuid;
    n   integer;
    d   date;
BEGIN
    FOR g IN
        SELECT o.id AS order_id, o.order_code, o.patient_id, o.created_at, doc.user_id AS doctor_user_id,
               t.required_tube_type AS tube
        FROM lab_orders o
        JOIN doctors doc ON doc.id = o.ordering_doctor_id
        JOIN lab_order_items i ON i.lab_order_id = o.id AND i.status <> 'CANCELLED'
        JOIN lab_tests t ON t.id = i.lab_test_id
        WHERE o.status = 'ORDERED'
          AND NOT EXISTS (SELECT 1 FROM sample_items si WHERE si.lab_order_item_id = i.id)
        GROUP BY o.id, o.order_code, o.patient_id, o.created_at, doc.user_id, t.required_tube_type
        ORDER BY o.created_at, o.id, t.required_tube_type
    LOOP
        d := (g.created_at AT TIME ZONE 'Asia/Kolkata')::date;

        INSERT INTO sample_code_counters (day, last_number) VALUES (d, 1)
        ON CONFLICT (day) DO UPDATE SET last_number = sample_code_counters.last_number + 1
        RETURNING last_number INTO n;

        INSERT INTO samples (sample_code, lab_order_id, patient_id, required_tube_type, created_at)
        VALUES ('LAB-' || to_char(d, 'YYYYMMDD') || '-' || lpad(n::text, 4, '0'), g.order_id, g.patient_id, g.tube,
                g.created_at)
        RETURNING id INTO sid;

        INSERT INTO sample_items (sample_id, lab_order_item_id)
        SELECT sid, i.id
        FROM lab_order_items i
        JOIN lab_tests t ON t.id = i.lab_test_id
        WHERE i.lab_order_id = g.order_id AND i.status <> 'CANCELLED' AND t.required_tube_type = g.tube
          AND NOT EXISTS (SELECT 1 FROM sample_items si WHERE si.lab_order_item_id = i.id);

        INSERT INTO sample_status_events (sample_id, status, actor_user_id, occurred_at, detail)
        VALUES (sid, 'ORDERED', g.doctor_user_id, g.created_at, 'Created for existing order ' || g.order_code);
    END LOOP;
END
$$;
