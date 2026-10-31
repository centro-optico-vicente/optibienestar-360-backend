SET search_path TO app, public;

-- ============================================================================
-- V203: the public self-affiliation form may also report the payment of the
-- plan's INSCRIPTION fee, with its proof file.
--
-- The payment data is stored on the request as reported (method, bank,
-- reference, date, payer fields) together with the amount/currency the server
-- took from the selected plan (inscription_fee + its currency — never sent by
-- the client) and the proof file metadata (the object is uploaded to R2 under
-- the affiliation_requests owner key when storage is enabled).
--
-- On approval the inscription payment is registered on the new membership
-- through the normal payments flow (PENDING review, or auto-approved for a
-- direct-receipt method), the proof file is linked to it, and payment_id points
-- at that payment. A request without a reported payment keeps every column NULL.
-- ============================================================================

ALTER TABLE affiliation_requests
    ADD COLUMN payment_method_id                 BIGINT        REFERENCES payment_methods (payment_methods_id),
    ADD COLUMN payment_bank_id                   BIGINT        REFERENCES banks (banks_id),
    ADD COLUMN payment_amount                    NUMERIC(10, 2),
    ADD COLUMN payment_currency_code             VARCHAR(3),
    ADD COLUMN payment_identification            VARCHAR(80),
    ADD COLUMN payment_bank_account_type         VARCHAR(40),
    ADD COLUMN payment_bank_account_code         VARCHAR(40),
    ADD COLUMN payment_bank_account_identifier   VARCHAR(120),
    ADD COLUMN payment_phone                     VARCHAR(40),
    ADD COLUMN payment_email                     VARCHAR(160),
    ADD COLUMN payment_reference_number          VARCHAR(80),
    ADD COLUMN payment_date                      TIMESTAMPTZ,
    ADD COLUMN payment_support_file_url          VARCHAR(500),
    ADD COLUMN payment_support_file_name         VARCHAR(255),
    ADD COLUMN payment_support_file_content_type VARCHAR(100),
    ADD COLUMN payment_support_file_size_bytes   BIGINT,
    ADD COLUMN payment_id                        BIGINT        REFERENCES payments (payments_id);

ALTER TABLE affiliation_requests
    ADD CONSTRAINT ck_affiliation_requests_payment_method_date
        CHECK ((payment_method_id IS NULL) = (payment_date IS NULL));

CREATE INDEX idx_affiliation_requests_payment_id ON affiliation_requests (payment_id)
    WHERE payment_id IS NOT NULL;

COMMENT ON COLUMN affiliation_requests.payment_method_id IS
    'Forma de pago de la inscripción reportada en el formulario; vacío si el solicitante no reportó pago';
COMMENT ON COLUMN affiliation_requests.payment_bank_id IS
    'Banco del pago de inscripción reportado (cuando la forma de pago lo exige)';
COMMENT ON COLUMN affiliation_requests.payment_amount IS
    'Monto del pago de inscripción: la cuota de inscripción del plan al momento de la solicitud (no lo envía el cliente)';
COMMENT ON COLUMN affiliation_requests.payment_currency_code IS
    'Moneda (ISO 4217) del pago de inscripción: la moneda del plan';
COMMENT ON COLUMN affiliation_requests.payment_identification IS
    'Identificación (cédula/RIF) del titular del pago reportado';
COMMENT ON COLUMN affiliation_requests.payment_bank_account_type IS
    'Tipo de cuenta bancaria del pago reportado';
COMMENT ON COLUMN affiliation_requests.payment_bank_account_code IS
    'Código de la cuenta bancaria del pago reportado';
COMMENT ON COLUMN affiliation_requests.payment_bank_account_identifier IS
    'Número de cuenta bancaria del pago reportado';
COMMENT ON COLUMN affiliation_requests.payment_phone IS
    'Teléfono asociado al pago reportado (p. ej. pago móvil)';
COMMENT ON COLUMN affiliation_requests.payment_email IS
    'Correo asociado al pago reportado (p. ej. Zelle)';
COMMENT ON COLUMN affiliation_requests.payment_reference_number IS
    'Número de referencia del pago reportado';
COMMENT ON COLUMN affiliation_requests.payment_date IS
    'Fecha en que el solicitante indica haber realizado el pago de inscripción';
COMMENT ON COLUMN affiliation_requests.payment_support_file_url IS
    'Clave del comprobante de pago en el almacenamiento (R2); vacía si no se adjuntó o el almacenamiento estaba deshabilitado';
COMMENT ON COLUMN affiliation_requests.payment_support_file_name IS
    'Nombre original del comprobante de pago adjuntado';
COMMENT ON COLUMN affiliation_requests.payment_support_file_content_type IS
    'Tipo MIME del comprobante de pago adjuntado';
COMMENT ON COLUMN affiliation_requests.payment_support_file_size_bytes IS
    'Tamaño en bytes del comprobante de pago adjuntado';
COMMENT ON COLUMN affiliation_requests.payment_id IS
    'Pago de inscripción creado al aprobar la solicitud a partir del pago reportado';
