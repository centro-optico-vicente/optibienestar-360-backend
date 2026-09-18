SET search_path TO app, public;

-- ============================================================================
-- V115: payment_categories + payment_methods — two independent catalogs,
-- first step of the payments/payouts unification
-- (hub plan ".ai/plans/2026-09-17-payments-unification-plan.md").
--
-- Originally this migration created a single `payment_types` table shared by
-- both concerns (a `direction` column doing double duty: IN/OUT for reason
-- rows, BOTH as a disguised "this is actually a method" marker). Split before
-- anything came to depend on it, because a category and a method are not the
-- same kind of thing and mixing them in one catalog gave up real referential
-- integrity for a naming convenience:
--
--   - payment_categories → REASON/MOTIVO: why the money moved (membership
--     fee, inscription fee, commission payout, bonus, hierarchy override,
--     retroactive top-up). `direction` (IN/OUT) is a real, single-purpose
--     fact here — the actual direction of that category's money.
--   - payment_methods → MÉTODO/FORMA DE PAGO: how the money moved (cash,
--     bank transfer, Zelle, ...). No `direction` column at all — a method
--     never belongs to one direction, so there is nothing to encode. Instead
--     carries `is_mandatory_*` flags so the UI/backend know which extra fields
--     to demand per method (bank account for transfers, phone for pago
--     móvil, email for Zelle/crypto, reference number as proof of payment)
--     — same idea as the legacy `tglo_METODO_PAGO` catalog in
--     proyecto-iv-mh/data/bd/database.sql (es_banco_obligatorio,
--     es_telefono_obligatorio, es_correo_obligatorio, etc.), adapted to
--     this schema instead of carried over field-for-field.
--
-- `payments.payment_type_id` (header, motivo) will point to
-- `payment_categories`; `payment_lines.payment_type_id` (line, método) will
-- point to `payment_methods` — each FK to its own table, so the database
-- itself rejects a category id where a method belongs and vice versa,
-- instead of relying on convention.
--
-- The old payment_method values (V23 CHECK) are preserved verbatim as method
-- codes so the backfill in a later migration is a straight 1:1 mapping.
-- ============================================================================

CREATE TABLE payment_categories
(
    payment_categories_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    code                   VARCHAR(40)  NOT NULL UNIQUE,
    name                   VARCHAR(80)  NOT NULL,
    description            VARCHAR(255),
    -- Real direction of the money for this category: IN = cobro, OUT = pago de comisión.
    direction              VARCHAR(10)  NOT NULL CHECK (direction IN ('IN', 'OUT')),

    is_active              BOOLEAN     NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_by             UUID
);

CREATE INDEX idx_payment_categories_direction ON payment_categories (direction) WHERE is_active;

CREATE TRIGGER trg_payment_categories_updated_at
    BEFORE UPDATE ON payment_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE payment_methods
(
    payment_methods_id        BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    code                       VARCHAR(40) NOT NULL UNIQUE,
    name                       VARCHAR(80) NOT NULL,

    -- Which extra fields the UI/backend must demand for a payment_lines row
    -- using this method. Kept as flags on the catalog (not hardcoded per
    -- method in code) so a new method can be added without a code change.
    is_mandatory_bank_account  BOOLEAN     NOT NULL DEFAULT FALSE,
    is_mandatory_phone         BOOLEAN     NOT NULL DEFAULT FALSE,
    is_mandatory_email         BOOLEAN     NOT NULL DEFAULT FALSE,
    is_mandatory_reference_number BOOLEAN  NOT NULL DEFAULT TRUE,

    is_active                  BOOLEAN     NOT NULL DEFAULT TRUE,
    status                     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 UUID,
    updated_by                 UUID
);

CREATE TRIGGER trg_payment_methods_updated_at
    BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ─── Seed: categorías/motivo (uso en payments, header) ──────────────────────
INSERT INTO payment_categories (uuid, code, name, description, direction)
VALUES
    (gen_random_uuid(), 'MEMBERSHIP_FEE',     'Cuota de membresía',       'Cobro periódico de la cuota de membresía del afiliado.',                                                                          'IN'),
    (gen_random_uuid(), 'INSCRIPTION_FEE',    'Cuota de inscripción',     'Cobro único al momento de inscribir/afiliar a un nuevo miembro.',                                                                 'IN'),
    (gen_random_uuid(), 'COMMISSION_REGULAR', 'Comisión directa regular', 'Pago de la comisión directa que gana un promotor por la venta o renovación de sus afiliados.',                                     'OUT'),
    (gen_random_uuid(), 'COMMISSION_BONUS',   'Bono',                     'Pago de un bono adicional a la comisión regular, según las reglas de bonificación vigentes.',                                     'OUT'),
    (gen_random_uuid(), 'HIERARCHY_OVERRIDE', 'Comisión jerárquica',      'Pago de la comisión que un promotor de rango superior recibe sobre las ventas generadas por los promotores de los niveles inferiores de su red.', 'OUT'),
    (gen_random_uuid(), 'RETROACTIVE_TOPUP',  'Ajuste retroactivo',       'Pago complementario que corrige o completa, de forma retroactiva, una comisión ya calculada previamente.',                       'OUT'),
    (gen_random_uuid(), 'CHARGE',             'Cargo',                    'Cargo adicional aplicado al afiliado: comisión bancaria, recargo por mora/retraso, o interés por atraso en el pago.',         'IN');

-- ─── Seed: métodos/forma de pago (uso en payment_lines) ────────────────────────────────────────────
-- Códigos base verbatim del CHECK viejo de payments.payment_method (V23), ampliados con los métodos
-- reales que ya maneja el negocio según el catálogo legado `tglo_METODO_PAGO`
-- (proyecto-iv-mh/data/bd/database.sql y sus backups, ej. backup-2026-01-09_01-31.sql): Efectivo, Pago
-- Móvil, Transferencia, Binance, Zelle, Zinly, Paypal, Tarjeta de Débito, Tarjeta de Crédito, Cheque,
-- Depósito Bancario. De esa lista, `Cargo` (comisión bancaria, mora, intereses) NO es un método sino un
-- motivo de cobro adicional — se agregó como categoría `CHARGE` arriba, no acá. `Nota de Crédito` sigue
-- fuera de ambos catálogos por ahora — no es una forma de pago ni un motivo de cobro sino un instrumento
-- de compensación (ver "Fuera de alcance / futuro" del plan, sección de vuelto/sobregiro), pendiente de
-- confirmar con el dueño del producto antes de modelarlo.
INSERT INTO payment_methods (uuid, code, name, is_mandatory_bank_account, is_mandatory_phone, is_mandatory_email, is_mandatory_reference_number)
VALUES
    (gen_random_uuid(), 'BANK_TRANSFER',          'Transferencia bancaria',      TRUE,  FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'CASH',                   'Efectivo',                    FALSE, FALSE, FALSE, FALSE),
    (gen_random_uuid(), 'ZELLE',                  'Zelle',                       FALSE, FALSE, TRUE,  TRUE),
    (gen_random_uuid(), 'PAGO_MOVIL',             'Pago móvil',                  FALSE, TRUE,  FALSE, TRUE),
    (gen_random_uuid(), 'CRYPTO',                 'Criptomoneda',                FALSE, FALSE, TRUE,  TRUE),
    (gen_random_uuid(), 'INTERNATIONAL_TRANSFER', 'Transferencia internacional', TRUE,  FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'ZINLY',                  'Zinly',                       FALSE, FALSE, TRUE,  TRUE),
    (gen_random_uuid(), 'PAYPAL',                 'PayPal',                      FALSE, FALSE, TRUE,  TRUE),
    (gen_random_uuid(), 'DEBIT_CARD',             'Tarjeta de débito',           FALSE, FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'CREDIT_CARD',            'Tarjeta de crédito',          FALSE, FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'CHECK',                  'Cheque',                      TRUE,  FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'BANK_DEPOSIT',           'Depósito bancario',           TRUE,  FALSE, FALSE, TRUE),
    (gen_random_uuid(), 'OTHER',                  'Otro',                        FALSE, FALSE, FALSE, FALSE)
;
