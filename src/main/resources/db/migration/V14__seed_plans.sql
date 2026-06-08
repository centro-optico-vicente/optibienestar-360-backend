SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V14: seed the three commercial SKUs from the OPTIBIENESTAR 360 flyer
-- (Centro Óptico Vicente, jun 2026).
--
-- Pricing per flyer:
--     Individual    $10 inscripción / $5 mensualidad
--     Familiar      $20 inscripción / $5 mensualidad — incluye 3 beneficiarios,
--                   tope 5, $5 por beneficiario extra
--     Corporativo   $5/persona — modelo exacto TBD con cliente (queda
--                   unpublished hasta confirmar)
--
-- Publishing:
--     Individual + Familiar → is_published = TRUE: pricing solid per flyer,
--       el frontend público los muestra inmediatamente.
--     Corporativo           → is_published = FALSE: pricing TBD, no debe
--       aparecer en el directorio público hasta cierre con cliente.
--
-- The `code` column is the natural lookup key. Services / future code that
-- needs "the Individual plan" should query by code = 'INDIVIDUAL', NOT by
-- UUID — UUIDs are generated per-row and can differ across fresh deploys;
-- code is the stable contract.
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
        0,       -- sin beneficiarios incluidos
        0,       -- tope 0 — Individual no admite beneficiarios extra
        NULL,    -- por lo tanto no aplica fee extra
        7,
        TRUE, NOW()
    ),
    (
        'FAMILIAR',
        'Plan Familiar',
        'Cobertura familiar — titular + hasta 3 beneficiarios incluidos sin costo extra. Permite hasta 5 beneficiarios en total; cada adicional paga $5 USD de inscripción una sola vez. Mensualidad fija independiente del número de beneficiarios.',
        'FAMILIAR',
        20.00, 5.00,
        3,       -- 3 beneficiarios incluidos
        5,       -- tope 5 (TBD a confirmar con cliente — flyer no lo aclara, vertical-5 TBD)
        5.00,    -- $5 por beneficiario extra (flyer "Afiliado Adicional: $5")
        7,
        TRUE, NOW()
    ),
    (
        'CORPORATIVO',
        'Plan Corporativo — cobertura para empresas, escuelas y sindicatos. Pricing definitivo por confirmar con el cliente (ver vertical-5 TBDs). Por persona, sin tope de miembros; el contrato administra membresías masivas.',
        -- Description deliberately calls out the TBD so the admin sees it
        -- if they preview before the official launch.
        'Plan Corporativo — pricing por persona. PRICING TBD con cliente (vertical-5 v2). Modelo: cobertura masiva para empresas, escuelas, sindicatos. La membresía se administra vía un contrato corporativo (corporate_contracts, planeado v2).',
        'CORPORATIVO',
        5.00, 5.00,    -- placeholders por persona; pueden cambiar
        0,             -- no aplica el concepto "beneficiarios incluidos" — cada persona del contrato es member
        NULL,          -- sin tope (escala vía contrato)
        5.00,          -- si en el futuro un member corporativo agrega beneficiarios, $5 default
        7,
        FALSE, NULL    -- unpublished hasta confirmar pricing
    );
