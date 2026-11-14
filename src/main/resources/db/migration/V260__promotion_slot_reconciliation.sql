SET search_path TO app, public;

-- ============================================================================
-- V260: reconcile campaign slots with their payments (hub ADR 0038 §9).
--
-- promotion_redemptions:
--   manual_note               why an admin confirmed or released the slot by
--   manual_by_user_id         hand (PROMOTION_SLOT_CONFIRM / _RELEASE), and who.
--                             Both NULL for a slot moved by the system.
--   expiry_notice_sent_at     when the member and their advisor were told the
--                             PENDING slot expires within 48 h (sent once).
--   last_reminded_at          last internal reminder about the payment that
--   last_reminded_by_user_id  confirms the slot (campaign "Pagos" section); at
--                             most one per slot per hour.
--
-- Rules that need no column (computed from the payments):
--   - a PENDING slot whose campaign confirms on the approved payment does not
--     expire while a qualifying payment registered before its deadline is in
--     review ("frozen"); its approval confirms it even after the deadline and
--     its rejection lets the deadline run again (or expires the slot at once);
--   - the hourly CAMPAIGN_LIFECYCLE job confirms the PENDING slots that
--     already have their payment before it expires the rest.
--
-- Permissions (CAMPAIGN domain, SYSTEM + ADMINISTRADOR):
--   PROMOTION_SLOT_CONFIRM / PROMOTION_SLOT_RELEASE  manual confirm / release,
--   CAMPAIGN_PAYMENT_VIEW / CAMPAIGN_PAYMENT_REMIND  campaign "Pagos" section.
-- ============================================================================

-- ─── 1. promotion_redemptions: manual action, notices, reminders ───────────
ALTER TABLE promotion_redemptions
    ADD COLUMN manual_note              TEXT,
    ADD COLUMN manual_by_user_id        BIGINT REFERENCES users (users_id),
    ADD COLUMN expiry_notice_sent_at    TIMESTAMPTZ,
    ADD COLUMN last_reminded_at         TIMESTAMPTZ,
    ADD COLUMN last_reminded_by_user_id BIGINT REFERENCES users (users_id);

ALTER TABLE promotion_redemptions
    ADD CONSTRAINT chk_promotion_redemptions_manual
        CHECK (manual_by_user_id IS NULL OR (manual_note IS NOT NULL AND btrim(manual_note) <> ''));

CREATE INDEX idx_promotion_redemptions_pending_notice
    ON promotion_redemptions (redemption_status, expiry_notice_sent_at)
    WHERE redemption_status = 'PENDING';
CREATE INDEX idx_promotion_redemptions_confirmed_by_payment
    ON promotion_redemptions (confirmed_by_payment_id)
    WHERE confirmed_by_payment_id IS NOT NULL;

COMMENT ON COLUMN promotion_redemptions.manual_note IS 'Nota obligatoria de una confirmación o liberación manual del cupo';
COMMENT ON COLUMN promotion_redemptions.manual_by_user_id IS 'Usuario que confirmó o liberó el cupo a mano';
COMMENT ON COLUMN promotion_redemptions.expiry_notice_sent_at IS 'Cuándo se avisó al afiliado y a su asesor que el cupo pendiente vence en 48 horas';
COMMENT ON COLUMN promotion_redemptions.last_reminded_at IS 'Último recordatorio interno sobre el pago que confirma el cupo (máximo uno por hora)';
COMMENT ON COLUMN promotion_redemptions.last_reminded_by_user_id IS 'Usuario que envió el último recordatorio interno';

-- ─── 2. audit: manual confirm / release and reminders ──────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('promotion_redemption', 'Cupo de campaña', 'promotion_redemptions')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 3. permissions (CAMPAIGN domain) ──────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTION_SLOT_CONFIRM',  'Confirmar a mano un cupo pendiente de una campaña, con nota'),
    ('PROMOTION_SLOT_RELEASE',  'Liberar a mano un cupo de una campaña que no se ha usado, con nota'),
    ('CAMPAIGN_PAYMENT_VIEW',   'Ver la sección Pagos de una campaña: qué pagos confirmaron su cupo'),
    ('CAMPAIGN_PAYMENT_REMIND', 'Enviar recordatorios internos sobre los pagos que confirman cupos de una campaña')
) AS v(name, description)
CROSS JOIN permission_domains d
WHERE d.code = 'CAMPAIGN'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('PROMOTION_SLOT_CONFIRM', 'PROMOTION_SLOT_RELEASE',
                 'CAMPAIGN_PAYMENT_VIEW', 'CAMPAIGN_PAYMENT_REMIND')
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE scheduled_jobs
   SET description = 'Cierra las campañas cuya fecha de fin ya pasó, confirma los cupos pendientes que ya tienen su pago, vence los pendientes sin confirmar (salvo los que tienen un pago en revisión), avisa 48 horas antes del vencimiento, termina las promociones cuya ventana de uso acabó y vence los vales de aliados sin canjear.'
 WHERE code = 'CAMPAIGN_LIFECYCLE';

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions
         WHERE name IN ('PROMOTION_SLOT_CONFIRM', 'PROMOTION_SLOT_RELEASE',
                        'CAMPAIGN_PAYMENT_VIEW', 'CAMPAIGN_PAYMENT_REMIND')) < 4 THEN
        RAISE EXCEPTION 'V260: PROMOTION_SLOT_* / CAMPAIGN_PAYMENT_* permissions were not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM entity_config WHERE entity_key = 'promotion_redemption') THEN
        RAISE EXCEPTION 'V260: promotion_redemption audit entity was not registered';
    END IF;
END $$;
