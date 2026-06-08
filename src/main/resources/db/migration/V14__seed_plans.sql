SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V14: seed the three commercial SKUs from the OPTIBIENESTAR 360 flyer
-- (Centro Óptico Vicente, jun 2026).
--
-- Pricing per flyer:
--     Individual   $10 inscription / $5 monthly
--     Familiar     $20 inscription / $5 monthly — includes 3 beneficiaries,
--                  cap at 5, $5 inscription per extra beneficiary
--     Corporativo  $5 per person — exact model TBD with client (stays
--                  unpublished until confirmed)
--
-- Publishing:
--     Individual + Familiar → is_published = TRUE: pricing solid per the
--       flyer, the public landing renders them on fresh deploy.
--     Corporativo           → is_published = FALSE: pricing TBD, hide it
--       from the public directory until the client confirms.
--
-- The `code` column is the natural lookup key. Services / future code that
-- needs "the Individual plan" should query by code = 'INDIVIDUAL', NOT by
-- UUID — UUIDs are generated per-row and differ across fresh deploys; code
-- is the stable contract.
--
-- User-facing text (name + description) is in Spanish per ADR 0009 — the
-- application's primary locale is es-VE.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO plans (
    code, name, description, type,
    inscription_fee, monthly_fee,
    included_beneficiaries, max_beneficiaries, extra_beneficiary_inscription_fee,
    grace_period_days,
    is_published, published_at
)
VALUES
    (
        'INDIVIDUAL',
        'Plan Individual',
        'Cobertura individual para una persona. Incluye consultas oftalmológicas, monturas de lentes en clínicas seleccionadas, consultas de medicina general y descuentos en farmacias y servicios aliados.',
        'INDIVIDUAL',
        10.00, 5.00,
        0,       -- no included beneficiaries
        0,       -- cap 0 — Individual does not allow extras
        NULL,    -- consequently no extra fee
        7,
        TRUE, NOW()
    ),
    (
        'FAMILIAR',
        'Plan Familiar',
        'Cobertura familiar — titular + hasta 3 beneficiarios incluidos sin costo extra. Permite hasta 5 beneficiarios en total; cada adicional paga $5 USD de inscripción una sola vez. Mensualidad fija independiente del número de beneficiarios.',
        'FAMILIAR',
        20.00, 5.00,
        3,       -- 3 included beneficiaries
        5,       -- cap 5 — TBD with client; flyer does not specify (vertical-5 TBD)
        5.00,    -- $5 per extra beneficiary (flyer "Afiliado Adicional: $5")
        7,
        TRUE, NOW()
    ),
    (
        'CORPORATIVO',
        'Plan Corporativo',
        'Plan Corporativo — pricing por persona. PRICING TBD con cliente (ver vertical-5 v2). Modelo: cobertura masiva para empresas, escuelas y sindicatos. La membresía se administra vía un contrato corporativo (corporate_contracts, planeado v2).',
        'CORPORATIVO',
        5.00, 5.00,    -- placeholders per person; may change with client confirmation
        0,             -- no "included beneficiaries" concept — each person on the contract is a member
        NULL,          -- no cap (scales via the contract)
        5.00,          -- if a corporate member later adds beneficiaries, $5 default
        7,
        FALSE, NULL    -- unpublished until pricing confirmed
    );
