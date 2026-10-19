SET search_path TO app, public;

-- ============================================================================
-- V185: PROMOTOR → ASESOR (business glossary: "Asesor", never "promotor").
--
-- Database values stay in Spanish; only the ones that break the glossary
-- change. Code now refers to them through English identifiers
-- (RoleNames.ADVISOR, TermType.ADVISOR, AdvisorRankCodes.ADVISOR), whose
-- values are the codes below.
--
-- 1. roles.name            'PROMOTOR' → 'ASESOR'. user_roles and
--                          role_permissions reference the role by FK, so every
--                          advisor keeps the role and its permissions.
-- 2. terms_versions.term_type 'PROMOTOR' → 'ASESOR', around a drop/recreate of
--                          chk_terms_versions_term_type (V139). The V183 scope
--                          CHECK only names 'ALIADO' and is left as is.
-- 3. promoter_ranks.code   'PROMOTOR' → 'ASESOR' (and name 'Promotor' → 'Asesor').
-- 4. Seeded user-visible texts that say "promotor": permission descriptions,
--    permission domain names/descriptions, rank descriptions, audit entity
--    labels, scheduled job names/descriptions, payment category descriptions
--    and the INSTITUCION system advisor's description. Each row is rewritten
--    only while it still holds the exact seed text from its original
--    migration, so a text an admin already edited is never overwritten.
--
-- Not touched on purpose:
--   * login_audit_log.roles (JSONB): historical record of the role names a
--     session had at login time; old rows keep saying PROMOTOR.
--   * Tables, columns, entities and endpoints named promoter/promoters
--     (internal identifiers, out of scope).
--   * Report views with the literal 'Directo / Sin Promotor' (V97/V117/V122/
--     V133): changing them means recreating the views; left for a follow-up.
-- ============================================================================


-- ─── 1. roles ───────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM roles WHERE name = 'ASESOR')
       AND EXISTS (SELECT 1 FROM roles WHERE name = 'PROMOTOR') THEN
        RAISE EXCEPTION 'V185: both PROMOTOR and ASESOR roles exist — merge them by hand before migrating';
    END IF;
END $$;

UPDATE roles SET name = 'ASESOR' WHERE name = 'PROMOTOR';


-- ─── 2. terms_versions.term_type ────────────────────────────────────────────
ALTER TABLE terms_versions DROP CONSTRAINT chk_terms_versions_term_type;

UPDATE terms_versions SET term_type = 'ASESOR' WHERE term_type = 'PROMOTOR';

ALTER TABLE terms_versions
    ADD CONSTRAINT chk_terms_versions_term_type CHECK (term_type IN ('AFILIADO', 'ASESOR', 'ALIADO'));


-- ─── 3. promoter_ranks ──────────────────────────────────────────────────────
UPDATE promoter_ranks SET code = 'ASESOR' WHERE code = 'PROMOTOR';

UPDATE promoter_ranks SET name = 'Asesor' WHERE code = 'ASESOR' AND name = 'Promotor';

UPDATE promoter_ranks SET description = 'Supervisa hasta 10 asesores'
WHERE code = 'SUPERVISOR' AND description = 'Supervisa hasta 10 promotores/asesores';


-- ─── 4. seeded user-visible texts (only rows still equal to the seed) ───────

-- permissions.description (V6, V35, V37, V42, V43, V66, V71, V73, V78, V92,
-- V101, V104, V109, V111, V121). Matched by description, not by name, so the
-- V77 permission renames don't matter.
UPDATE permissions p
SET description = v.new_text
FROM (VALUES
    ('Registrar promotores',                                                      'Registrar asesores'),
    ('Actualizar datos del promotor',                                             'Actualizar datos del asesor'),
    ('Desactivar promotores',                                                     'Desactivar asesores'),
    ('Ver todos los promotores',                                                  'Ver todos los asesores'),
    ('Reasignar el promotor de un afiliado (enlace permanente)',                  'Reasignar el asesor de un afiliado (enlace permanente)'),
    ('Ver el panel propio del promotor (cartera y comisiones)',                   'Ver el panel propio del asesor (cartera y comisiones)'),
    ('Ver todos los premios/bonos otorgados a promotores',                        'Ver todos los premios/bonos otorgados a asesores'),
    ('Ver los bonos propios del promotor',                                        'Ver los bonos propios del asesor'),
    ('Ver el ranking de promotores',                                              'Ver el ranking de asesores'),
    ('Crear, editar y eliminar tipos de promotor',                                'Crear, editar y eliminar tipos de asesor'),
    ('Ver el historial de cambios de un promotor',                                'Ver el historial de cambios de un asesor'),
    ('Restaurar un promotor a un punto de su historial',                          'Restaurar un asesor a un punto de su historial'),
    ('Generar reportes de promotores',                                            'Generar reportes de asesores'),
    ('Ver el historial de reportes generados de promotores',                      'Ver el historial de reportes generados de asesores'),
    ('Ver el historial de cambios de un tipo de promotor',                        'Ver el historial de cambios de un tipo de asesor'),
    ('Ver el historial de reportes generados de tipos de promotor',               'Ver el historial de reportes generados de tipos de asesor'),
    ('Ver tipos de promotor',                                                     'Ver tipos de asesor'),
    ('Crear tipos de promotor',                                                   'Crear tipos de asesor'),
    ('Editar tipos de promotor',                                                  'Editar tipos de asesor'),
    ('Eliminar tipos de promotor',                                                'Eliminar tipos de asesor'),
    ('Marcar un bono/premio de promotor otorgado como pagado',                    'Marcar un bono/premio de asesor otorgado como pagado'),
    ('Ver los cargos/rangos jerárquicos de promotor',                             'Ver los cargos/rangos jerárquicos de asesor'),
    ('Crear cargos/rangos jerárquicos de promotor',                               'Crear cargos/rangos jerárquicos de asesor'),
    ('Editar cargos/rangos jerárquicos de promotor',                              'Editar cargos/rangos jerárquicos de asesor'),
    ('Desactivar cargos/rangos jerárquicos de promotor',                          'Desactivar cargos/rangos jerárquicos de asesor'),
    ('Asignar/reasignar el supervisor de un promotor',                            'Asignar/reasignar el supervisor de un asesor'),
    ('Ascender o degradar el cargo jerárquico de un promotor',                    'Ascender o degradar el cargo jerárquico de un asesor'),
    ('Ver el historial de cambios de un cargo jerárquico de promotor',            'Ver el historial de cambios de un cargo jerárquico de asesor'),
    ('Ver el historial de reportes generados de cargos jerárquicos de promotor',  'Ver el historial de reportes generados de cargos jerárquicos de asesor'),
    ('Reordenar/insertar cargos jerárquicos de promotor',                         'Reordenar/insertar cargos jerárquicos de asesor'),
    ('Registrar un cobro para un afiliado de la cartera del promotor',            'Registrar un cobro para un afiliado de la cartera del asesor'),
    ('Aprobar un cobro pendiente de la cartera del promotor',                     'Aprobar un cobro pendiente de la cartera del asesor'),
    ('Rechazar un cobro pendiente de la cartera del promotor',                    'Rechazar un cobro pendiente de la cartera del asesor'),
    ('Eliminar un cobro pendiente de la cartera del promotor',                    'Eliminar un cobro pendiente de la cartera del asesor')
) AS v(seed_text, new_text)
WHERE p.description = v.seed_text;

-- permission_domains (V5, V76): visible name and description.
UPDATE permission_domains SET name = 'Asesores'
WHERE code = 'PROMOTERS' AND name = 'Promotores';

UPDATE permission_domains SET description = 'Asesores y red de ventas'
WHERE code = 'PROMOTERS' AND description = 'Promotores y red de ventas';

UPDATE permission_domains SET description = 'Comisiones de asesores y ciclos de pago'
WHERE code = 'COMMISSIONS' AND description = 'Comisiones de promotores y ciclos de pago';

UPDATE permission_domains SET name = 'Tipos de asesor'
WHERE code = 'CATALOG_PROMOTER_TYPE' AND name = 'Tipos de promotor';

UPDATE permission_domains SET description = 'Catálogo de tipos de asesor'
WHERE code = 'CATALOG_PROMOTER_TYPE' AND description = 'Catálogo de tipos de promotor';

-- entity_config.display_name (V60, V101, V102): audit/report entity labels.
UPDATE entity_config ec
SET display_name = v.new_text
FROM (VALUES
    ('promoter',                    'Promotores',                         'Asesores'),
    ('member_promoter',             'Asignación afiliado-promotor',       'Asignación afiliado-asesor'),
    ('promoter_rank',               'Cargos jerárquicos de promotor',     'Cargos jerárquicos de asesor'),
    ('promoter_hierarchy_override', 'Overrides jerárquicos de promotor',  'Overrides jerárquicos de asesor')
) AS v(entity_key, seed_text, new_text)
WHERE ec.entity_key = v.entity_key
  AND ec.display_name = v.seed_text;

-- scheduled_jobs (V37, V42, V151, V152): name and description shown in the
-- admin scheduler screen.
UPDATE scheduled_jobs SET display_name = 'Evaluación mensual de bonos de asesores'
WHERE code = 'BONUS_EVALUATION' AND display_name = 'Evaluación mensual de bonos de promotores';

UPDATE scheduled_jobs
SET description = 'Recorre las reglas de bono activas y otorga premios a los asesores que alcanzan las metas de suscriptores (nuevos o activos) del período. Idempotente: solo otorga los bloques nuevos no premiados aún.'
WHERE code = 'BONUS_EVALUATION'
  AND description = 'Recorre las reglas de bono activas y otorga premios a los promotores que alcanzan las metas de suscriptores (nuevos o activos) del período. Idempotente: solo otorga los bloques nuevos no premiados aún.';

UPDATE scheduled_jobs
SET description = 'Otorga premios al top del ranking de asesores al cierre del período'
WHERE code = 'LEADERBOARD_PRIZE_AWARD'
  AND description = 'Otorga premios al top del ranking de promotores al cierre del período';

UPDATE scheduled_jobs
SET description = 'Recorre las reglas activas de commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo INSCRIPTION de cada asesor con actividad en el corte. Reutiliza CommissionPeriodicSettlementService.settleCut por asesor.'
WHERE code = 'COMMISSION_TIER_SETTLEMENT_CUT'
  AND description = 'Recorre las reglas activas de commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo INSCRIPTION de cada promotor con actividad en el corte. Reutiliza CommissionPeriodicSettlementService.settleCut por promotor.';

UPDATE scheduled_jobs
SET description = 'Recorre las reglas activas de collection_commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo MONTHLY de cada asesor con actividad en el corte. No re-precia: la comisión de cobranza se fija una sola vez por transacción al crearse (CommissionService.priceByCollectionSpeed), así que este corte es una transición de estado pura. Reutiliza CommissionPeriodicSettlementService.settleCollectionCut por asesor.'
WHERE code = 'COLLECTION_COMMISSION_TIER_SETTLEMENT_CUT'
  AND description = 'Recorre las reglas activas de collection_commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo MONTHLY de cada promotor con actividad en el corte. No re-precia: la comisión de cobranza se fija una sola vez por transacción al crearse (CommissionService.priceByCollectionSpeed), así que este corte es una transición de estado pura. Reutiliza CommissionPeriodicSettlementService.settleCollectionCut por promotor.';

-- payment_categories.description (V115).
UPDATE payment_categories
SET description = 'Pago de la comisión directa que gana un asesor por la venta o renovación de sus afiliados.'
WHERE code = 'COMMISSION_REGULAR'
  AND description = 'Pago de la comisión directa que gana un promotor por la venta o renovación de sus afiliados.';

UPDATE payment_categories
SET description = 'Pago de la comisión que un asesor de rango superior recibe sobre las ventas generadas por los asesores de los niveles inferiores de su red.'
WHERE code = 'HIERARCHY_OVERRIDE'
  AND description = 'Pago de la comisión que un promotor de rango superior recibe sobre las ventas generadas por los promotores de los niveles inferiores de su red.';

-- promoters.description of the INSTITUCION system advisor (V25).
UPDATE promoters
SET description = 'Asesor del sistema. Atribuye al área de administración central las afiliaciones registradas por taquilla o sin código de referido. No participa en el ranking público de asesores; sus comisiones se contabilizan a favor de la institución y no se pagan a una persona física.'
WHERE referral_code = 'INSTITUCION'
  AND is_system = TRUE
  AND description = 'Promotor del sistema. Atribuye al área de administración central las afiliaciones registradas por taquilla o sin código de referido. No participa en el ranking público de promotores; sus comisiones se contabilizan a favor de la institución y no se pagan a una persona física.';


-- ─── 5. Fail loudly rather than leave a half-renamed state ──────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM roles WHERE name = 'PROMOTOR') THEN
        RAISE EXCEPTION 'V185: role PROMOTOR still exists';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ASESOR') THEN
        RAISE EXCEPTION 'V185: role ASESOR is missing';
    END IF;

    IF EXISTS (SELECT 1 FROM terms_versions WHERE term_type = 'PROMOTOR') THEN
        RAISE EXCEPTION 'V185: terms_versions still has term_type PROMOTOR';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_terms_versions_term_type'
          AND pg_get_constraintdef(oid) LIKE '%ASESOR%'
          AND pg_get_constraintdef(oid) NOT LIKE '%PROMOTOR%'
    ) THEN
        RAISE EXCEPTION 'V185: chk_terms_versions_term_type was not recreated with ASESOR';
    END IF;

    IF EXISTS (SELECT 1 FROM promoter_ranks WHERE code = 'PROMOTOR') THEN
        RAISE EXCEPTION 'V185: promoter_ranks still has code PROMOTOR';
    END IF;
END $$;
