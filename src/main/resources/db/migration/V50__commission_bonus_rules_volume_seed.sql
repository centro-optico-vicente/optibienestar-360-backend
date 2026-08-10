SET search_path TO app, public;

-- V50: monthly-volume stacking bonuses (vertical-8 Ítem A). Reuses the V37
-- engine unchanged — THRESHOLD accrual grants once per rule per promoter per
-- calendar month, and rules are additive, so a promoter crossing all 4
-- thresholds in the same month collects $100+$100+$150+$300 = $650 on top of
-- the escalated inscription commission (V49).

INSERT INTO commission_bonus_rules
    (uuid, name, description, metric, accrual, threshold_count, window_strategy, reward_type, flat_amount, reward_currency)
VALUES
    (gen_random_uuid(), 'Bono 41 inscripciones/mes',
     'Bono por alcanzar 41 inscripciones nuevas en el mes calendario.',
     'NEW_SUBSCRIBERS', 'THRESHOLD', 41, 'MONTHLY', 'FLAT', 100.00, 'USD'),
    (gen_random_uuid(), 'Bono 61 inscripciones/mes',
     'Bono por alcanzar 61 inscripciones nuevas en el mes calendario.',
     'NEW_SUBSCRIBERS', 'THRESHOLD', 61, 'MONTHLY', 'FLAT', 100.00, 'USD'),
    (gen_random_uuid(), 'Bono 76 inscripciones/mes',
     'Bono por alcanzar 76 inscripciones nuevas en el mes calendario.',
     'NEW_SUBSCRIBERS', 'THRESHOLD', 76, 'MONTHLY', 'FLAT', 150.00, 'USD'),
    (gen_random_uuid(), 'Bono 101 inscripciones/mes',
     'Bono por alcanzar 101 inscripciones nuevas en el mes calendario.',
     'NEW_SUBSCRIBERS', 'THRESHOLD', 101, 'MONTHLY', 'FLAT', 300.00, 'USD');


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM commission_bonus_rules
        WHERE threshold_count IN (41, 61, 76, 101) AND window_strategy = 'MONTHLY') <> 4 THEN
        RAISE EXCEPTION 'V50: monthly-volume bonus rule seed did not land';
    END IF;
END $$;
