SET search_path TO app, public;

-- ============================================================================
-- V189: ALIADO contracts are accepted once per ally agreement, and every
-- acceptance keeps the exact text the user accepted.
--
-- Until now an acceptance was unique per (user, version): one ALIADO template
-- shared by two of the user's allies was accepted once, rendered with the
-- data of whichever agreement the (unordered) query returned first. A doctor
-- working in three clinics, each with its own agreement and rates, must
-- accept each agreement with that clinic's data.
--
-- 1. terms_acceptances gains:
--      ally_agreement_id  — the agreement an ALIADO acceptance was given for
--                           (NULL for AFILIADO / ASESOR, and for legacy
--                           ALIADO rows that could not be linked, see 3);
--      rendered_content   — the contract text exactly as shown and accepted
--                           (placeholders already resolved);
--      content_sha256     — hex SHA-256 of rendered_content (UTF-8), so the
--                           accepted text can be proven unaltered.
-- 2. uq_terms_acceptances_user_version (V139) is replaced by two partial
--    unique indexes: (user, version) when there is no agreement, and
--    (user, version, agreement) when there is one.
-- 3. Backfill: an existing ALIADO acceptance is linked to the agreement only
--    when the user has exactly ONE agreement (any status, through any of
--    their ally_users rows) linking that version. Otherwise it stays NULL and
--    the user accepts each in-force agreement separately at next login —
--    never guess which agreement a legal acceptance was given for.
--    rendered_content stays NULL for legacy rows (the backend re-renders
--    them with current data when it needs the text).
-- ============================================================================

-- ─── 1. New columns ─────────────────────────────────────────────────────────

ALTER TABLE terms_acceptances
    ADD COLUMN ally_agreement_id BIGINT      NULL REFERENCES ally_agreements (ally_agreements_id),
    ADD COLUMN rendered_content  TEXT        NULL,
    ADD COLUMN content_sha256    VARCHAR(64) NULL
        CONSTRAINT chk_terms_acceptances_content_sha256 CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$');

CREATE INDEX idx_terms_acceptances_ally_agreement
    ON terms_acceptances (ally_agreement_id) WHERE ally_agreement_id IS NOT NULL;

COMMENT ON COLUMN terms_acceptances.ally_agreement_id IS 'Convenio de aliado para el que se aceptó el contrato ALIADO; NULL para AFILIADO/ASESOR y para aceptaciones ALIADO anteriores a V189 que no se pudieron vincular a un único convenio';
COMMENT ON COLUMN terms_acceptances.rendered_content IS 'Texto del contrato tal como se mostró y aceptó (variables ya resueltas); NULL en aceptaciones anteriores a V189';
COMMENT ON COLUMN terms_acceptances.content_sha256 IS 'SHA-256 en hexadecimal (UTF-8) de rendered_content, para probar que el texto aceptado no se alteró';

-- ─── 2. Uniqueness per agreement ────────────────────────────────────────────

ALTER TABLE terms_acceptances DROP CONSTRAINT uq_terms_acceptances_user_version;

CREATE UNIQUE INDEX uq_terms_acceptances_user_version
    ON terms_acceptances (user_id, terms_versions_id)
    WHERE ally_agreement_id IS NULL;

CREATE UNIQUE INDEX uq_terms_acceptances_user_version_agreement
    ON terms_acceptances (user_id, terms_versions_id, ally_agreement_id)
    WHERE ally_agreement_id IS NOT NULL;

-- ─── 3. Backfill legacy ALIADO acceptances ──────────────────────────────────

DO $$
DECLARE
    v_total    INTEGER;
    v_linked   INTEGER;
    v_unlinked INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_total
    FROM terms_acceptances ta
    JOIN terms_versions tv ON tv.terms_versions_id = ta.terms_versions_id
    WHERE tv.term_type = 'ALIADO';

    WITH candidates AS (
        SELECT ta.terms_acceptances_id,
               MIN(ag.ally_agreements_id)                 AS ally_agreements_id,
               COUNT(DISTINCT ag.ally_agreements_id)      AS agreements
        FROM terms_acceptances ta
        JOIN terms_versions tv  ON tv.terms_versions_id = ta.terms_versions_id
        JOIN ally_users au      ON au.user_id = ta.user_id
        JOIN ally_agreements ag ON ag.ally_id = au.ally_id
                               AND ag.terms_version_id = ta.terms_versions_id
        WHERE tv.term_type = 'ALIADO'
          AND ta.ally_agreement_id IS NULL
        GROUP BY ta.terms_acceptances_id
    )
    UPDATE terms_acceptances ta
    SET ally_agreement_id = c.ally_agreements_id
    FROM candidates c
    WHERE c.terms_acceptances_id = ta.terms_acceptances_id
      AND c.agreements = 1;

    GET DIAGNOSTICS v_linked = ROW_COUNT;
    v_unlinked := v_total - v_linked;

    RAISE NOTICE 'V189: % ALIADO acceptance(s): % linked to their only agreement, % left unlinked (re-accepted per agreement)',
        v_total, v_linked, v_unlinked;
END $$;

-- ─── Self-check ─────────────────────────────────────────────────────────────

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_terms_acceptances_user_version') THEN
        RAISE EXCEPTION 'V189: uq_terms_acceptances_user_version constraint still present';
    END IF;
    IF (SELECT COUNT(*) FROM pg_indexes
        WHERE tablename = 'terms_acceptances'
          AND indexname IN ('uq_terms_acceptances_user_version', 'uq_terms_acceptances_user_version_agreement')) <> 2 THEN
        RAISE EXCEPTION 'V189: partial unique indexes on terms_acceptances missing';
    END IF;
    IF EXISTS (SELECT 1
               FROM terms_acceptances ta
               JOIN terms_versions tv ON tv.terms_versions_id = ta.terms_versions_id
               WHERE ta.ally_agreement_id IS NOT NULL
                 AND tv.term_type <> 'ALIADO') THEN
        RAISE EXCEPTION 'V189: a non-ALIADO acceptance was linked to an ally agreement';
    END IF;
    IF EXISTS (SELECT 1
               FROM terms_acceptances ta
               JOIN ally_agreements ag ON ag.ally_agreements_id = ta.ally_agreement_id
               WHERE ag.terms_version_id IS DISTINCT FROM ta.terms_versions_id) THEN
        RAISE EXCEPTION 'V189: an acceptance was linked to an agreement of another version';
    END IF;
END $$;
