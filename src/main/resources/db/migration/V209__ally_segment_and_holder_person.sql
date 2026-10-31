-- ====================================================
-- V209__ally_segment_and_holder_person.sql
--
-- Hub ADR 0031 (professional ally, B2P) — step 3 of the plan
-- `.ai/plans/2026-10-31-professional-ally.md`:
--
--   1. allies.segment — the segment becomes a property of the ally
--      (B2B "Aliado Comercial" / B2P "Aliado Profesional"); until now it was
--      derived from the ally's types and could be mixed. Backfilled from the
--      types: an ally whose every active type is B2P becomes B2P, any other
--      ally B2B. Allies with types of both segments are listed in a NOTICE
--      so they can be reviewed by hand.
--   2. allies.person_id — the ally's holder person: the professional (B2P,
--      required by the service) or the representative who signs the
--      agreement (B2B). Nullable at the DB level so existing B2P allies keep
--      working until their person is loaded.
--   3. B2P practice-site fields (Convenio Profesional §III): host clinic
--      (an ally or free text), office number, appointment phone, assistant.
--   4. B2B fields (Convenio Comercial §I–IV): commercial zone, trade name,
--      founding year, cash registers, branches, POS, representative position.
--   5. representative_name / representative_document_* are deprecated in
--      favour of person_id; they are dropped by a later migration once their
--      data has been moved to persons.
-- ====================================================

ALTER TABLE allies
    ADD COLUMN segment VARCHAR(3) NOT NULL DEFAULT 'B2B'
        CONSTRAINT chk_allies_segment CHECK (segment IN ('B2B', 'B2P')),
    ADD COLUMN person_id BIGINT REFERENCES persons (persons_id),
    ADD COLUMN host_ally_id BIGINT REFERENCES allies (allies_id),
    ADD COLUMN host_name VARCHAR(200),
    ADD COLUMN office_number VARCHAR(20),
    ADD COLUMN appointment_phone VARCHAR(30),
    ADD COLUMN assistant_name VARCHAR(150),
    ADD COLUMN commercial_zone VARCHAR(100),
    ADD COLUMN trade_name VARCHAR(200),
    ADD COLUMN founded_year SMALLINT
        CONSTRAINT chk_allies_founded_year CHECK (founded_year IS NULL OR founded_year >= 1800),
    ADD COLUMN cash_registers_count INT
        CONSTRAINT chk_allies_cash_registers_count CHECK (cash_registers_count IS NULL OR cash_registers_count >= 0),
    ADD COLUMN branches_count INT
        CONSTRAINT chk_allies_branches_count CHECK (branches_count IS NULL OR branches_count >= 0),
    ADD COLUMN has_pos BOOLEAN,
    ADD COLUMN representative_position VARCHAR(100),
    ADD CONSTRAINT chk_allies_host_not_self CHECK (host_ally_id IS NULL OR host_ally_id <> allies_id);

CREATE INDEX idx_allies_segment ON allies (segment);
CREATE INDEX idx_allies_person_id ON allies (person_id) WHERE person_id IS NOT NULL;
CREATE INDEX idx_allies_host_ally_id ON allies (host_ally_id) WHERE host_ally_id IS NOT NULL;

-- Backfill: B2P only when every active type of the ally is B2P.
UPDATE allies a
SET segment = 'B2P'
WHERE EXISTS (
          SELECT 1 FROM ally_ally_types aat
          JOIN ally_types t ON t.ally_types_id = aat.ally_type_id
          WHERE aat.ally_id = a.allies_id AND aat.is_active AND t.segment = 'B2P')
  AND NOT EXISTS (
          SELECT 1 FROM ally_ally_types aat
          JOIN ally_types t ON t.ally_types_id = aat.ally_type_id
          WHERE aat.ally_id = a.allies_id AND aat.is_active AND t.segment = 'B2B');

DO $$
DECLARE
    mixed RECORD;
BEGIN
    FOR mixed IN
        SELECT a.allies_id, a.ally_number, a.name
        FROM allies a
        WHERE EXISTS (
                  SELECT 1 FROM ally_ally_types aat
                  JOIN ally_types t ON t.ally_types_id = aat.ally_type_id
                  WHERE aat.ally_id = a.allies_id AND aat.is_active AND t.segment = 'B2P')
          AND EXISTS (
                  SELECT 1 FROM ally_ally_types aat
                  JOIN ally_types t ON t.ally_types_id = aat.ally_type_id
                  WHERE aat.ally_id = a.allies_id AND aat.is_active AND t.segment = 'B2B')
    LOOP
        RAISE NOTICE 'V209: ally % (Nº %, "%") has B2B and B2P types; segment set to B2B — review by hand',
            mixed.allies_id, mixed.ally_number, mixed.name;
    END LOOP;
END $$;

COMMENT ON COLUMN allies.segment IS 'B2B Aliado Comercial / B2P Aliado Profesional (hub ADR 0031); its types must belong to this segment';
COMMENT ON COLUMN allies.person_id IS 'Holder person: the professional (B2P) or the representative who signs the agreement (B2B)';
COMMENT ON COLUMN allies.host_ally_id IS 'B2P: clinic where the professional practices, when that clinic is an ally too';
COMMENT ON COLUMN allies.host_name IS 'B2P: clinic name when it is not an ally';
COMMENT ON COLUMN allies.office_number IS 'B2P: office (consultorio) number';
COMMENT ON COLUMN allies.appointment_phone IS 'B2P: phone for appointments';
COMMENT ON COLUMN allies.assistant_name IS 'B2P: assistant name';
COMMENT ON COLUMN allies.commercial_zone IS 'B2B: commercial region / zone';
COMMENT ON COLUMN allies.trade_name IS 'B2B: trade name (nombre comercial); name is the legal name';
COMMENT ON COLUMN allies.founded_year IS 'B2B: founding year';
COMMENT ON COLUMN allies.cash_registers_count IS 'B2B: number of cash registers';
COMMENT ON COLUMN allies.branches_count IS 'B2B: number of branches';
COMMENT ON COLUMN allies.has_pos IS 'B2B: whether it runs a POS system';
COMMENT ON COLUMN allies.representative_position IS 'B2B: position (cargo) of the representative who signs';
COMMENT ON COLUMN allies.representative_name IS 'DEPRECATED (V209): use person_id';
COMMENT ON COLUMN allies.representative_document_type IS 'DEPRECATED (V209): use person_id';
COMMENT ON COLUMN allies.representative_document_number IS 'DEPRECATED (V209): use person_id';
