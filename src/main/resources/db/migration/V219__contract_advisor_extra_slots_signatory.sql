-- ====================================================
-- V219__contract_advisor_extra_slots_signatory.sql
--
-- Fills the contract fields V217 left out:
--
-- 1. organizations gains the company's authorized signatory
--    ({{org.signatoryName}}, {{org.signatoryDocument}} = type-number,
--    {{org.signatoryPosition}}), edited in the organization admin page.
--
-- 2. The two V217 versions are corrected IN PLACE (decision of the product
--    owner: no new version, so nobody has to re-accept — an acceptance keeps
--    its own rendered text and sha256):
--      - AFILIADO front block: advisor code and name, extra slots (active?
--        and additional amount paid);
--      - ASESOR signature block: the company signer's name, C.I. and position.
--    Only the front and signature blocks change; the clauses are untouched.
--    Each row is identified like V217 inserted it (type, unscoped, title)
--    and must be exactly one, or the migration aborts.
-- ====================================================

SET search_path TO app, public;

-- ─── 1. organizations: authorized signatory ─────────────────────────────────
ALTER TABLE organizations
    ADD COLUMN signatory_name            VARCHAR(200),
    ADD COLUMN signatory_document_type   VARCHAR(1),
    ADD COLUMN signatory_document_number VARCHAR(20),
    ADD COLUMN signatory_position        VARCHAR(100),
    ADD CONSTRAINT chk_organizations_signatory_document_type
        CHECK (signatory_document_type IS NULL OR signatory_document_type IN ('J', 'V', 'E', 'G', 'P')),
    ADD CONSTRAINT chk_organizations_signatory_document CHECK (
        (signatory_document_type IS NULL AND signatory_document_number IS NULL) OR
        (signatory_document_type IS NOT NULL AND signatory_document_number IS NOT NULL)
    );

COMMENT ON COLUMN organizations.signatory_name IS 'Nombre del firmante autorizado de la empresa ({{org.signatoryName}})';
COMMENT ON COLUMN organizations.signatory_document_type IS 'Tipo de documento del firmante: J, V, E, G o P';
COMMENT ON COLUMN organizations.signatory_document_number IS 'Número de documento del firmante; con el tipo forma {{org.signatoryDocument}}';
COMMENT ON COLUMN organizations.signatory_position IS 'Cargo del firmante autorizado ({{org.signatoryPosition}})';

-- ─── 2. terms_versions: front and signature blocks of the V217 texts ────────
DO $fix$
DECLARE
    v_id      BIGINT;
    v_content TEXT;
    v_count   INT;
BEGIN
    -- AFILIADO: advisor line after the activation date, extra slots after the beneficiaries.
    SELECT COUNT(*) INTO v_count
    FROM terms_versions
    WHERE term_type = 'AFILIADO' AND ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL
      AND title = 'Acuerdo de Membresía y Blindaje Familiar'
      AND content_markdown LIKE '%{{membership.activationFeeLabel}}%'
      AND content_markdown LIKE '%**I. REGISTRO OPERATIVO**%';
    IF v_count <> 1 THEN
        RAISE EXCEPTION 'V219: expected exactly one V217 AFILIADO version, found %', v_count;
    END IF;

    SELECT terms_versions_id, content_markdown INTO v_id, v_content
    FROM terms_versions
    WHERE term_type = 'AFILIADO' AND ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL
      AND title = 'Acuerdo de Membresía y Blindaje Familiar'
      AND content_markdown LIKE '%{{membership.activationFeeLabel}}%'
      AND content_markdown LIKE '%**I. REGISTRO OPERATIVO**%';

    IF v_content NOT LIKE '%{{membership.advisorCode}}%' THEN
        v_content := replace(v_content,
            $a$- **Fecha de activación:** {{membership.activatedAt}}$a$,
            $a$- **Fecha de activación:** {{membership.activatedAt}}
- **Código de Asesor (ID):** {{membership.advisorCode}} · {{membership.advisorFullName}}$a$);
        v_content := replace(v_content,
            $b$- **Beneficiarios registrados:** {{beneficiaries.count}}$b$,
            $b$- **Beneficiarios registrados:** {{beneficiaries.count}}
- **¿Activa cupos extra?:** {{membership.hasExtraSlots}}
- **Monto adicional pagado:** {{membership.extraSlotsAmount}}$b$);
        IF v_content NOT LIKE '%{{membership.advisorCode}}%' OR v_content NOT LIKE '%{{membership.extraSlotsAmount}}%' THEN
            RAISE EXCEPTION 'V219: the V217 AFILIADO front block was not found as expected';
        END IF;
        UPDATE terms_versions SET content_markdown = v_content WHERE terms_versions_id = v_id;
    END IF;

    -- ASESOR: the company signer line.
    SELECT COUNT(*) INTO v_count
    FROM terms_versions
    WHERE term_type = 'ASESOR' AND ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL
      AND title = 'Contrato de Mandato y Representación Comercial Independiente'
      AND content_markdown LIKE '%Gerente autorizado (nombre, C.I. y firma)%';
    IF v_count > 1 THEN
        RAISE EXCEPTION 'V219: expected at most one V217 ASESOR version, found %', v_count;
    END IF;
    IF v_count = 0 AND NOT EXISTS (
        SELECT 1 FROM terms_versions
        WHERE term_type = 'ASESOR' AND ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL
          AND title = 'Contrato de Mandato y Representación Comercial Independiente'
          AND content_markdown LIKE '%{{org.signatoryName}}%') THEN
        RAISE EXCEPTION 'V219: the V217 ASESOR version was not found';
    END IF;
    UPDATE terms_versions
    SET content_markdown = replace(content_markdown,
            $c$**Por {{brand.name}}:** Gerente autorizado (nombre, C.I. y firma)$c$,
            $c$**Por {{brand.name}}:** {{org.signatoryName}} · C.I. {{org.signatoryDocument}} · {{org.signatoryPosition}}$c$)
    WHERE term_type = 'ASESOR' AND ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL
      AND title = 'Contrato de Mandato y Representación Comercial Independiente'
      AND content_markdown LIKE '%Gerente autorizado (nombre, C.I. y firma)%';
END
$fix$;
