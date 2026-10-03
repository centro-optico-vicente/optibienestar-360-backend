SET search_path TO app, public;

-- ============================================================================
-- V173: discount-authority matrix. A promoter may discount a payment up to the
-- most restrictive cap defined on their rank (cargo) and their promoter type
-- (DiscountAuthority). NULL on an axis = that axis sets no cap; NULL on both
-- = 0% (cannot discount). Back-office users holding ALLOWS_DISCOUNT who are
-- not promoters stay uncapped. No behavior change for back-office users.
-- ============================================================================

ALTER TABLE promoter_ranks
    ADD COLUMN max_discount_pct NUMERIC(5, 2)
        CONSTRAINT chk_promoter_ranks_max_discount_pct
            CHECK (max_discount_pct IS NULL OR (max_discount_pct >= 0 AND max_discount_pct <= 100));

ALTER TABLE promoter_types
    ADD COLUMN max_discount_pct NUMERIC(5, 2)
        CONSTRAINT chk_promoter_types_max_discount_pct
            CHECK (max_discount_pct IS NULL OR (max_discount_pct >= 0 AND max_discount_pct <= 100));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'promoter_ranks' AND column_name = 'max_discount_pct'
    ) THEN
        RAISE EXCEPTION 'V173: promoter_ranks.max_discount_pct was not created';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'promoter_types' AND column_name = 'max_discount_pct'
    ) THEN
        RAISE EXCEPTION 'V173: promoter_types.max_discount_pct was not created';
    END IF;
END $$;
