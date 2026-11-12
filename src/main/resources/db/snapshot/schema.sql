--
-- PostgreSQL database dump
--

--
-- Name: app; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA app;

--
-- Name: citext; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA app;

--
-- Name: EXTENSION citext; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION citext IS 'data type for case-insensitive character strings';

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA app;

--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA app;

--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';

--
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA app;

--
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';

--
-- Name: bcrypt_hash(text, integer); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.bcrypt_hash(plain text, strength integer DEFAULT 12) RETURNS text
    LANGUAGE sql STRICT
    SET search_path TO 'public', 'app'
    AS $$
    SELECT crypt(plain, gen_salt('bf', strength));
$$;

--
-- Name: FUNCTION bcrypt_hash(plain text, strength integer); Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON FUNCTION app.bcrypt_hash(plain text, strength integer) IS 'BCrypt hash ($2a$) compatible with Spring BCryptPasswordEncoder. Seeds/dev only: plaintext may appear in SQL logs.';

--
-- Name: enforce_exclusive_product_owner(); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.enforce_exclusive_product_owner() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    owner BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ally_products' THEN
        SELECT owner_ally_id INTO owner FROM app.products WHERE products_id = NEW.product_id;
        IF owner IS NOT NULL AND owner <> NEW.ally_id THEN
            RAISE EXCEPTION 'product % is exclusive to ally %', NEW.product_id, owner
                USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_ally_products_exclusive_owner';
        END IF;
    ELSIF NEW.owner_ally_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM app.ally_products
            WHERE product_id = NEW.products_id AND ally_id <> NEW.owner_ally_id) THEN
        RAISE EXCEPTION 'product % is offered by other allies and cannot become exclusive', NEW.products_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_products_exclusive_owner';
    END IF;
    RETURN NEW;
END;
$$;

--
-- Name: grant_permission_to_system(); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.grant_permission_to_system() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO app.role_permissions (role_id, permission_id)
    SELECT r.roles_id, NEW.permissions_id
    FROM app.roles r
    WHERE r.name = 'SYSTEM'
    ON CONFLICT (role_id, permission_id) DO NOTHING;
    RETURN NEW;
END;
$$;

--
-- Name: immutable_unaccent(text); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$ SELECT unaccent('unaccent', $1) $_$;

--
-- Name: set_membership_contract_number(); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.set_membership_contract_number() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.contract_number IS NULL THEN
        NEW.contract_number := 'OB-' || lpad(nextval('membership_contract_seq')::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$;

--
-- Name: set_updated_at(); Type: FUNCTION; Schema: app; Owner: -
--

CREATE FUNCTION app.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

--
-- Name: affiliation_request_logs; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.affiliation_request_logs (
    affiliation_request_logs_id bigint NOT NULL,
    affiliation_request_id bigint NOT NULL,
    action character varying(20) NOT NULL,
    from_status character varying(20),
    to_status character varying(20),
    changes jsonb,
    comment text,
    actor_user_id bigint,
    action_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_affiliation_request_logs_action CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'UPDATED'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

--
-- Name: TABLE affiliation_request_logs; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.affiliation_request_logs IS 'Bitácora de cada solicitud de afiliación: creación, ediciones, aprobación y rechazo';

--
-- Name: COLUMN affiliation_request_logs.action; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_request_logs.action IS 'Qué pasó: CREATED, UPDATED, APPROVED o REJECTED';

--
-- Name: COLUMN affiliation_request_logs.changes; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_request_logs.changes IS 'Campos editados: lista de {field, from, to}';

--
-- Name: COLUMN affiliation_request_logs.comment; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_request_logs.comment IS 'Nota de quien hizo el cambio (en un rechazo, el motivo)';

--
-- Name: COLUMN affiliation_request_logs.actor_user_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_request_logs.actor_user_id IS 'Usuario que hizo el cambio; vacío cuando lo envió la persona desde la página pública';

--
-- Name: affiliation_request_logs_affiliation_request_logs_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.affiliation_request_logs ALTER COLUMN affiliation_request_logs_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.affiliation_request_logs_affiliation_request_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: affiliation_requests; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.affiliation_requests (
    affiliation_requests_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    first_name character varying(50) NOT NULL,
    last_name character varying(50) NOT NULL,
    document_type character varying(2) NOT NULL,
    document_number character varying(20) NOT NULL,
    birth_date date,
    email character varying(254) NOT NULL,
    phone character varying(30),
    locale character varying(10),
    plan_id bigint,
    promoter_id bigint,
    referral_code character varying(20),
    password_hash character varying(255),
    terms_versions_id bigint,
    terms_rendered_content text,
    terms_content_sha256 character varying(64),
    terms_accepted_at timestamp with time zone,
    ip_address character varying(45),
    user_agent character varying(400),
    reviewed_by bigint,
    reviewed_at timestamp with time zone,
    rejection_reason text,
    user_id bigint,
    member_id bigint,
    membership_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    channel character varying(20) DEFAULT 'SELF_SERVICE'::character varying NOT NULL,
    expires_at_override date,
    reinscription_required_override boolean,
    payment_method_id bigint,
    payment_bank_id bigint,
    payment_amount numeric(10,2),
    payment_currency_code character varying(3),
    payment_identification character varying(80),
    payment_bank_account_type character varying(40),
    payment_bank_account_code character varying(40),
    payment_bank_account_identifier character varying(120),
    payment_phone character varying(40),
    payment_email character varying(160),
    payment_reference_number character varying(80),
    payment_date timestamp with time zone,
    payment_support_file_url character varying(500),
    payment_support_file_name character varying(255),
    payment_support_file_content_type character varying(100),
    payment_support_file_size_bytes bigint,
    payment_id bigint,
    family jsonb,
    submitted_at timestamp with time zone,
    retain_from_cleanup boolean DEFAULT false NOT NULL,
    retain_note character varying(200),
    retained_by uuid,
    retained_at timestamp with time zone,
    CONSTRAINT chk_affiliation_requests_retain CHECK ((retain_from_cleanup OR ((retain_note IS NULL) AND (retained_by IS NULL) AND (retained_at IS NULL)))),
    CONSTRAINT ck_affiliation_requests_approved_links CHECK ((((status)::text <> 'APPROVED'::text) OR ((user_id IS NOT NULL) AND (member_id IS NOT NULL) AND (membership_id IS NOT NULL)))),
    CONSTRAINT ck_affiliation_requests_channel CHECK (((channel)::text = ANY ((ARRAY['SELF_SERVICE'::character varying, 'ASSISTED'::character varying])::text[]))),
    CONSTRAINT ck_affiliation_requests_complete_after_draft CHECK ((((status)::text = 'DRAFT'::text) OR ((birth_date IS NOT NULL) AND (plan_id IS NOT NULL) AND (terms_versions_id IS NOT NULL) AND (terms_rendered_content IS NOT NULL) AND (terms_content_sha256 IS NOT NULL) AND (terms_accepted_at IS NOT NULL)))),
    CONSTRAINT ck_affiliation_requests_document_type CHECK (((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[]))),
    CONSTRAINT ck_affiliation_requests_password_by_channel CHECK ((((channel)::text = 'ASSISTED'::text) OR (password_hash IS NOT NULL) OR (user_id IS NOT NULL))),
    CONSTRAINT ck_affiliation_requests_payment_method_date CHECK (((payment_method_id IS NULL) = (payment_date IS NULL))),
    CONSTRAINT ck_affiliation_requests_rejection_reason CHECK ((((status)::text <> 'REJECTED'::text) OR ((rejection_reason IS NOT NULL) AND (btrim(rejection_reason) <> ''::text)))),
    CONSTRAINT ck_affiliation_requests_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

--
-- Name: TABLE affiliation_requests; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.affiliation_requests IS 'Solicitudes de afiliación desde la página pública (QR del asesor); se revisan y se aprueban o rechazan';

--
-- Name: COLUMN affiliation_requests.locale; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.locale IS 'Idioma del formulario (es / en), usado para los correos de la solicitud';

--
-- Name: COLUMN affiliation_requests.promoter_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.promoter_id IS 'Asesor de la solicitud: el del código de referido (?ref=) en la página pública, o el asesor que la llenó en una afiliación asistida';

--
-- Name: COLUMN affiliation_requests.referral_code; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.referral_code IS 'Código de referido tal como llegó, normalizado en mayúsculas';

--
-- Name: COLUMN affiliation_requests.password_hash; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.password_hash IS 'Hash BCrypt de la contraseña elegida en el formulario; pasa al usuario al aprobar. Vacío en una afiliación asistida sin contraseña: al aprobar se envía un enlace para crearla';

--
-- Name: COLUMN affiliation_requests.terms_versions_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.terms_versions_id IS 'Versión del contrato de afiliado aceptada en el formulario';

--
-- Name: COLUMN affiliation_requests.terms_rendered_content; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.terms_rendered_content IS 'Texto del contrato tal como lo leyó la persona, con sus datos';

--
-- Name: COLUMN affiliation_requests.terms_content_sha256; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.terms_content_sha256 IS 'SHA-256 del texto aceptado; si al aprobar el texto cambia, se pide aceptar de nuevo al entrar';

--
-- Name: COLUMN affiliation_requests.terms_accepted_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.terms_accepted_at IS 'Momento en que la persona aceptó el contrato en el formulario';

--
-- Name: COLUMN affiliation_requests.ip_address; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.ip_address IS 'IP desde la que se envió la solicitud (evidencia de la aceptación)';

--
-- Name: COLUMN affiliation_requests.user_agent; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.user_agent IS 'Navegador desde el que se envió la solicitud (evidencia de la aceptación)';

--
-- Name: COLUMN affiliation_requests.reviewed_by; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.reviewed_by IS 'Usuario que aprobó o rechazó la solicitud';

--
-- Name: COLUMN affiliation_requests.reviewed_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.reviewed_at IS 'Momento de la aprobación o el rechazo';

--
-- Name: COLUMN affiliation_requests.rejection_reason; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.rejection_reason IS 'Motivo del rechazo, obligatorio cuando la solicitud se rechaza; se envía a la persona';

--
-- Name: COLUMN affiliation_requests.user_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.user_id IS 'Usuario creado al aprobar';

--
-- Name: COLUMN affiliation_requests.member_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.member_id IS 'Afiliado creado al aprobar';

--
-- Name: COLUMN affiliation_requests.membership_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.membership_id IS 'Membresía creada al aprobar (suspendida hasta el primer pago)';

--
-- Name: COLUMN affiliation_requests.status; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.status IS 'Estado: DRAFT (registro en progreso, aún no enviado), PENDING (por revisar), APPROVED o REJECTED';

--
-- Name: COLUMN affiliation_requests.channel; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.channel IS 'Canal de la solicitud: SELF_SERVICE (página pública, QR del asesor) o ASSISTED (el asesor la llenó desde su portal con la familia)';

--
-- Name: COLUMN affiliation_requests.payment_method_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_method_id IS 'Forma de pago de la inscripción reportada en el formulario; vacío si el solicitante no reportó pago';

--
-- Name: COLUMN affiliation_requests.payment_bank_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_bank_id IS 'Banco del pago de inscripción reportado (cuando la forma de pago lo exige)';

--
-- Name: COLUMN affiliation_requests.payment_amount; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_amount IS 'Monto del pago de inscripción: la cuota de inscripción del plan al momento de la solicitud (no lo envía el cliente)';

--
-- Name: COLUMN affiliation_requests.payment_currency_code; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_currency_code IS 'Moneda (ISO 4217) del pago de inscripción: la moneda del plan';

--
-- Name: COLUMN affiliation_requests.payment_identification; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_identification IS 'Identificación (cédula/RIF) del titular del pago reportado';

--
-- Name: COLUMN affiliation_requests.payment_bank_account_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_bank_account_type IS 'Tipo de cuenta bancaria del pago reportado';

--
-- Name: COLUMN affiliation_requests.payment_bank_account_code; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_bank_account_code IS 'Código de la cuenta bancaria del pago reportado';

--
-- Name: COLUMN affiliation_requests.payment_bank_account_identifier; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_bank_account_identifier IS 'Número de cuenta bancaria del pago reportado';

--
-- Name: COLUMN affiliation_requests.payment_phone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_phone IS 'Teléfono asociado al pago reportado (p. ej. pago móvil)';

--
-- Name: COLUMN affiliation_requests.payment_email; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_email IS 'Correo asociado al pago reportado (p. ej. Zelle)';

--
-- Name: COLUMN affiliation_requests.payment_reference_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_reference_number IS 'Número de referencia del pago reportado';

--
-- Name: COLUMN affiliation_requests.payment_date; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_date IS 'Fecha en que el solicitante indica haber realizado el pago de inscripción';

--
-- Name: COLUMN affiliation_requests.payment_support_file_url; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_support_file_url IS 'Clave del comprobante de pago en el almacenamiento (R2); vacía si no se adjuntó o el almacenamiento estaba deshabilitado';

--
-- Name: COLUMN affiliation_requests.payment_support_file_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_support_file_name IS 'Nombre original del comprobante de pago adjuntado';

--
-- Name: COLUMN affiliation_requests.payment_support_file_content_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_support_file_content_type IS 'Tipo MIME del comprobante de pago adjuntado';

--
-- Name: COLUMN affiliation_requests.payment_support_file_size_bytes; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_support_file_size_bytes IS 'Tamaño en bytes del comprobante de pago adjuntado';

--
-- Name: COLUMN affiliation_requests.payment_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.payment_id IS 'Pago de inscripción creado al aprobar la solicitud a partir del pago reportado';

--
-- Name: COLUMN affiliation_requests.family; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.family IS 'Familiares capturados en el paso "familia" (lista de {firstName, lastName, documentType, documentNumber, birthDate, relationship, phone, email}); se convierten en beneficiarios al aprobar';

--
-- Name: COLUMN affiliation_requests.submitted_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.submitted_at IS 'Momento en que la persona envió el registro a revisión (salió de DRAFT)';

--
-- Name: COLUMN affiliation_requests.retain_from_cleanup; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.retain_from_cleanup IS 'Registro mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';

--
-- Name: COLUMN affiliation_requests.retain_note; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.retain_note IS 'Nota opcional del motivo por el que se mantiene el registro (máx. 200)';

--
-- Name: COLUMN affiliation_requests.retained_by; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.retained_by IS 'UUID del usuario que marcó el registro como mantenido';

--
-- Name: COLUMN affiliation_requests.retained_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.affiliation_requests.retained_at IS 'Momento en que se marcó el registro como mantenido';

--
-- Name: affiliation_requests_affiliation_requests_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.affiliation_requests ALTER COLUMN affiliation_requests_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.affiliation_requests_affiliation_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: allies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.allies (
    allies_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(200) NOT NULL,
    tax_document_type character varying(1),
    tax_document_number character varying(20),
    email app.citext,
    phone character varying(30),
    website character varying(255),
    address text,
    city_id bigint,
    logo_url character varying(500),
    description text,
    joined_at date,
    is_published boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    whatsapp character varying(30),
    instagram character varying(255),
    facebook character varying(255),
    google_maps_url character varying(500),
    referral_code character varying(20),
    ally_number integer NOT NULL,
    captured_by_promoter_id bigint,
    representative_name character varying(210),
    representative_document_type character varying(1),
    representative_document_number character varying(20),
    segment character varying(3) DEFAULT 'B2B'::character varying NOT NULL,
    person_id bigint,
    host_ally_id bigint,
    host_name character varying(200),
    office_number character varying(20),
    appointment_phone character varying(30),
    assistant_name character varying(150),
    commercial_zone character varying(100),
    trade_name character varying(200),
    founded_year smallint,
    cash_registers_count integer,
    branches_count integer,
    has_pos boolean,
    representative_position character varying(100),
    rating_avg numeric(3,2),
    rating_count integer DEFAULT 0 NOT NULL,
    registration_status character varying(10) NOT NULL,
    registration_channel character varying(12) NOT NULL,
    registration_note text,
    approved_at timestamp with time zone,
    approved_by uuid,
    rejected_at timestamp with time zone,
    rejected_by uuid,
    rejection_reason text,
    registration_submitted_at timestamp with time zone,
    retain_from_cleanup boolean DEFAULT false NOT NULL,
    retain_note character varying(200),
    retained_by uuid,
    retained_at timestamp with time zone,
    CONSTRAINT allies_tax_document_type_check CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[]))),
    CONSTRAINT chk_allies_branches_count CHECK (((branches_count IS NULL) OR (branches_count >= 0))),
    CONSTRAINT chk_allies_cash_registers_count CHECK (((cash_registers_count IS NULL) OR (cash_registers_count >= 0))),
    CONSTRAINT chk_allies_founded_year CHECK (((founded_year IS NULL) OR (founded_year >= 1800))),
    CONSTRAINT chk_allies_host_not_self CHECK (((host_ally_id IS NULL) OR (host_ally_id <> allies_id))),
    CONSTRAINT chk_allies_published_requires_approval CHECK (((NOT is_published) OR ((registration_status)::text = 'APPROVED'::text))),
    CONSTRAINT chk_allies_registration_channel CHECK (((registration_channel)::text = ANY ((ARRAY['ADMIN'::character varying, 'SELF_SERVICE'::character varying, 'IMPORT'::character varying, 'ADVISOR'::character varying])::text[]))),
    CONSTRAINT chk_allies_registration_status CHECK (((registration_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT chk_allies_rejection_reason CHECK ((((registration_status)::text <> 'REJECTED'::text) OR (rejection_reason IS NOT NULL))),
    CONSTRAINT chk_allies_representative_document CHECK ((((representative_document_type IS NULL) AND (representative_document_number IS NULL)) OR ((representative_document_type IS NOT NULL) AND (representative_document_number IS NOT NULL)))),
    CONSTRAINT chk_allies_representative_document_type CHECK (((representative_document_type IS NULL) OR ((representative_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[])))),
    CONSTRAINT chk_allies_retain CHECK ((retain_from_cleanup OR ((retain_note IS NULL) AND (retained_by IS NULL) AND (retained_at IS NULL)))),
    CONSTRAINT chk_allies_segment CHECK (((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[]))),
    CONSTRAINT chk_allies_tax_document CHECK ((((tax_document_type IS NULL) AND (tax_document_number IS NULL)) OR ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL))))
);

--
-- Name: COLUMN allies.ally_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.ally_number IS 'Número secuencial del aliado (Nº DE ALIADO); asignado por la BD, nunca editable';

--
-- Name: COLUMN allies.captured_by_promoter_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.captured_by_promoter_id IS 'Asesor (promotor) que captó al aliado; su código de referido forma el expediente [CÓDIGO DE ASESOR]-[Nº DE ALIADO]';

--
-- Name: COLUMN allies.representative_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.representative_name IS 'DEPRECATED (V211): use person_id';

--
-- Name: COLUMN allies.representative_document_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.representative_document_type IS 'DEPRECATED (V211): use person_id';

--
-- Name: COLUMN allies.representative_document_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.representative_document_number IS 'DEPRECATED (V211): use person_id';

--
-- Name: COLUMN allies.segment; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.segment IS 'B2B Aliado Comercial / B2P Aliado Profesional (hub ADR 0031); its types must belong to this segment';

--
-- Name: COLUMN allies.person_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.person_id IS 'Holder person: the professional (B2P) or the representative who signs the agreement (B2B)';

--
-- Name: COLUMN allies.host_ally_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.host_ally_id IS 'B2P: clinic where the professional practices, when that clinic is an ally too';

--
-- Name: COLUMN allies.host_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.host_name IS 'B2P: clinic name when it is not an ally';

--
-- Name: COLUMN allies.office_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.office_number IS 'B2P: office (consultorio) number';

--
-- Name: COLUMN allies.appointment_phone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.appointment_phone IS 'B2P: phone for appointments';

--
-- Name: COLUMN allies.assistant_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.assistant_name IS 'B2P: assistant name';

--
-- Name: COLUMN allies.commercial_zone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.commercial_zone IS 'B2B: commercial region / zone';

--
-- Name: COLUMN allies.trade_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.trade_name IS 'B2B: trade name (nombre comercial); name is the legal name';

--
-- Name: COLUMN allies.founded_year; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.founded_year IS 'B2B: founding year';

--
-- Name: COLUMN allies.cash_registers_count; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.cash_registers_count IS 'B2B: number of cash registers';

--
-- Name: COLUMN allies.branches_count; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.branches_count IS 'B2B: number of branches';

--
-- Name: COLUMN allies.has_pos; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.has_pos IS 'B2B: whether it runs a POS system';

--
-- Name: COLUMN allies.representative_position; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.representative_position IS 'B2B: position (cargo) of the representative who signs';

--
-- Name: COLUMN allies.rating_avg; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.rating_avg IS 'Promedio de ally_rating de sus consumos vigentes; NULL sin votos. Lo sincroniza el servicio de calificaciones.';

--
-- Name: COLUMN allies.registration_status; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.registration_status IS 'Ciclo de la solicitud de alianza (ADR 0036): PENDING hasta que un operador registra el convenio y la aprueba; solo APPROVED puede publicarse';

--
-- Name: COLUMN allies.registration_channel; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.registration_channel IS 'Origen del aliado: ADMIN (alta desde el panel), SELF_SERVICE (/solicitar-alianza), IMPORT (plantilla Excel/CSV), ADVISOR (captado por un asesor desde su portal)';

--
-- Name: COLUMN allies.registration_note; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.registration_note IS 'Beneficio que el aliado propone al registrarse (texto libre para el operador)';

--
-- Name: COLUMN allies.registration_submitted_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.registration_submitted_at IS 'Momento en que el aliado envió su solicitud a revisión; vacío mientras la completa por pasos';

--
-- Name: COLUMN allies.retain_from_cleanup; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.retain_from_cleanup IS 'Registro en progreso mantenido (negociación abierta o a la espera de información): la limpieza de borradores abandonados lo omite';

--
-- Name: COLUMN allies.retain_note; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.retain_note IS 'Nota opcional del motivo por el que se mantiene el registro (máx. 200)';

--
-- Name: COLUMN allies.retained_by; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.retained_by IS 'UUID del usuario que marcó el registro como mantenido';

--
-- Name: COLUMN allies.retained_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.allies.retained_at IS 'Momento en que se marcó el registro como mantenido';

--
-- Name: allies_allies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.allies ALTER COLUMN allies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.allies_allies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: allies_ally_number_seq; Type: SEQUENCE; Schema: app; Owner: -
--

CREATE SEQUENCE app.allies_ally_number_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: allies_ally_number_seq; Type: SEQUENCE OWNED BY; Schema: app; Owner: -
--

ALTER SEQUENCE app.allies_ally_number_seq OWNED BY app.allies.ally_number;

--
-- Name: ally_agreements; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_agreements (
    ally_agreements_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    ally_id bigint NOT NULL,
    agreement_type character varying(50) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    terms text,
    signed_pdf_url character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    terms_version_id bigint,
    auto_renew boolean DEFAULT true NOT NULL,
    notice_days integer DEFAULT 30 NOT NULL,
    CONSTRAINT ally_agreements_agreement_type_check CHECK (((agreement_type)::text = ANY ((ARRAY['COMMERCIAL'::character varying, 'MEDICAL'::character varying, 'EXCLUSIVITY'::character varying, 'SUPPLY'::character varying])::text[]))),
    CONSTRAINT ally_agreements_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'TERMINATED'::character varying])::text[]))),
    CONSTRAINT chk_agreement_dates CHECK (((end_date IS NULL) OR (end_date >= start_date))),
    CONSTRAINT chk_ally_agreements_notice_days CHECK ((notice_days >= 0))
);

--
-- Name: COLUMN ally_agreements.terms_version_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_agreements.terms_version_id IS 'Versión del convenio estándar (terms_versions con term_type = ALIADO) bajo la que se firmó';

--
-- Name: COLUMN ally_agreements.auto_renew; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_agreements.auto_renew IS 'Renovación automática al vencer la vigencia (por defecto anual y automática)';

--
-- Name: COLUMN ally_agreements.notice_days; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_agreements.notice_days IS 'Días de preaviso requeridos para rescindir el convenio (por defecto 30)';

--
-- Name: ally_agreements_ally_agreements_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_agreements ALTER COLUMN ally_agreements_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_agreements_ally_agreements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_ally_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_ally_types (
    ally_ally_types_id bigint NOT NULL,
    ally_id bigint NOT NULL,
    ally_type_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);

--
-- Name: ally_ally_types_ally_ally_types_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_ally_types ALTER COLUMN ally_ally_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_ally_types_ally_ally_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_opening_hours; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_opening_hours (
    ally_opening_hours_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    ally_id bigint NOT NULL,
    day_of_week smallint NOT NULL,
    opens_at time without time zone NOT NULL,
    closes_at time without time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_ally_opening_hours_day CHECK (((day_of_week >= 1) AND (day_of_week <= 7))),
    CONSTRAINT ck_ally_opening_hours_range CHECK ((closes_at > opens_at)),
    CONSTRAINT ck_ally_opening_hours_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);

--
-- Name: TABLE ally_opening_hours; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.ally_opening_hours IS 'Horario semanal de atención del aliado: un tramo por fila, varios por día (ADR 0031 §7)';

--
-- Name: COLUMN ally_opening_hours.day_of_week; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_opening_hours.day_of_week IS 'Día ISO-8601: 1 = lunes … 7 = domingo';

--
-- Name: COLUMN ally_opening_hours.opens_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_opening_hours.opens_at IS 'Hora de apertura (hora local de America/Caracas)';

--
-- Name: COLUMN ally_opening_hours.closes_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_opening_hours.closes_at IS 'Hora de cierre (hora local de America/Caracas), siempre mayor que la apertura';

--
-- Name: ally_opening_hours_ally_opening_hours_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_opening_hours ALTER COLUMN ally_opening_hours_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_opening_hours_ally_opening_hours_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_patient_links; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_patient_links (
    ally_patient_links_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    doctor_person_id bigint NOT NULL,
    member_id bigint NOT NULL,
    created_at_ally_id bigint NOT NULL,
    created_by_user_id bigint NOT NULL,
    revoked_at timestamp with time zone,
    revoked_by uuid,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_ally_patient_links_revoked CHECK ((((status)::text = 'REVOKED'::text) = (revoked_at IS NOT NULL))),
    CONSTRAINT chk_ally_patient_links_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying])::text[])))
);

--
-- Name: TABLE ally_patient_links; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.ally_patient_links IS 'Vínculo médico–paciente (ADR 0032): el médico (persona titular de un aliado B2P de área médica) puede leer la historia médica del afiliado mientras esté ACTIVE.';

--
-- Name: ally_patient_links_ally_patient_links_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_patient_links ALTER COLUMN ally_patient_links_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_patient_links_ally_patient_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_product_review_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_product_review_log (
    ally_product_review_log_id bigint NOT NULL,
    ally_product_id bigint NOT NULL,
    from_status character varying(20),
    to_status character varying(20) NOT NULL,
    actor_user_id bigint,
    action_at timestamp with time zone DEFAULT now() NOT NULL,
    comment text,
    CONSTRAINT ally_product_review_log_to_status_check CHECK (((to_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[])))
);

--
-- Name: ally_product_review_log_ally_product_review_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_product_review_log ALTER COLUMN ally_product_review_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_product_review_log_ally_product_review_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_products; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_products (
    ally_products_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    ally_id bigint NOT NULL,
    price_amount numeric(10,2),
    discount_pct numeric(5,2),
    review_status character varying(20) DEFAULT 'PROPOSED'::character varying NOT NULL,
    reviewed_by bigint,
    reviewed_at timestamp with time zone,
    review_reason text,
    is_published boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    price_currency_id bigint,
    exclusions text,
    product_id bigint NOT NULL,
    logo_url character varying(500),
    discount_type character varying(20) DEFAULT 'PERCENTAGE'::character varying NOT NULL,
    discount_amount numeric(10,2),
    rating_avg numeric(3,2),
    rating_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ally_products_discount_pct_check CHECK (((discount_pct IS NULL) OR ((discount_pct >= (0)::numeric) AND (discount_pct <= (100)::numeric)))),
    CONSTRAINT ally_products_review_status_check CHECK (((review_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[]))),
    CONSTRAINT chk_ally_products_discount_coherence CHECK (((((discount_type)::text = 'FLAT'::text) AND (discount_amount IS NOT NULL) AND (discount_amount > (0)::numeric) AND (discount_pct IS NULL) AND (price_amount IS NOT NULL)) OR (((discount_type)::text = 'PERCENTAGE'::text) AND (discount_amount IS NULL)))),
    CONSTRAINT chk_ally_products_discount_type CHECK (((discount_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[]))),
    CONSTRAINT chk_ally_products_price_currency_paired CHECK ((((price_amount IS NULL) AND (price_currency_id IS NULL)) OR ((price_amount IS NOT NULL) AND (price_currency_id IS NOT NULL)))),
    CONSTRAINT chk_ally_products_published_requires_approved CHECK (((is_published = false) OR ((review_status)::text = 'APPROVED'::text))),
    CONSTRAINT chk_ally_products_review_reason CHECK ((((review_status)::text <> ALL ((ARRAY['REJECTED'::character varying, 'REMOVED'::character varying])::text[])) OR ((review_reason IS NOT NULL) AND (reviewed_by IS NOT NULL))))
);

--
-- Name: TABLE ally_products; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.ally_products IS 'Oferta de un aliado sobre un producto del maestro (antes ally_services, ADR 0026): precio, descuento, exclusiones, revisión y publicación.';

--
-- Name: COLUMN ally_products.exclusions; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_products.exclusions IS 'Exclusiones operativas de la tarifa preferencial (p. ej. ofertas existentes, licores, cigarrillos, insumos de alto costo)';

--
-- Name: COLUMN ally_products.logo_url; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_products.logo_url IS 'URL de la imagen principal propia de la oferta (media_items); NULL = usa la del producto (ADR 0027).';

--
-- Name: COLUMN ally_products.discount_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_products.discount_type IS 'Tarifa preferencial: FLAT (monto fijo en la moneda del precio) o PERCENTAGE (porcentaje)';

--
-- Name: COLUMN ally_products.discount_amount; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_products.discount_amount IS 'Monto de descuento en la moneda del precio (price_currency_id); solo para FLAT';

--
-- Name: COLUMN ally_products.rating_avg; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_products.rating_avg IS 'Promedio de product_rating de los consumos vigentes de esta oferta; NULL sin votos.';

--
-- Name: ally_products_ally_products_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_products ALTER COLUMN ally_products_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_products_ally_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_professions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_professions (
    ally_specialties_id bigint NOT NULL,
    ally_id bigint NOT NULL,
    profession_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);

--
-- Name: ally_specialties_ally_specialties_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_professions ALTER COLUMN ally_specialties_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_specialties_ally_specialties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_types (
    ally_types_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    segment character varying(3) DEFAULT 'B2B'::character varying NOT NULL,
    professional_area character varying(20),
    CONSTRAINT chk_ally_types_professional_area CHECK (((professional_area IS NULL) OR ((professional_area)::text = ANY ((ARRAY['MEDICAL'::character varying, 'WELLNESS'::character varying])::text[])))),
    CONSTRAINT chk_ally_types_professional_area_requires_b2p CHECK (((professional_area IS NULL) OR ((segment)::text = 'B2P'::text))),
    CONSTRAINT chk_ally_types_segment CHECK (((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[])))
);

--
-- Name: COLUMN ally_types.segment; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_types.segment IS 'Segmento del aliado: B2B = Aliado Comercial (persona jurídica), B2P = Aliado Profesional (profesional independiente)';

--
-- Name: COLUMN ally_types.professional_area; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.ally_types.professional_area IS 'Solo para B2P: MEDICAL = Área Médica, WELLNESS = Bienestar Integral; NULL para B2B';

--
-- Name: ally_types_ally_types_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_types ALTER COLUMN ally_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_types_ally_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: ally_users; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.ally_users (
    ally_users_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    ally_id bigint NOT NULL,
    user_id bigint NOT NULL,
    ally_role character varying(20) DEFAULT 'STAFF'::character varying NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    joined_at date,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ally_users_ally_role_check CHECK (((ally_role)::text = ANY ((ARRAY['OWNER'::character varying, 'STAFF'::character varying, 'VIEWER'::character varying])::text[]))),
    CONSTRAINT chk_ally_users_primary_is_owner CHECK (((is_primary = false) OR ((ally_role)::text = 'OWNER'::text)))
);

--
-- Name: ally_users_ally_users_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.ally_users ALTER COLUMN ally_users_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.ally_users_ally_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: attached_files; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.attached_files (
    attached_files_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_table character varying(50) NOT NULL,
    owner_uuid uuid NOT NULL,
    visibility character varying(20) NOT NULL,
    category character varying(50) NOT NULL,
    file_key character varying(500) NOT NULL,
    file_name character varying(255) NOT NULL,
    mime_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    uploaded_by bigint,
    uploaded_at timestamp with time zone DEFAULT now() NOT NULL,
    shared boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    published_at timestamp with time zone,
    published_by bigint,
    status character varying(50),
    CONSTRAINT attached_files_size_bytes_check CHECK ((size_bytes > 0)),
    CONSTRAINT attached_files_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))
);

--
-- Name: attached_files_attached_files_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.attached_files ALTER COLUMN attached_files_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.attached_files_attached_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: entity_config; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.entity_config (
    entity_config_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    entity_key character varying(80) NOT NULL,
    display_name character varying(120) NOT NULL,
    table_name character varying(120),
    enabled boolean DEFAULT true NOT NULL,
    audit_create boolean DEFAULT true NOT NULL,
    audit_update boolean DEFAULT true NOT NULL,
    audit_delete boolean DEFAULT true NOT NULL,
    audit_report boolean DEFAULT true NOT NULL,
    capture_before_after boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    default_sort jsonb
);

--
-- Name: TABLE entity_config; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.entity_config IS 'Per-entity behavior config: audit toggles (original V60 scope) + default list sort (V80). entity_key must match the "entity" attribute of the @Auditable annotation on the corresponding service method.';

--
-- Name: COLUMN entity_config.default_sort; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.entity_config.default_sort IS 'Ordered array of {"field": "<sortable key>", "direction": "ASC"|"DESC"}, e.g. [{"field":"name","direction":"ASC"},{"field":"allyTypeName","direction":"ASC"}]. NULL falls back to the hard default createdAt DESC. "field" is the key the frontend table sends (matches SortFieldValidator.SORTABLE_FIELDS), not a raw JPA path.';

--
-- Name: audit_entity_config_audit_entity_config_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.entity_config ALTER COLUMN entity_config_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.audit_entity_config_audit_entity_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: banks; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.banks (
    banks_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(10) NOT NULL,
    name character varying(120) NOT NULL,
    short_name character varying(60) NOT NULL,
    tax_document_type character varying(1) NOT NULL,
    tax_document_number character varying(20) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT banks_tax_document_type_check CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'G'::character varying])::text[])))
);

--
-- Name: banks_banks_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.banks ALTER COLUMN banks_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.banks_banks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: beneficiaries; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.beneficiaries (
    beneficiaries_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    member_id bigint NOT NULL,
    person_id bigint NOT NULL,
    relationship character varying(20) NOT NULL,
    extra_inscription_paid boolean DEFAULT false NOT NULL,
    inscription_payment_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    slot_type character varying(10) DEFAULT 'BASE'::character varying NOT NULL,
    user_id bigint,
    access_invited_at timestamp with time zone,
    CONSTRAINT beneficiaries_relationship_check CHECK (((relationship)::text = ANY ((ARRAY['SPOUSE'::character varying, 'CHILD'::character varying, 'PARENT'::character varying, 'SIBLING'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT chk_beneficiaries_slot_type CHECK (((slot_type)::text = ANY ((ARRAY['BASE'::character varying, 'EXTRA'::character varying])::text[])))
);

--
-- Name: COLUMN beneficiaries.slot_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.beneficiaries.slot_type IS 'Cupo que ocupa el beneficiario: BASE (núcleo familiar base, incluido en el plan) o EXTRA (extensión de blindaje, paga inscripción adicional)';

--
-- Name: COLUMN beneficiaries.user_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.beneficiaries.user_id IS 'Usuario propio del beneficiario (rol AFILIADO) para entrar al portal y usar los beneficios como el titular; vacío si nunca se le dio acceso. Al quitar el acceso el usuario se desactiva y el vínculo se conserva';

--
-- Name: COLUMN beneficiaries.access_invited_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.beneficiaries.access_invited_at IS 'Último envío del correo de activación de la cuenta del beneficiario';

--
-- Name: beneficiaries_beneficiaries_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.beneficiaries ALTER COLUMN beneficiaries_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.beneficiaries_beneficiaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: benefit_usages; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.benefit_usages (
    benefit_usages_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    membership_id bigint NOT NULL,
    ally_id bigint NOT NULL,
    ally_product_id bigint,
    ally_user_id bigint,
    usage_date date DEFAULT CURRENT_DATE NOT NULL,
    usage_datetime timestamp with time zone DEFAULT now() NOT NULL,
    copay_amount numeric(10,2),
    metadata jsonb,
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'REGISTERED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    copay_currency_id bigint,
    consumption_amount numeric(10,2),
    consumption_currency_id bigint,
    product_category_id bigint NOT NULL,
    beneficiary_id bigint,
    ally_rating smallint,
    product_rating smallint,
    rating_comment character varying(500),
    rated_at timestamp with time zone,
    converted_currency_id bigint,
    consumption_amount_converted numeric(14,2),
    consumption_exchange_rate numeric(18,8),
    copay_amount_converted numeric(14,2),
    copay_exchange_rate numeric(18,8),
    exchange_rate_date date,
    CONSTRAINT benefit_usages_status_check CHECK (((status)::text = ANY ((ARRAY['REGISTERED'::character varying, 'REVERSED'::character varying, 'DISPUTED'::character varying])::text[]))),
    CONSTRAINT chk_benefit_usages_ally_rating_range CHECK (((ally_rating IS NULL) OR ((ally_rating >= 1) AND (ally_rating <= 5)))),
    CONSTRAINT chk_benefit_usages_consumption_converted_paired CHECK (((consumption_amount_converted IS NULL) = (consumption_exchange_rate IS NULL))),
    CONSTRAINT chk_benefit_usages_consumption_paired CHECK ((((consumption_amount IS NULL) AND (consumption_currency_id IS NULL)) OR ((consumption_amount IS NOT NULL) AND (consumption_amount >= (0)::numeric) AND (consumption_currency_id IS NOT NULL)))),
    CONSTRAINT chk_benefit_usages_converted_currency CHECK ((((consumption_amount_converted IS NULL) AND (copay_amount_converted IS NULL)) OR (converted_currency_id IS NOT NULL))),
    CONSTRAINT chk_benefit_usages_copay_converted_paired CHECK (((copay_amount_converted IS NULL) = (copay_exchange_rate IS NULL))),
    CONSTRAINT chk_benefit_usages_copay_paired CHECK ((((copay_amount IS NULL) AND (copay_currency_id IS NULL)) OR ((copay_amount IS NOT NULL) AND (copay_amount >= (0)::numeric) AND (copay_currency_id IS NOT NULL)))),
    CONSTRAINT chk_benefit_usages_datetime_aligned CHECK (((usage_datetime <= (usage_date + '2 days'::interval)) AND (usage_datetime >= (usage_date - '1 day'::interval)))),
    CONSTRAINT chk_benefit_usages_not_future CHECK ((usage_date <= (CURRENT_DATE + '1 day'::interval))),
    CONSTRAINT chk_benefit_usages_product_rating_needs_product CHECK (((product_rating IS NULL) OR (ally_product_id IS NOT NULL))),
    CONSTRAINT chk_benefit_usages_product_rating_range CHECK (((product_rating IS NULL) OR ((product_rating >= 1) AND (product_rating <= 5)))),
    CONSTRAINT chk_benefit_usages_rating_paired CHECK ((((rated_at IS NULL) AND (ally_rating IS NULL) AND (product_rating IS NULL) AND (rating_comment IS NULL)) OR ((rated_at IS NOT NULL) AND (ally_rating IS NOT NULL))))
);

--
-- Name: COLUMN benefit_usages.consumption_amount; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.consumption_amount IS 'Valor del servicio consumido (precio de lista), distinto del copago que pagó el afiliado. NULL en los usos previos a V158.';

--
-- Name: COLUMN benefit_usages.product_category_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.product_category_id IS 'Rubro del consumo. Se copia del producto de la oferta cuando la hay; si no, lo elige el operador. Obligatorio para que todo consumo entre en el reporte por rubro.';

--
-- Name: COLUMN benefit_usages.beneficiary_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.beneficiary_id IS 'Beneficiario que usó el beneficio en el aliado; vacío cuando lo usó el titular';

--
-- Name: COLUMN benefit_usages.ally_rating; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.ally_rating IS 'Estrellas 1..5 que el afiliado dio al aliado por este consumo. NULL = sin calificar.';

--
-- Name: COLUMN benefit_usages.product_rating; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.product_rating IS 'Estrellas 1..5 al producto/servicio consumido. Solo cuando hay ally_product_id.';

--
-- Name: COLUMN benefit_usages.rated_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.rated_at IS 'Última vez que el afiliado calificó (o editó la calificación de) este consumo.';

--
-- Name: COLUMN benefit_usages.converted_currency_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.converted_currency_id IS 'Moneda oficial de la organización al registrar el consumo; destino de la conversión (V229).';

--
-- Name: COLUMN benefit_usages.consumption_exchange_rate; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.consumption_exchange_rate IS 'Unidades de la moneda convertida por 1 unidad de consumption_currency, vigente en la fecha del consumo (V229).';

--
-- Name: COLUMN benefit_usages.copay_exchange_rate; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.copay_exchange_rate IS 'Unidades de la moneda convertida por 1 unidad de copay_currency, vigente en la fecha del consumo (V229).';

--
-- Name: COLUMN benefit_usages.exchange_rate_date; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.benefit_usages.exchange_rate_date IS 'Fecha de operación (publicación) de la tasa usada en la conversión (V229).';

--
-- Name: benefit_usages_benefit_usages_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.benefit_usages ALTER COLUMN benefit_usages_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.benefit_usages_benefit_usages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: campaign_promoters; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.campaign_promoters (
    campaign_promoters_id bigint NOT NULL,
    uuid uuid NOT NULL,
    campaign_id bigint NOT NULL,
    promoter_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: campaign_promoters_campaign_promoters_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.campaign_promoters ALTER COLUMN campaign_promoters_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.campaign_promoters_campaign_promoters_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: campaign_transaction_exceptions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.campaign_transaction_exceptions (
    campaign_transaction_exceptions_id bigint NOT NULL,
    uuid uuid NOT NULL,
    campaign_id bigint NOT NULL,
    payment_id bigint,
    membership_id bigint,
    action character varying(10) NOT NULL,
    reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_campaign_transaction_exceptions_action CHECK (((action)::text = ANY ((ARRAY['INCLUDE'::character varying, 'EXCLUDE'::character varying])::text[]))),
    CONSTRAINT chk_campaign_transaction_exceptions_one_target CHECK (((payment_id IS NOT NULL) <> (membership_id IS NOT NULL)))
);

--
-- Name: campaign_transaction_exceptio_campaign_transaction_exceptio_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.campaign_transaction_exceptions ALTER COLUMN campaign_transaction_exceptions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.campaign_transaction_exceptio_campaign_transaction_exceptio_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: campaign_transaction_links; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.campaign_transaction_links (
    campaign_transaction_links_id bigint NOT NULL,
    uuid uuid NOT NULL,
    campaign_id bigint NOT NULL,
    payment_id bigint,
    membership_id bigint,
    source character varying(20) DEFAULT 'AUTO'::character varying NOT NULL,
    resolved_at timestamp with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_campaign_transaction_links_one_target CHECK (((payment_id IS NOT NULL) <> (membership_id IS NOT NULL))),
    CONSTRAINT chk_campaign_transaction_links_source CHECK (((source)::text = ANY ((ARRAY['AUTO'::character varying, 'EXCEPTION_INCLUDE'::character varying, 'EXCEPTION_EXCLUDE'::character varying])::text[])))
);

--
-- Name: campaign_transaction_links_campaign_transaction_links_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.campaign_transaction_links ALTER COLUMN campaign_transaction_links_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.campaign_transaction_links_campaign_transaction_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: campaigns; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.campaigns (
    campaigns_id bigint NOT NULL,
    uuid uuid NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    scope character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    mode character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    evaluate_only_at_end boolean DEFAULT false NOT NULL,
    pay_only_at_end boolean DEFAULT false NOT NULL,
    target_amount numeric(14,2),
    target_count integer,
    exclusivity_group character varying(80),
    priority integer,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    target_amount_currency_id bigint,
    CONSTRAINT campaigns_target_amount_currency_xor CHECK ((((target_amount IS NULL) AND (target_amount_currency_id IS NULL)) OR ((target_amount IS NOT NULL) AND (target_amount_currency_id IS NOT NULL)))),
    CONSTRAINT chk_campaigns_dates CHECK ((ends_at > starts_at)),
    CONSTRAINT chk_campaigns_evaluate_end_implies_pay_end CHECK (((NOT evaluate_only_at_end) OR pay_only_at_end)),
    CONSTRAINT chk_campaigns_mode CHECK (((mode)::text = ANY ((ARRAY['TARGETED'::character varying, 'GENERAL'::character varying])::text[]))),
    CONSTRAINT chk_campaigns_scope CHECK (((scope)::text = ANY ((ARRAY['ALL'::character varying, 'INCLUDE'::character varying, 'EXCLUDE'::character varying])::text[])))
);

--
-- Name: campaigns_campaigns_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.campaigns ALTER COLUMN campaigns_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.campaigns_campaigns_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: cities; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.cities (
    cities_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    state_id bigint NOT NULL,
    name character varying(120) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: cities_cities_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.cities ALTER COLUMN cities_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.cities_cities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: collection_commission_tier_promoter_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.collection_commission_tier_promoter_types (
    collection_commission_tier_promoter_types_id bigint NOT NULL,
    collection_commission_tier_id bigint NOT NULL,
    promoter_type_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);

--
-- Name: collection_commission_tier_pr_collection_commission_tier_pr_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.collection_commission_tier_promoter_types ALTER COLUMN collection_commission_tier_promoter_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.collection_commission_tier_pr_collection_commission_tier_pr_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: collection_commission_tiers; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.collection_commission_tiers (
    collection_commission_tiers_id bigint NOT NULL,
    uuid uuid NOT NULL,
    name character varying(80) NOT NULL,
    max_days integer,
    commission_pct numeric(5,2),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    description text,
    basis character varying(10) DEFAULT 'DAYS'::character varying NOT NULL,
    min_amount numeric(14,2),
    flat_amount numeric(10,2),
    flat_amount_currency_id bigint,
    campaign_id bigint,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    min_amount_currency_id bigint,
    accrual_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    partial_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    final_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    retroactive_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    accrual_period_anchor smallint,
    partial_settlement_period_anchor smallint,
    final_settlement_period_anchor smallint,
    retroactive_settlement_period_anchor smallint,
    CONSTRAINT chk_collection_commission_tiers_accrual_period_anchor_range CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31)))),
    CONSTRAINT chk_collection_commission_tiers_accrual_period_strategy CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_collection_commission_tiers_basis CHECK (((basis)::text = ANY ((ARRAY['DAYS'::character varying, 'AMOUNT'::character varying])::text[]))),
    CONSTRAINT chk_collection_commission_tiers_basis_field CHECK (((((basis)::text = 'DAYS'::text) AND (max_days IS NOT NULL) AND (min_amount IS NULL) AND (min_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (min_amount IS NOT NULL) AND (min_amount_currency_id IS NOT NULL) AND (max_days IS NULL)))),
    CONSTRAINT chk_collection_commission_tiers_final_settlement_period_anchor_ CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_collection_commission_tiers_final_settlement_period_strateg CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_collection_commission_tiers_flat_needs_currency CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL))),
    CONSTRAINT chk_collection_commission_tiers_max_days CHECK ((max_days > 0)),
    CONSTRAINT chk_collection_commission_tiers_partial_settlement_period_ancho CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_collection_commission_tiers_partial_settlement_period_strat CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_collection_commission_tiers_pct CHECK (((commission_pct > (0)::numeric) AND (commission_pct <= (100)::numeric))),
    CONSTRAINT chk_collection_commission_tiers_pct_xor_flat CHECK (((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL))),
    CONSTRAINT chk_collection_commission_tiers_retroactive_settlement_period_a CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_collection_commission_tiers_retroactive_settlement_period_s CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
);

--
-- Name: collection_commission_tiers_collection_commission_tiers_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.collection_commission_tiers ALTER COLUMN collection_commission_tiers_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.collection_commission_tiers_collection_commission_tiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commission_bonus_rule_promoter_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_bonus_rule_promoter_types (
    commission_bonus_rule_promoter_types_id bigint NOT NULL,
    commission_bonus_rule_id bigint NOT NULL,
    promoter_type_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);

--
-- Name: commission_bonus_rule_promote_commission_bonus_rule_promote_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_bonus_rule_promoter_types ALTER COLUMN commission_bonus_rule_promoter_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_bonus_rule_promote_commission_bonus_rule_promote_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commission_bonus_rules; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_bonus_rules (
    commission_bonus_rules_id bigint NOT NULL,
    uuid uuid NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    metric character varying(30) NOT NULL,
    accrual character varying(20) NOT NULL,
    threshold_count integer NOT NULL,
    accrual_period_strategy character varying(20) NOT NULL,
    campaign_start timestamp with time zone,
    campaign_end timestamp with time zone,
    reward_type character varying(20) NOT NULL,
    flat_amount numeric(10,2),
    reward_pct numeric(5,2),
    include_system_promoters boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    reward_currency_id bigint NOT NULL,
    campaign_id bigint,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    threshold_amount numeric(14,2),
    threshold_currency_id bigint,
    partial_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    final_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    retroactive_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    accrual_period_anchor smallint,
    partial_settlement_period_anchor smallint,
    final_settlement_period_anchor smallint,
    retroactive_settlement_period_anchor smallint,
    CONSTRAINT chk_bonus_rule_accrual CHECK (((accrual)::text = ANY ((ARRAY['PER_BLOCK'::character varying, 'THRESHOLD'::character varying])::text[]))),
    CONSTRAINT chk_bonus_rule_accrual_period_strategy CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['LIFETIME'::character varying, 'DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'CAMPAIGN'::character varying])::text[]))),
    CONSTRAINT chk_bonus_rule_campaign_dates CHECK (((((accrual_period_strategy)::text = 'CAMPAIGN'::text) AND (campaign_start IS NOT NULL) AND (campaign_end IS NOT NULL) AND (campaign_end >= campaign_start)) OR (((accrual_period_strategy)::text <> 'CAMPAIGN'::text) AND (campaign_start IS NULL) AND (campaign_end IS NULL)))),
    CONSTRAINT chk_bonus_rule_metric CHECK (((metric)::text = ANY ((ARRAY['NEW_SUBSCRIBERS'::character varying, 'ACTIVE_SUBSCRIBERS'::character varying, 'AMOUNT_COLLECTED'::character varying])::text[]))),
    CONSTRAINT chk_bonus_rule_reward_coherence CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (flat_amount > (0)::numeric) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (reward_pct > (0)::numeric) AND (reward_pct <= (100)::numeric) AND (flat_amount IS NULL)))),
    CONSTRAINT chk_bonus_rule_reward_type CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[]))),
    CONSTRAINT chk_bonus_rule_threshold_positive CHECK ((threshold_count > 0)),
    CONSTRAINT chk_commission_bonus_rules_accrual_period_anchor_range CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31)))),
    CONSTRAINT chk_commission_bonus_rules_final_settlement_period_anchor_range CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_bonus_rules_final_settlement_period_strategy CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_bonus_rules_partial_settlement_period_anchor_ran CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_bonus_rules_partial_settlement_period_strategy CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_bonus_rules_retroactive_settlement_period_anchor CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_bonus_rules_retroactive_settlement_period_strate CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT commission_bonus_rules_threshold_xor CHECK (((((metric)::text = 'AMOUNT_COLLECTED'::text) AND (threshold_amount IS NOT NULL) AND (threshold_currency_id IS NOT NULL) AND (threshold_count = 0)) OR (((metric)::text <> 'AMOUNT_COLLECTED'::text) AND (threshold_amount IS NULL) AND (threshold_currency_id IS NULL))))
);

--
-- Name: commission_bonus_rules_commission_bonus_rules_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_bonus_rules ALTER COLUMN commission_bonus_rules_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_bonus_rules_commission_bonus_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commissions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commissions (
    commissions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    promoter_id bigint NOT NULL,
    payment_id bigint NOT NULL,
    member_id bigint NOT NULL,
    amount numeric(10,2) NOT NULL,
    calculation_basis numeric(10,2) NOT NULL,
    commission_pct numeric(5,2),
    flat_amount numeric(10,2),
    commission_tier_id bigint,
    tier_name_snapshot character varying(80),
    applies_to character varying(20) NOT NULL,
    period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    earned_at timestamp with time zone DEFAULT now() NOT NULL,
    payout_reference character varying(120),
    paid_at timestamp with time zone,
    voided_at timestamp with time zone,
    void_reason text,
    admin_notes text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    collection_days integer,
    collection_tier_id bigint,
    currency_id bigint NOT NULL,
    approved_by_user_id bigint,
    approved_at timestamp with time zone,
    rejection_reason text,
    exchange_rate_at_earned numeric(18,8),
    earned_rate_date date,
    exchange_rate_at_paid numeric(18,8),
    paid_rate_date date,
    payout_payment_id bigint,
    auto_approved boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_commissions_approved_consistency CHECK ((((status)::text <> 'APPROVED'::text) OR auto_approved OR ((approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL)))),
    CONSTRAINT chk_commissions_collection_days CHECK ((collection_days >= 0)),
    CONSTRAINT chk_commissions_earned_in_period CHECK ((((earned_at)::date >= period_start) AND ((earned_at)::date <= period_end))),
    CONSTRAINT chk_commissions_paid_consistency CHECK ((((status)::text <> 'PAID'::text) OR ((paid_at IS NOT NULL) AND (payout_reference IS NOT NULL)))),
    CONSTRAINT chk_commissions_pct_xor_flat CHECK ((((commission_pct IS NOT NULL) AND (flat_amount IS NULL)) OR ((commission_pct IS NULL) AND (flat_amount IS NOT NULL)))),
    CONSTRAINT chk_commissions_period_bounds CHECK ((period_end >= period_start)),
    CONSTRAINT chk_commissions_rejected_consistency CHECK ((((status)::text <> 'REJECTED'::text) OR ((approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (rejection_reason IS NOT NULL)))),
    CONSTRAINT chk_commissions_voided_consistency CHECK ((((status)::text <> 'VOIDED'::text) OR ((voided_at IS NOT NULL) AND (void_reason IS NOT NULL)))),
    CONSTRAINT commissions_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT commissions_applies_to_check CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying])::text[]))),
    CONSTRAINT commissions_calculation_basis_check CHECK ((calculation_basis >= (0)::numeric)),
    CONSTRAINT commissions_commission_pct_check CHECK (((commission_pct IS NULL) OR ((commission_pct >= (0)::numeric) AND (commission_pct <= (100)::numeric)))),
    CONSTRAINT commissions_flat_amount_check CHECK (((flat_amount IS NULL) OR (flat_amount >= (0)::numeric))),
    CONSTRAINT commissions_period_strategy_check CHECK (((period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT commissions_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'PAID'::character varying, 'VOIDED'::character varying, 'DISPUTED'::character varying])::text[])))
);

--
-- Name: COLUMN commissions.collection_days; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.commissions.collection_days IS 'Days late the recurring payment was collected (payment_date - scheduled collection date), when priced by the collection-commission engine.';

--
-- Name: COLUMN commissions.collection_tier_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.commissions.collection_tier_id IS 'FK to the collection_commission_tiers bucket applied, when priced by the collection-commission engine.';

--
-- Name: commission_period_summary; Type: VIEW; Schema: app; Owner: -
--

CREATE VIEW app.commission_period_summary AS
 SELECT c.promoter_id,
    c.period_strategy,
    c.period_start,
    c.period_end,
    count(*) AS commission_count,
    sum(c.amount) AS total_amount,
    min(c.currency_id) AS currency_id
   FROM app.commissions c
  WHERE ((c.is_active = true) AND ((c.status)::text <> 'VOIDED'::text))
  GROUP BY c.promoter_id, c.period_strategy, c.period_start, c.period_end;

--
-- Name: commission_retroactive_topup_cuts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_retroactive_topup_cuts (
    commission_retroactive_topup_cuts_id bigint NOT NULL,
    uuid uuid NOT NULL,
    promoter_id bigint NOT NULL,
    ledger_type character varying(40) NOT NULL,
    accrual_period_start date NOT NULL,
    accrual_period_end date NOT NULL,
    cut_sequence integer NOT NULL,
    cut_start date NOT NULL,
    cut_end date NOT NULL,
    basis_amount_cumulative numeric(14,2) NOT NULL,
    target_amount_cumulative numeric(14,2) NOT NULL,
    already_paid_amount numeric(14,2) NOT NULL,
    retro_amount numeric(14,2) NOT NULL,
    currency_id bigint NOT NULL,
    tier_id bigint,
    tier_name_snapshot character varying(80),
    payout_payment_id bigint,
    payout_reference character varying(120),
    paid_at timestamp with time zone,
    voided_at timestamp with time zone,
    void_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_commission_retroactive_topup_cuts_ledger_type CHECK (((ledger_type)::text = ANY ((ARRAY['DIRECT_INSCRIPTION'::character varying, 'DIRECT_COLLECTION'::character varying, 'HIERARCHY_OVERRIDE_INSCRIPTION'::character varying, 'HIERARCHY_OVERRIDE_COLLECTION'::character varying])::text[]))),
    CONSTRAINT chk_commission_retroactive_topup_cuts_sequence CHECK ((cut_sequence >= 1)),
    CONSTRAINT chk_commission_retroactive_topup_cuts_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
);

--
-- Name: commission_retroactive_topup__commission_retroactive_topup__seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_retroactive_topup_cuts ALTER COLUMN commission_retroactive_topup_cuts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_retroactive_topup__commission_retroactive_topup__seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commission_retroactive_topups; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_retroactive_topups (
    commission_retroactive_topups_id bigint NOT NULL,
    uuid uuid NOT NULL,
    promoter_id bigint NOT NULL,
    ledger_type character varying(40) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    basis_amount numeric(10,2) NOT NULL,
    target_amount numeric(10,2) NOT NULL,
    already_paid_amount numeric(10,2) NOT NULL,
    retro_amount numeric(10,2) NOT NULL,
    currency_id bigint NOT NULL,
    tier_id bigint,
    tier_name_snapshot character varying(80),
    payout_reference character varying(120),
    paid_at timestamp with time zone,
    voided_at timestamp with time zone,
    void_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    payout_payment_id bigint,
    CONSTRAINT chk_commission_retroactive_topups_ledger_type CHECK (((ledger_type)::text = ANY ((ARRAY['DIRECT_INSCRIPTION'::character varying, 'DIRECT_COLLECTION'::character varying, 'HIERARCHY_OVERRIDE_INSCRIPTION'::character varying, 'HIERARCHY_OVERRIDE_COLLECTION'::character varying])::text[]))),
    CONSTRAINT chk_commission_retroactive_topups_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
);

--
-- Name: commission_retroactive_topups_commission_retroactive_topups_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_retroactive_topups ALTER COLUMN commission_retroactive_topups_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_retroactive_topups_commission_retroactive_topups_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commission_tier_promoter_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_tier_promoter_types (
    commission_tier_promoter_types_id bigint NOT NULL,
    commission_tier_id bigint NOT NULL,
    promoter_type_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);

--
-- Name: commission_tier_promoter_type_commission_tier_promoter_type_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_tier_promoter_types ALTER COLUMN commission_tier_promoter_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_tier_promoter_type_commission_tier_promoter_type_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commission_tiers; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.commission_tiers (
    commission_tiers_id bigint NOT NULL,
    uuid uuid NOT NULL,
    name character varying(80) NOT NULL,
    plan_type character varying(20),
    threshold_count integer DEFAULT 0 NOT NULL,
    commission_pct numeric(5,2),
    flat_amount numeric(10,2),
    accrual_period_strategy character varying(20) NOT NULL,
    applies_to character varying(20) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    partial_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    final_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    campaign_id bigint,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    flat_amount_currency_id bigint,
    description text,
    retroactive_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    accrual_period_anchor smallint,
    partial_settlement_period_anchor smallint,
    final_settlement_period_anchor smallint,
    retroactive_settlement_period_anchor smallint,
    basis character varying(10) DEFAULT 'COUNT'::character varying NOT NULL,
    threshold_amount numeric(14,2),
    threshold_amount_currency_id bigint,
    CONSTRAINT chk_commission_tiers_accrual_period_anchor_range CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31)))),
    CONSTRAINT chk_commission_tiers_accrual_period_strategy CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_applies_to CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying, 'BOTH'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_basis CHECK (((basis)::text = ANY ((ARRAY['COUNT'::character varying, 'AMOUNT'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_basis_field CHECK (((((basis)::text = 'COUNT'::text) AND (threshold_amount IS NULL) AND (threshold_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (threshold_amount IS NOT NULL) AND (threshold_amount_currency_id IS NOT NULL)))),
    CONSTRAINT chk_commission_tiers_final_settlement_period_anchor_range CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_tiers_final_settlement_period_strategy CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_flat CHECK (((flat_amount IS NULL) OR (flat_amount >= (0)::numeric))),
    CONSTRAINT chk_commission_tiers_flat_needs_currency CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL))),
    CONSTRAINT chk_commission_tiers_partial_settlement_period_anchor_range CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_tiers_partial_settlement_period_strategy CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_pct CHECK (((commission_pct IS NULL) OR ((commission_pct >= (0)::numeric) AND (commission_pct <= (100)::numeric)))),
    CONSTRAINT chk_commission_tiers_pct_xor_flat CHECK (((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL))),
    CONSTRAINT chk_commission_tiers_plan_type CHECK (((plan_type IS NULL) OR ((plan_type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'FAMILIAR'::character varying, 'CORPORATIVO'::character varying])::text[])))),
    CONSTRAINT chk_commission_tiers_retroactive_settlement_period_anchor_range CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_commission_tiers_retroactive_settlement_period_strategy CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_commission_tiers_threshold CHECK ((threshold_count >= 0))
);

--
-- Name: commission_tiers_commission_tiers_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commission_tiers ALTER COLUMN commission_tiers_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commission_tiers_commission_tiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: commissions_commissions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.commissions ALTER COLUMN commissions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.commissions_commissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: company_bank_account_payment_methods; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.company_bank_account_payment_methods (
    company_bank_account_id bigint NOT NULL,
    payment_method_id bigint NOT NULL
);

--
-- Name: TABLE company_bank_account_payment_methods; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.company_bank_account_payment_methods IS 'Métodos de pago que acepta cada cuenta de la empresa; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';

--
-- Name: company_bank_accounts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.company_bank_accounts (
    company_bank_accounts_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    bank_id bigint,
    currency_id bigint NOT NULL,
    account_type character varying(40),
    account_number character varying(40),
    holder_name character varying(160) NOT NULL,
    holder_document character varying(30),
    notes character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    is_public boolean DEFAULT false NOT NULL,
    phone character varying(30),
    email character varying(160)
);

--
-- Name: TABLE company_bank_accounts; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.company_bank_accounts IS 'Cuentas bancarias propias de OptiBienestar donde los afiliados depositan; se elige una por línea de pago.';

--
-- Name: COLUMN company_bank_accounts.is_public; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.company_bank_accounts.is_public IS 'TRUE = visible sin autenticación en GET /v1/public/company-bank-accounts; FALSE = solo para quien registra pagos.';

--
-- Name: COLUMN company_bank_accounts.phone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.company_bank_accounts.phone IS 'Teléfono de la cuenta (pago móvil).';

--
-- Name: COLUMN company_bank_accounts.email; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.company_bank_accounts.email IS 'Correo de la cuenta (Zelle y similares).';

--
-- Name: company_bank_accounts_company_bank_accounts_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.company_bank_accounts ALTER COLUMN company_bank_accounts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.company_bank_accounts_company_bank_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_award_settlements; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_award_settlements (
    competitive_commission_award_settlements_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    competitive_commission_rule_id bigint NOT NULL,
    competitive_commission_award_id bigint,
    promoter_id bigint NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    cut_kind character varying(20) NOT NULL,
    cut_sequence integer NOT NULL,
    cut_start date NOT NULL,
    cut_end date NOT NULL,
    award_position_at_cut integer,
    basis_amount_cumulative numeric(14,2),
    entitlement_cumulative numeric(12,2) NOT NULL,
    already_paid_amount numeric(12,2) NOT NULL,
    amount numeric(12,2) NOT NULL,
    currency_id bigint NOT NULL,
    exchange_rate_at_paid numeric(18,8),
    paid_rate_date date,
    payout_reference character varying(120),
    payout_payment_id bigint,
    paid_at timestamp with time zone,
    voided_at timestamp with time zone,
    void_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT competitive_commission_award_settlements_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT competitive_commission_award_settlements_cut_kind_check CHECK (((cut_kind)::text = ANY ((ARRAY['PARTIAL'::character varying, 'RETROACTIVE'::character varying, 'FINAL'::character varying])::text[]))),
    CONSTRAINT competitive_commission_award_settlements_cut_sequence_check CHECK ((cut_sequence >= 1)),
    CONSTRAINT competitive_commission_award_settlements_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
);

--
-- Name: competitive_commission_award__competitive_commission_award__seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_award_settlements ALTER COLUMN competitive_commission_award_settlements_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_award__competitive_commission_award__seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_awards; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_awards (
    competitive_commission_awards_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    competitive_commission_rule_id bigint NOT NULL,
    competitive_commission_rule_position_id bigint NOT NULL,
    promoter_id bigint NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    award_position integer NOT NULL,
    tie_group_size smallint DEFAULT 1 NOT NULL,
    metric_value numeric(14,2) NOT NULL,
    metric_transaction_count integer DEFAULT 0 NOT NULL,
    achieved_at timestamp with time zone,
    awarded_at timestamp with time zone DEFAULT now() NOT NULL,
    confirmed_at timestamp with time zone,
    reward_type character varying(20) NOT NULL,
    flat_amount numeric(10,2),
    reward_pct numeric(5,2),
    basis_amount numeric(14,2),
    amount numeric(12,2) NOT NULL,
    currency_id bigint NOT NULL,
    exchange_rate_at_award numeric(18,8),
    award_rate_date date,
    paid_at timestamp with time zone,
    payout_reference character varying(120),
    payout_payment_id bigint,
    exchange_rate_at_paid numeric(18,8),
    paid_rate_date date,
    voided_at timestamp with time zone,
    void_reason text,
    admin_notes text,
    rule_name_snapshot character varying(150) NOT NULL,
    snapshot_json jsonb NOT NULL,
    selection_source character varying(10) DEFAULT 'AUTO'::character varying NOT NULL,
    manual_decision_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PROVISIONAL'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_cca_paid CHECK ((((status)::text <> 'PAID'::text) OR (paid_at IS NOT NULL))),
    CONSTRAINT chk_cca_period CHECK ((period_end >= period_start)),
    CONSTRAINT chk_cca_voided CHECK ((((status)::text <> 'VOIDED'::text) OR (voided_at IS NOT NULL))),
    CONSTRAINT competitive_commission_awards_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT competitive_commission_awards_award_position_check CHECK ((award_position >= 1)),
    CONSTRAINT competitive_commission_awards_selection_source_check CHECK (((selection_source)::text = ANY ((ARRAY['AUTO'::character varying, 'MANUAL'::character varying])::text[]))),
    CONSTRAINT competitive_commission_awards_status_check CHECK (((status)::text = ANY ((ARRAY['PROVISIONAL'::character varying, 'PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[]))),
    CONSTRAINT competitive_commission_awards_tie_group_size_check CHECK ((tie_group_size >= 1))
);

--
-- Name: competitive_commission_awards_competitive_commission_awards_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_awards ALTER COLUMN competitive_commission_awards_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_awards_competitive_commission_awards_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_manual_decisions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_manual_decisions (
    competitive_commission_manual_decisions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    competitive_commission_rule_id bigint NOT NULL,
    competitive_commission_tie_id bigint,
    period_start date NOT NULL,
    kind character varying(20) NOT NULL,
    award_position integer,
    promoter_id bigint NOT NULL,
    replaced_promoter_id bigint,
    exclude_from_group boolean DEFAULT false NOT NULL,
    reason_category character varying(30) DEFAULT 'OTHER'::character varying NOT NULL,
    reason text NOT NULL,
    decided_by uuid NOT NULL,
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    reverted_at timestamp with time zone,
    reverted_by uuid,
    revert_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_ccmd_redirect CHECK ((((kind)::text <> 'REDIRECT'::text) OR ((replaced_promoter_id IS NOT NULL) AND (award_position IS NOT NULL)))),
    CONSTRAINT chk_ccmd_revert CHECK ((((status)::text <> 'REVERTED'::text) OR ((reverted_at IS NOT NULL) AND (length(TRIM(BOTH FROM revert_reason)) >= 10)))),
    CONSTRAINT competitive_commission_manual_decisions_award_position_check CHECK ((award_position >= 1)),
    CONSTRAINT competitive_commission_manual_decisions_kind_check CHECK (((kind)::text = ANY ((ARRAY['TIE_RESOLUTION'::character varying, 'REDIRECT'::character varying, 'DISQUALIFY'::character varying])::text[]))),
    CONSTRAINT competitive_commission_manual_decisions_reason_category_check CHECK (((reason_category)::text = ANY ((ARRAY['TIE_BREAK'::character varying, 'UNSPORTSMANLIKE_CONDUCT'::character varying, 'DATA_ERROR'::character varying, 'POLICY'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT competitive_commission_manual_decisions_reason_check CHECK ((length(TRIM(BOTH FROM reason)) >= 10)),
    CONSTRAINT competitive_commission_manual_decisions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'STALE'::character varying, 'REVERTED'::character varying])::text[])))
);

--
-- Name: competitive_commission_manual_competitive_commission_manual_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_manual_decisions ALTER COLUMN competitive_commission_manual_decisions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_manual_competitive_commission_manual_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_rule_positions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_rule_positions (
    competitive_commission_rule_positions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    competitive_commission_rule_id bigint NOT NULL,
    position_from integer NOT NULL,
    position_to integer NOT NULL,
    label character varying(80),
    reward_type character varying(20) NOT NULL,
    flat_amount numeric(10,2),
    reward_pct numeric(5,2),
    reward_currency_id bigint NOT NULL,
    reward_min_amount numeric(10,2),
    reward_max_amount numeric(10,2),
    min_threshold_count integer,
    min_threshold_amount numeric(14,2),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_ccrp_minmax CHECK (((reward_min_amount IS NULL) OR (reward_max_amount IS NULL) OR (reward_max_amount >= reward_min_amount))),
    CONSTRAINT chk_ccrp_range CHECK ((position_to >= position_from)),
    CONSTRAINT chk_ccrp_reward CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (flat_amount IS NULL)))),
    CONSTRAINT competitive_commission_rule_position_min_threshold_amount_check CHECK ((min_threshold_amount > (0)::numeric)),
    CONSTRAINT competitive_commission_rule_positions_flat_amount_check CHECK ((flat_amount > (0)::numeric)),
    CONSTRAINT competitive_commission_rule_positions_min_threshold_count_check CHECK ((min_threshold_count > 0)),
    CONSTRAINT competitive_commission_rule_positions_position_from_check CHECK ((position_from >= 1)),
    CONSTRAINT competitive_commission_rule_positions_reward_max_amount_check CHECK ((reward_max_amount > (0)::numeric)),
    CONSTRAINT competitive_commission_rule_positions_reward_min_amount_check CHECK ((reward_min_amount >= (0)::numeric)),
    CONSTRAINT competitive_commission_rule_positions_reward_pct_check CHECK (((reward_pct > (0)::numeric) AND (reward_pct <= (100)::numeric))),
    CONSTRAINT competitive_commission_rule_positions_reward_type_check CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))
);

--
-- Name: competitive_commission_rule_p_competitive_commission_rule_p_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_rule_positions ALTER COLUMN competitive_commission_rule_positions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_rule_p_competitive_commission_rule_p_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_rule_promoter_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_rule_promoter_types (
    competitive_commission_rule_id bigint NOT NULL,
    promoter_type_id bigint NOT NULL
);

--
-- Name: competitive_commission_rule_ranks; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_rule_ranks (
    competitive_commission_rule_id bigint NOT NULL,
    rank_id bigint NOT NULL
);

--
-- Name: competitive_commission_rules; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_rules (
    competitive_commission_rules_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    metric character varying(40) NOT NULL,
    competition_type character varying(20) NOT NULL,
    threshold_count integer,
    threshold_amount numeric(14,2),
    threshold_currency_id bigint,
    achievement_date_basis character varying(20) DEFAULT 'APPROVED_AT'::character varying NOT NULL,
    tie_policy character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    competition_group character varying(60),
    group_priority smallint,
    accrual_period_strategy character varying(20) NOT NULL,
    partial_settlement_period_strategy character varying(20) NOT NULL,
    final_settlement_period_strategy character varying(20) NOT NULL,
    retroactive_settlement_period_strategy character varying(20) NOT NULL,
    accrual_period_anchor smallint,
    partial_settlement_period_anchor smallint,
    final_settlement_period_anchor smallint,
    retroactive_settlement_period_anchor smallint,
    confirmation_delay_days smallint DEFAULT 0 NOT NULL,
    campaign_id bigint,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    include_system_promoters boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_ccr_amount_currency CHECK (((threshold_amount IS NULL) OR (threshold_currency_id IS NOT NULL))),
    CONSTRAINT chk_ccr_dates_order CHECK (((starts_at IS NULL) OR (ends_at IS NULL) OR (ends_at > starts_at))),
    CONSTRAINT chk_ccr_end_date_accrual CHECK ((((accrual_period_strategy)::text <> 'END_DATE'::text) OR ((starts_at IS NOT NULL) AND (ends_at IS NOT NULL)))),
    CONSTRAINT chk_ccr_end_date_settlement CHECK (((ends_at IS NOT NULL) OR ((('END_DATE'::text <> (partial_settlement_period_strategy)::text) AND ('END_DATE'::text <> (final_settlement_period_strategy)::text)) AND ('END_DATE'::text <> (retroactive_settlement_period_strategy)::text)))),
    CONSTRAINT chk_ccr_ftr_threshold CHECK ((((competition_type)::text <> 'FIRST_TO_REACH'::text) OR (threshold_count IS NOT NULL) OR (threshold_amount IS NOT NULL))),
    CONSTRAINT chk_ccr_settlement_axes CHECK ((((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])) AND ((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])) AND ((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[])))),
    CONSTRAINT chk_ccr_threshold_xor CHECK ((NOT ((threshold_count IS NOT NULL) AND (threshold_amount IS NOT NULL)))),
    CONSTRAINT competitive_commission_rules_accrual_period_anchor_check CHECK (((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31))),
    CONSTRAINT competitive_commission_rules_accrual_period_strategy_check CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying, 'END_DATE'::character varying])::text[]))),
    CONSTRAINT competitive_commission_rules_achievement_date_basis_check CHECK (((achievement_date_basis)::text = ANY ((ARRAY['PAYMENT_DATE'::character varying, 'REGISTERED_AT'::character varying, 'APPROVED_AT'::character varying])::text[]))),
    CONSTRAINT competitive_commission_rules_competition_type_check CHECK (((competition_type)::text = ANY ((ARRAY['FIRST_TO_REACH'::character varying, 'RANKING'::character varying])::text[]))),
    CONSTRAINT competitive_commission_rules_confirmation_delay_days_check CHECK (((confirmation_delay_days >= 0) AND (confirmation_delay_days <= 60))),
    CONSTRAINT competitive_commission_rules_final_settlement_period_anch_check CHECK (((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31))),
    CONSTRAINT competitive_commission_rules_group_priority_check CHECK ((group_priority >= 1)),
    CONSTRAINT competitive_commission_rules_metric_check CHECK (((metric)::text = ANY ((ARRAY['NEW_SUBSCRIBERS'::character varying, 'ACTIVE_SUBSCRIBERS'::character varying, 'SALES_COUNT'::character varying, 'SALES_AMOUNT'::character varying, 'COLLECTION_COUNT'::character varying, 'COLLECTION_AMOUNT'::character varying, 'ADVANCE_COUNT'::character varying, 'ADVANCE_AMOUNT'::character varying, 'COMMISSION_EARNED'::character varying, 'OVERDUE_SETTLED_COUNT'::character varying, 'OVERDUE_SETTLED_AMOUNT'::character varying])::text[]))),
    CONSTRAINT competitive_commission_rules_partial_settlement_period_an_check CHECK (((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31))),
    CONSTRAINT competitive_commission_rules_retroactive_settlement_perio_check CHECK (((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31))),
    CONSTRAINT competitive_commission_rules_threshold_amount_check CHECK ((threshold_amount > (0)::numeric)),
    CONSTRAINT competitive_commission_rules_threshold_count_check CHECK ((threshold_count > 0)),
    CONSTRAINT competitive_commission_rules_tie_policy_check CHECK (((tie_policy)::text = ANY ((ARRAY['STRICT'::character varying, 'SHARED_FULL'::character varying, 'SHARED_SPLIT'::character varying, 'MANUAL'::character varying])::text[])))
);

--
-- Name: competitive_commission_rules_competitive_commission_rules_i_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_rules ALTER COLUMN competitive_commission_rules_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_rules_competitive_commission_rules_i_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: competitive_commission_tie_candidates; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_tie_candidates (
    competitive_commission_tie_id bigint NOT NULL,
    promoter_id bigint NOT NULL,
    metric_value numeric(14,2) NOT NULL,
    achieved_at timestamp with time zone,
    metric_transaction_count integer NOT NULL,
    selected boolean DEFAULT false NOT NULL
);

--
-- Name: competitive_commission_ties; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.competitive_commission_ties (
    competitive_commission_ties_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    competitive_commission_rule_id bigint NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    position_from integer NOT NULL,
    slots integer NOT NULL,
    resolved_by uuid,
    resolved_at timestamp with time zone,
    reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_cct_resolved CHECK ((((status)::text <> 'RESOLVED'::text) OR ((resolved_at IS NOT NULL) AND (length(TRIM(BOTH FROM reason)) >= 10)))),
    CONSTRAINT competitive_commission_ties_position_from_check CHECK ((position_from >= 1)),
    CONSTRAINT competitive_commission_ties_slots_check CHECK ((slots >= 1)),
    CONSTRAINT competitive_commission_ties_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'RESOLVED'::character varying, 'STALE'::character varying])::text[])))
);

--
-- Name: competitive_commission_ties_competitive_commission_ties_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.competitive_commission_ties ALTER COLUMN competitive_commission_ties_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.competitive_commission_ties_competitive_commission_ties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: contact_messages; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.contact_messages (
    contact_messages_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(150) NOT NULL,
    email character varying(254) NOT NULL,
    phone character varying(30),
    subject character varying(200) NOT NULL,
    message text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'new'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT contact_messages_status_check CHECK (((status)::text = ANY ((ARRAY['new'::character varying, 'read'::character varying, 'archived'::character varying])::text[])))
);

--
-- Name: contact_messages_contact_messages_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.contact_messages ALTER COLUMN contact_messages_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.contact_messages_contact_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: corporate_contracts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.corporate_contracts (
    corporate_contracts_id bigint NOT NULL,
    uuid uuid NOT NULL,
    plan_id bigint NOT NULL,
    institution_name character varying(200) NOT NULL,
    institution_tax_id character varying(20) NOT NULL,
    contact_user_id bigint,
    payer_mode character varying(20) NOT NULL,
    expected_member_count integer,
    actual_member_count integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_corporate_contracts_actual_count CHECK ((actual_member_count >= 0)),
    CONSTRAINT chk_corporate_contracts_payer_mode CHECK (((payer_mode)::text = ANY ((ARRAY['INSTITUTION_BULK'::character varying, 'INDIVIDUAL_PAYER'::character varying])::text[]))),
    CONSTRAINT corporate_contracts_expected_member_count_check CHECK (((expected_member_count IS NULL) OR (expected_member_count >= 0)))
);

--
-- Name: corporate_contracts_corporate_contracts_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.corporate_contracts ALTER COLUMN corporate_contracts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.corporate_contracts_corporate_contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: countries; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.countries (
    countries_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    iso_code character varying(2) NOT NULL,
    name character varying(100) NOT NULL,
    locale character varying(10),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    official_currency_id bigint
);

--
-- Name: countries_countries_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.countries ALTER COLUMN countries_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.countries_countries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: currencies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.currencies (
    currencies_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(4) NOT NULL,
    name character varying(60) NOT NULL,
    symbol character varying(6) NOT NULL,
    decimal_places smallint DEFAULT 2 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT currencies_decimal_places_check CHECK ((decimal_places >= 0))
);

--
-- Name: currencies_currencies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.currencies ALTER COLUMN currencies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.currencies_currencies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: data_change_audit_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.data_change_audit_log (
    data_change_audit_log_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    entity_key character varying(80) NOT NULL,
    entity_id bigint,
    entity_uuid uuid,
    action character varying(20) NOT NULL,
    before_json jsonb,
    after_json jsonb,
    actor_id bigint,
    login_audit_log_id bigint,
    request_method character varying(10),
    request_path character varying(255),
    restored_from_id bigint,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_data_change_action CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);

--
-- Name: data_change_audit_log_data_change_audit_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.data_change_audit_log ALTER COLUMN data_change_audit_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.data_change_audit_log_data_change_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: members; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.members (
    members_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id bigint NOT NULL,
    occupation_id bigint,
    enrolled_at date DEFAULT CURRENT_DATE NOT NULL,
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    promoter_id bigint,
    referral_code character varying(20),
    employer_name character varying(150),
    job_position character varying(100),
    employer_address text,
    corporate_contract_id bigint,
    confirmed_at timestamp with time zone
);

--
-- Name: COLUMN members.confirmed_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.members.confirmed_at IS 'Set automatically on the member''s first approved payment, or manually by an admin (MEMBER_CONFIRM) for subsidized members that never pay. NULL = pending confirmation.';

--
-- Name: memberships; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.memberships (
    memberships_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    member_id bigint NOT NULL,
    plan_id bigint NOT NULL,
    enrolled_at date DEFAULT CURRENT_DATE NOT NULL,
    expires_at date,
    next_due_date date NOT NULL,
    last_paid_through date,
    inscription_fee numeric(10,2) NOT NULL,
    monthly_fee numeric(10,2) NOT NULL,
    grace_period_days integer NOT NULL,
    last_status_change_at timestamp with time zone,
    last_status_change_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    billing_start_day integer,
    currency_id bigint NOT NULL,
    campaign_id bigint,
    contract_number character varying(20) NOT NULL,
    reinscription_required boolean DEFAULT false NOT NULL,
    term_mode character varying(20),
    term_fixed_month integer,
    term_fixed_day integer,
    term_period_months integer,
    term_started_at date,
    inscription_due_since date,
    CONSTRAINT chk_memberships_billing_start_day CHECK (((billing_start_day >= 1) AND (billing_start_day <= 28))),
    CONSTRAINT chk_memberships_expires_after_enrolled CHECK (((expires_at IS NULL) OR (expires_at >= enrolled_at))),
    CONSTRAINT chk_memberships_inscription_due CHECK (((inscription_due_since IS NULL) OR (reinscription_required = true))),
    CONSTRAINT chk_memberships_next_due_after_enrolled CHECK ((next_due_date >= enrolled_at)),
    CONSTRAINT chk_memberships_paid_through_after_enrolled CHECK (((last_paid_through IS NULL) OR (last_paid_through >= enrolled_at))),
    CONSTRAINT chk_memberships_term_coherence CHECK ((((reinscription_required = false) AND (term_mode IS NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'FIXED_DATE'::text) AND (term_fixed_month IS NOT NULL) AND (term_fixed_day IS NOT NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'PERIOD'::text) AND (term_period_months IS NOT NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL)))),
    CONSTRAINT chk_memberships_term_day CHECK (((term_fixed_day IS NULL) OR ((term_fixed_day >= 1) AND (term_fixed_day <= 28)))),
    CONSTRAINT chk_memberships_term_mode CHECK (((term_mode IS NULL) OR ((term_mode)::text = ANY ((ARRAY['FIXED_DATE'::character varying, 'PERIOD'::character varying])::text[])))),
    CONSTRAINT chk_memberships_term_month CHECK (((term_fixed_month IS NULL) OR ((term_fixed_month >= 1) AND (term_fixed_month <= 12)))),
    CONSTRAINT chk_memberships_term_period CHECK (((term_period_months IS NULL) OR (term_period_months > 0))),
    CONSTRAINT memberships_grace_period_days_check CHECK ((grace_period_days >= 0)),
    CONSTRAINT memberships_inscription_fee_check CHECK ((inscription_fee >= (0)::numeric)),
    CONSTRAINT memberships_monthly_fee_check CHECK ((monthly_fee >= (0)::numeric)),
    CONSTRAINT memberships_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'EXPIRED'::character varying, 'CANCELED'::character varying])::text[])))
);

--
-- Name: COLUMN memberships.billing_start_day; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.memberships.billing_start_day IS 'Billing cutover day of month (1-28). NULL = derive from enrolled_at day-of-month. Anchors the collection-commission engine''s scheduled collection date.';

--
-- Name: COLUMN memberships.contract_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.memberships.contract_number IS 'Número de contrato legible (OB-000123), asignado al inscribir; es lo que cita quien paga.';

--
-- Name: COLUMN memberships.reinscription_required; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.memberships.reinscription_required IS 'Snapshot del plan (editable por admin): exige reinscripción en cada vigencia.';

--
-- Name: COLUMN memberships.term_started_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.memberships.term_started_at IS 'Inicio de la vigencia actual; expires_at es su fin.';

--
-- Name: COLUMN memberships.inscription_due_since; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.memberships.inscription_due_since IS 'Fecha desde la que hay una reinscripción pendiente; la membresía queda SUSPENDED hasta aplicar un pago de inscripción aprobado.';

--
-- Name: persons; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.persons (
    persons_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    first_name character varying(50) NOT NULL,
    middle_name character varying(50),
    last_name character varying(50) NOT NULL,
    second_last_name character varying(50),
    full_name character varying(210) GENERATED ALWAYS AS (TRIM(BOTH FROM regexp_replace((((((((COALESCE(first_name, ''::character varying))::text || ' '::text) || (COALESCE(middle_name, ''::character varying))::text) || ' '::text) || (COALESCE(last_name, ''::character varying))::text) || ' '::text) || (COALESCE(second_last_name, ''::character varying))::text), '\s+'::text, ' '::text, 'g'::text))) STORED,
    document_type character varying(2) NOT NULL,
    document_number character varying(20) NOT NULL,
    tax_document_type character varying(1),
    tax_document_number character varying(20),
    birth_date date,
    gender_id bigint,
    marital_status_id bigint,
    phone character varying(30),
    email app.citext,
    locale character varying(10),
    address text,
    city_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    birthplace character varying(120),
    number_of_children integer,
    landline_phone character varying(30),
    spouse_name character varying(210),
    credit_auto_apply boolean DEFAULT true NOT NULL,
    credit_hold_reason character varying(255),
    credit_hold_by uuid,
    credit_hold_at timestamp with time zone,
    CONSTRAINT chk_persons_number_of_children CHECK (((number_of_children IS NULL) OR (number_of_children >= 0))),
    CONSTRAINT chk_persons_tax_document CHECK ((((tax_document_type IS NULL) AND (tax_document_number IS NULL)) OR ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL)))),
    CONSTRAINT persons_document_type_check CHECK (((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[]))),
    CONSTRAINT persons_tax_document_type_check CHECK (((tax_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[])))
);

--
-- Name: COLUMN persons.credit_auto_apply; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.persons.credit_auto_apply IS 'FALSE = el saldo a favor de la persona no se aplica automáticamente (reclamo, reembolso en curso); lo controla el personal.';

--
-- Name: plans; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.plans (
    plans_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    type character varying(20) NOT NULL,
    inscription_fee numeric(10,2) NOT NULL,
    monthly_fee numeric(10,2) NOT NULL,
    included_beneficiaries integer DEFAULT 0 NOT NULL,
    max_beneficiaries integer,
    extra_beneficiary_inscription_fee numeric(10,2),
    grace_period_days integer DEFAULT 7 NOT NULL,
    is_published boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    currency_id bigint NOT NULL,
    min_members integer,
    reinscription_required boolean DEFAULT false NOT NULL,
    term_mode character varying(20),
    term_fixed_month integer,
    term_fixed_day integer,
    term_period_months integer,
    CONSTRAINT chk_plans_term_coherence CHECK ((((reinscription_required = false) AND (term_mode IS NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'FIXED_DATE'::text) AND (term_fixed_month IS NOT NULL) AND (term_fixed_day IS NOT NULL) AND (term_period_months IS NULL)) OR ((reinscription_required = true) AND ((term_mode)::text = 'PERIOD'::text) AND (term_period_months IS NOT NULL) AND (term_fixed_month IS NULL) AND (term_fixed_day IS NULL)))),
    CONSTRAINT chk_plans_term_day CHECK (((term_fixed_day IS NULL) OR ((term_fixed_day >= 1) AND (term_fixed_day <= 28)))),
    CONSTRAINT chk_plans_term_mode CHECK (((term_mode IS NULL) OR ((term_mode)::text = ANY ((ARRAY['FIXED_DATE'::character varying, 'PERIOD'::character varying])::text[])))),
    CONSTRAINT chk_plans_term_month CHECK (((term_fixed_month IS NULL) OR ((term_fixed_month >= 1) AND (term_fixed_month <= 12)))),
    CONSTRAINT chk_plans_term_period CHECK (((term_period_months IS NULL) OR (term_period_months > 0))),
    CONSTRAINT ck_plans_min_members CHECK (((min_members IS NULL) OR (min_members >= 1))),
    CONSTRAINT plans_check CHECK (((max_beneficiaries IS NULL) OR (max_beneficiaries >= included_beneficiaries))),
    CONSTRAINT plans_extra_beneficiary_inscription_fee_check CHECK (((extra_beneficiary_inscription_fee IS NULL) OR (extra_beneficiary_inscription_fee >= (0)::numeric))),
    CONSTRAINT plans_grace_period_days_check CHECK ((grace_period_days >= 0)),
    CONSTRAINT plans_included_beneficiaries_check CHECK ((included_beneficiaries >= 0)),
    CONSTRAINT plans_inscription_fee_check CHECK ((inscription_fee >= (0)::numeric)),
    CONSTRAINT plans_monthly_fee_check CHECK ((monthly_fee >= (0)::numeric)),
    CONSTRAINT plans_type_check CHECK (((type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'FAMILIAR'::character varying, 'CORPORATIVO'::character varying])::text[])))
);

--
-- Name: COLUMN plans.min_members; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.plans.min_members IS 'Minimum number of people the plan requires (e.g. 20 for CORPORATIVO); NULL = no minimum';

--
-- Name: COLUMN plans.reinscription_required; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.plans.reinscription_required IS 'El plan exige reinscripción en cada vigencia; sin esto la membresía queda abierta (sin vencimiento).';

--
-- Name: COLUMN plans.term_mode; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.plans.term_mode IS 'FIXED_DATE (fecha anual fija) o PERIOD (N meses desde la afiliación).';

--
-- Name: users; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.users (
    users_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    email app.citext NOT NULL,
    password_hash character varying(255) NOT NULL,
    default_role_id bigint,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    password_reset_token character varying(255),
    password_reset_expires_at timestamp with time zone,
    last_login_at timestamp with time zone,
    password_never_expires boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    person_id bigint NOT NULL,
    login_token_hash character varying(255),
    login_token_expires_at timestamp with time zone,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'LOCKED'::character varying])::text[])))
);

--
-- Name: COLUMN users.login_token_hash; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.users.login_token_hash IS 'SHA-256 of the single-use access-link token (15 min TTL); NULL when none is pending.';

--
-- Name: COLUMN users.login_token_expires_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.users.login_token_expires_at IS 'Expiry of login_token_hash; the link is also invalidated by its first use.';

--
-- Name: digital_cards_view; Type: VIEW; Schema: app; Owner: -
--

CREATE VIEW app.digital_cards_view AS
 SELECT m.uuid AS member_uuid,
    u.uuid AS user_uuid,
    p.full_name,
    p.document_type,
    p.document_number,
    pl.name AS plan_name,
    ms.status AS membership_status,
    ms.next_due_date,
    m.enrolled_at AS member_since,
    ms.expires_at,
    ms.inscription_due_since
   FROM ((((app.members m
     JOIN app.persons p ON ((p.persons_id = m.person_id)))
     LEFT JOIN app.users u ON ((u.person_id = p.persons_id)))
     LEFT JOIN app.memberships ms ON (((ms.member_id = m.members_id) AND (ms.is_active = true))))
     LEFT JOIN app.plans pl ON ((pl.plans_id = ms.plan_id)))
  WHERE (m.is_active = true);

--
-- Name: VIEW digital_cards_view; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON VIEW app.digital_cards_view IS 'Vertical-9 read model for the affiliate digital card: one row per active member with person, active membership + plan, and the linked user account.';

--
-- Name: document_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.document_types (
    document_types_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(3) NOT NULL,
    name character varying(60) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: document_types_document_types_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.document_types ALTER COLUMN document_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.document_types_document_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: exchange_rates; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.exchange_rates (
    exchange_rates_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    base_currency_id bigint NOT NULL,
    quote_currency_id bigint NOT NULL,
    rate numeric(18,8) NOT NULL,
    operation_date date NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    source character varying(30) NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_exchange_rates_currencies_distinct CHECK ((base_currency_id <> quote_currency_id)),
    CONSTRAINT chk_exchange_rates_source CHECK (((source)::text = ANY ((ARRAY['BCV'::character varying, 'EXCHANGE_RATES_API'::character varying, 'MANUAL'::character varying])::text[]))),
    CONSTRAINT exchange_rates_rate_check CHECK ((rate > (0)::numeric))
);

--
-- Name: exchange_rates_exchange_rates_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.exchange_rates ALTER COLUMN exchange_rates_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.exchange_rates_exchange_rates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: file_download_links; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.file_download_links (
    file_download_links_id bigint NOT NULL,
    token uuid DEFAULT gen_random_uuid() NOT NULL,
    visibility character varying(20) NOT NULL,
    resource_table character varying(50) NOT NULL,
    resource_uuid uuid NOT NULL,
    file_key character varying(500) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    max_downloads integer,
    download_count integer DEFAULT 0 NOT NULL,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT file_download_links_max_downloads_check CHECK (((max_downloads IS NULL) OR (max_downloads > 0))),
    CONSTRAINT file_download_links_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))
);

--
-- Name: file_download_links_file_download_links_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.file_download_links ALTER COLUMN file_download_links_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.file_download_links_file_download_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: file_type_policies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.file_type_policies (
    file_type_policies_id bigint NOT NULL,
    visibility character varying(20) NOT NULL,
    mode character varying(10) NOT NULL,
    extensions text[] NOT NULL,
    max_size_bytes bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by uuid,
    CONSTRAINT file_type_policies_max_size_bytes_check CHECK ((max_size_bytes > 0)),
    CONSTRAINT file_type_policies_mode_check CHECK (((mode)::text = ANY ((ARRAY['ALLOWLIST'::character varying, 'DENYLIST'::character varying])::text[]))),
    CONSTRAINT file_type_policies_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'TEMPORARY'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying])::text[])))
);

--
-- Name: file_type_policies_file_type_policies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.file_type_policies ALTER COLUMN file_type_policies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.file_type_policies_file_type_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: flyway_schema_history; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);

--
-- Name: genders; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.genders (
    genders_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(1) NOT NULL,
    name character varying(20) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: genders_genders_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.genders ALTER COLUMN genders_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.genders_genders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: hierarchy_override_tiers; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.hierarchy_override_tiers (
    hierarchy_override_tiers_id bigint NOT NULL,
    uuid uuid NOT NULL,
    name character varying(80) NOT NULL,
    rank_id bigint NOT NULL,
    category character varying(20) NOT NULL,
    threshold_count integer DEFAULT 0 NOT NULL,
    override_pct numeric(5,2),
    flat_amount numeric(10,2),
    flat_amount_currency_id bigint,
    accrual_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    partial_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    final_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    campaign_id bigint,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    description text,
    retroactive_settlement_period_strategy character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    accrual_period_anchor smallint,
    partial_settlement_period_anchor smallint,
    final_settlement_period_anchor smallint,
    retroactive_settlement_period_anchor smallint,
    basis character varying(10) DEFAULT 'COUNT'::character varying NOT NULL,
    threshold_amount numeric(14,2),
    threshold_amount_currency_id bigint,
    CONSTRAINT chk_hierarchy_override_tiers_accrual_period_anchor_range CHECK (((accrual_period_anchor IS NULL) OR ((accrual_period_anchor >= 1) AND (accrual_period_anchor <= 31)))),
    CONSTRAINT chk_hierarchy_override_tiers_accrual_period_strategy CHECK (((accrual_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_hierarchy_override_tiers_basis CHECK (((basis)::text = ANY ((ARRAY['COUNT'::character varying, 'AMOUNT'::character varying])::text[]))),
    CONSTRAINT chk_hierarchy_override_tiers_basis_field CHECK (((((basis)::text = 'COUNT'::text) AND (threshold_amount IS NULL) AND (threshold_amount_currency_id IS NULL)) OR (((basis)::text = 'AMOUNT'::text) AND (threshold_amount IS NOT NULL) AND (threshold_amount_currency_id IS NOT NULL)))),
    CONSTRAINT chk_hierarchy_override_tiers_category CHECK (((category)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'COLLECTION'::character varying])::text[]))),
    CONSTRAINT chk_hierarchy_override_tiers_final_settlement_period_anchor_ran CHECK (((final_settlement_period_anchor IS NULL) OR ((final_settlement_period_anchor >= 1) AND (final_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_hierarchy_override_tiers_final_settlement_period_strategy CHECK (((final_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_hierarchy_override_tiers_flat_needs_currency CHECK (((flat_amount IS NULL) OR (flat_amount_currency_id IS NOT NULL))),
    CONSTRAINT chk_hierarchy_override_tiers_partial_settlement_period_anchor_r CHECK (((partial_settlement_period_anchor IS NULL) OR ((partial_settlement_period_anchor >= 1) AND (partial_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_hierarchy_override_tiers_partial_settlement_period_strategy CHECK (((partial_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT chk_hierarchy_override_tiers_pct_xor_flat CHECK (((override_pct IS NOT NULL) <> (flat_amount IS NOT NULL))),
    CONSTRAINT chk_hierarchy_override_tiers_retroactive_settlement_period_anch CHECK (((retroactive_settlement_period_anchor IS NULL) OR ((retroactive_settlement_period_anchor >= 1) AND (retroactive_settlement_period_anchor <= 31)))),
    CONSTRAINT chk_hierarchy_override_tiers_retroactive_settlement_period_stra CHECK (((retroactive_settlement_period_strategy)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'BIWEEKLY'::character varying, 'MONTHLY'::character varying, 'QUARTERLY'::character varying, 'SEMIANNUAL'::character varying, 'ANNUAL'::character varying])::text[])))
);

--
-- Name: hierarchy_override_tiers_hierarchy_override_tiers_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.hierarchy_override_tiers ALTER COLUMN hierarchy_override_tiers_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.hierarchy_override_tiers_hierarchy_override_tiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: holidays; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.holidays (
    holidays_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(50),
    name character varying(150) NOT NULL,
    description character varying(255),
    holiday_date date NOT NULL,
    is_recurring boolean DEFAULT false NOT NULL,
    recurrence_type character varying(12) DEFAULT 'NONE'::character varying NOT NULL,
    repetitions_count integer DEFAULT 0 NOT NULL,
    country_id bigint,
    state_id bigint,
    city_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_holidays_city_requires_state CHECK (((city_id IS NULL) OR (state_id IS NOT NULL))),
    CONSTRAINT chk_holidays_recurrence_coherence CHECK ((((is_recurring = false) AND ((recurrence_type)::text = 'NONE'::text)) OR ((is_recurring = true) AND ((recurrence_type)::text <> 'NONE'::text)))),
    CONSTRAINT chk_holidays_state_requires_country CHECK (((state_id IS NULL) OR (country_id IS NOT NULL))),
    CONSTRAINT holidays_recurrence_type_check CHECK (((recurrence_type)::text = ANY ((ARRAY['NONE'::character varying, 'WEEKLY'::character varying, 'MONTHLY'::character varying, 'ANNUAL'::character varying])::text[]))),
    CONSTRAINT holidays_repetitions_count_check CHECK ((repetitions_count >= 0))
);

--
-- Name: holidays_holidays_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.holidays ALTER COLUMN holidays_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.holidays_holidays_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: licensing_bodies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.licensing_bodies (
    licensing_bodies_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(200),
    professional_area character varying(20),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_licensing_bodies_professional_area CHECK (((professional_area IS NULL) OR ((professional_area)::text = ANY ((ARRAY['MEDICAL'::character varying, 'WELLNESS'::character varying])::text[]))))
);

--
-- Name: licensing_bodies_licensing_bodies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.licensing_bodies ALTER COLUMN licensing_bodies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.licensing_bodies_licensing_bodies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: login_audit_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.login_audit_log (
    login_audit_log_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id bigint,
    attempted_email character varying(255) NOT NULL,
    result character varying(20) NOT NULL,
    roles jsonb,
    locale character varying(10),
    ip_address inet,
    user_agent character varying(500),
    hostname character varying(255),
    jti character varying(36),
    failure_reason character varying(255),
    session_status character varying(20),
    session_expires_at timestamp with time zone,
    is_valid boolean DEFAULT true NOT NULL,
    logged_out_at timestamp with time zone,
    logout_reason character varying(50),
    attempted_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    active_role_id bigint,
    CONSTRAINT chk_login_audit_result CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED_CREDENTIALS'::character varying, 'FAILED_LOCKED'::character varying, 'FAILED_INACTIVE'::character varying])::text[]))),
    CONSTRAINT chk_login_audit_session_status CHECK (((session_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOGGED_OUT'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))
)
WITH (fillfactor='90', autovacuum_vacuum_scale_factor='0.05', autovacuum_analyze_scale_factor='0.02');

--
-- Name: login_audit_log_login_audit_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.login_audit_log ALTER COLUMN login_audit_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.login_audit_log_login_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: marital_statuses; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.marital_statuses (
    marital_statuses_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(50) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: marital_statuses_marital_statuses_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.marital_statuses ALTER COLUMN marital_statuses_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.marital_statuses_marital_statuses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: media_items; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.media_items (
    media_items_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_table character varying(50) NOT NULL,
    owner_uuid uuid NOT NULL,
    attached_file_id bigint,
    public_key character varying(500),
    external_url character varying(500),
    caption character varying(200),
    sort_order integer DEFAULT 0 NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_media_items_one_source CHECK (((public_key IS NULL) <> (external_url IS NULL)))
);

--
-- Name: TABLE media_items; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.media_items IS 'Lista ordenada de imágenes de un dueño (aliado, producto, oferta…), con una principal; archivo en R2 o URL externa (ADR 0027).';

--
-- Name: media_items_media_items_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.media_items ALTER COLUMN media_items_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.media_items_media_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: medical_record_access_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.medical_record_access_log (
    medical_record_access_log_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    link_id bigint NOT NULL,
    accessed_by_user_id bigint NOT NULL,
    ally_id bigint NOT NULL,
    member_id bigint NOT NULL,
    accessed_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE medical_record_access_log; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.medical_record_access_log IS 'Bitácora de solo-agregar de cada lectura de historia médica por un aliado médico (ADR 0032). No se purga.';

--
-- Name: medical_record_access_log_medical_record_access_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.medical_record_access_log ALTER COLUMN medical_record_access_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.medical_record_access_log_medical_record_access_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: medical_records; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.medical_records (
    medical_records_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id bigint NOT NULL,
    blood_type character varying(5),
    allergies text,
    chronic_conditions text,
    current_medications text,
    emergency_contact_name character varying(200),
    emergency_contact_phone character varying(30),
    emergency_contact_relationship character varying(20),
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT medical_records_blood_type_check CHECK (((blood_type)::text = ANY ((ARRAY['A+'::character varying, 'A-'::character varying, 'B+'::character varying, 'B-'::character varying, 'AB+'::character varying, 'AB-'::character varying, 'O+'::character varying, 'O-'::character varying])::text[]))),
    CONSTRAINT medical_records_emergency_contact_relationship_check CHECK (((emergency_contact_relationship)::text = ANY ((ARRAY['SPOUSE'::character varying, 'CHILD'::character varying, 'PARENT'::character varying, 'SIBLING'::character varying, 'FRIEND'::character varying, 'OTHER'::character varying])::text[])))
);

--
-- Name: medical_records_medical_records_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.medical_records ALTER COLUMN medical_records_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.medical_records_medical_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: professions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.professions (
    professions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    parent_id bigint,
    CONSTRAINT ck_professions_not_own_parent CHECK (((parent_id IS NULL) OR (parent_id <> professions_id)))
);

--
-- Name: COLUMN professions.parent_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.professions.parent_id IS 'Profesión general de una sub-especialidad (máximo dos niveles)';

--
-- Name: medical_specialties_medical_specialties_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.professions ALTER COLUMN professions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.medical_specialties_medical_specialties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_documents; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.member_documents (
    member_documents_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    member_id bigint NOT NULL,
    document_type character varying(50) NOT NULL,
    file_url character varying(500) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_size_bytes bigint NOT NULL,
    mime_type character varying(100) NOT NULL,
    uploaded_by bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT member_documents_document_type_check CHECK (((document_type)::text = ANY ((ARRAY['ID_FRONT'::character varying, 'ID_BACK'::character varying, 'PROOF_OF_RESIDENCE'::character varying, 'MEDICAL_HISTORY'::character varying, 'MEMBER_PHOTO'::character varying, 'INCOME_PROOF'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT member_documents_file_size_bytes_check CHECK ((file_size_bytes > 0))
);

--
-- Name: member_documents_member_documents_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.member_documents ALTER COLUMN member_documents_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.member_documents_member_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_promoter_assignments; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.member_promoter_assignments (
    member_promoter_assignments_id bigint NOT NULL,
    uuid uuid NOT NULL,
    member_id bigint NOT NULL,
    from_promoter_id bigint,
    to_promoter_id bigint NOT NULL,
    actor_user_id bigint,
    reason text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: member_promoter_assignments_member_promoter_assignments_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.member_promoter_assignments ALTER COLUMN member_promoter_assignments_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.member_promoter_assignments_member_promoter_assignments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: members_members_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.members ALTER COLUMN members_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.members_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: membership_charges; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.membership_charges (
    membership_charges_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    membership_id bigint NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    due_date date NOT NULL,
    amount numeric(10,2) NOT NULL,
    currency_id bigint NOT NULL,
    covered_by_payment_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    gross_amount numeric(10,2) NOT NULL,
    discount_amount numeric(10,2) DEFAULT 0 NOT NULL,
    discount_source character varying(20),
    membership_promotion_id bigint,
    CONSTRAINT chk_membership_charges_discount_source CHECK (((discount_source IS NULL) OR ((discount_source)::text = ANY ((ARRAY['PROMOTION'::character varying, 'SUBSIDY'::character varying])::text[])))),
    CONSTRAINT chk_membership_charges_net CHECK (((discount_amount >= (0)::numeric) AND (discount_amount <= gross_amount) AND (amount = (gross_amount - discount_amount)))),
    CONSTRAINT chk_membership_charges_period_end_after_start CHECK ((period_end >= period_start)),
    CONSTRAINT membership_charges_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT membership_charges_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COVERED'::character varying, 'OVERDUE'::character varying, 'WAIVED'::character varying])::text[])))
);

--
-- Name: membership_charges_membership_charges_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.membership_charges ALTER COLUMN membership_charges_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.membership_charges_membership_charges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: membership_contract_seq; Type: SEQUENCE; Schema: app; Owner: -
--

CREATE SEQUENCE app.membership_contract_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: membership_promotions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.membership_promotions (
    membership_promotions_id bigint NOT NULL,
    uuid uuid NOT NULL,
    membership_id bigint NOT NULL,
    promotion_id bigint NOT NULL,
    origin character varying(20) NOT NULL,
    code_used character varying(20),
    code_owner_promoter_id bigint,
    code_owner_member_id bigint,
    code_owner_ally_id bigint,
    cycles_remaining integer,
    inscription_applied boolean DEFAULT false NOT NULL,
    referrer_reward_granted boolean DEFAULT false NOT NULL,
    assigned_by_user_id bigint,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    ended_at timestamp with time zone,
    ended_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_membership_promotions_cycles CHECK (((cycles_remaining IS NULL) OR (cycles_remaining >= 0))),
    CONSTRAINT chk_membership_promotions_ended CHECK ((((status)::text = 'ACTIVE'::text) OR (ended_at IS NOT NULL))),
    CONSTRAINT chk_membership_promotions_origin CHECK (((origin)::text = ANY ((ARRAY['ENROLLMENT'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_membership_promotions_single_owner CHECK ((num_nonnulls(code_owner_promoter_id, code_owner_member_id, code_owner_ally_id) <= 1)),
    CONSTRAINT chk_membership_promotions_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CONSUMED'::character varying, 'CANCELED'::character varying])::text[])))
);

--
-- Name: membership_promotions_membership_promotions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.membership_promotions ALTER COLUMN membership_promotions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.membership_promotions_membership_promotions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: memberships_memberships_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.memberships ALTER COLUMN memberships_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.memberships_memberships_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: notifications; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.notifications (
    notifications_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    channel character varying(20) DEFAULT 'EMAIL'::character varying NOT NULL,
    recipient_email app.citext NOT NULL,
    recipient_user_id bigint,
    recipient_locale character varying(10) DEFAULT 'es'::character varying NOT NULL,
    template_code character varying(80) NOT NULL,
    subject character varying(200) NOT NULL,
    template_vars jsonb DEFAULT '{}'::jsonb NOT NULL,
    source_module character varying(40),
    source_entity_uuid uuid,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    scheduled_for timestamp with time zone DEFAULT now() NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    last_attempt_at timestamp with time zone,
    next_retry_at timestamp with time zone,
    last_error_message text,
    sent_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_notifications_attempt_bounds CHECK ((attempt_count <= max_attempts)),
    CONSTRAINT chk_notifications_dead_letter_exhausted CHECK ((((status)::text <> 'DEAD_LETTER'::text) OR (attempt_count >= max_attempts))),
    CONSTRAINT chk_notifications_failed_has_error CHECK ((((status)::text <> ALL ((ARRAY['FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])) OR (last_error_message IS NOT NULL))),
    CONSTRAINT chk_notifications_sent_has_sent_at CHECK ((((status)::text <> 'SENT'::text) OR (sent_at IS NOT NULL))),
    CONSTRAINT notifications_attempt_count_check CHECK ((attempt_count >= 0)),
    CONSTRAINT notifications_channel_check CHECK (((channel)::text = 'EMAIL'::text)),
    CONSTRAINT notifications_max_attempts_check CHECK ((max_attempts >= 1)),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])))
);

--
-- Name: notifications_notifications_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.notifications ALTER COLUMN notifications_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.notifications_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: occupations; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.occupations (
    occupations_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: occupations_occupations_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.occupations ALTER COLUMN occupations_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.occupations_occupations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: onboarding_policies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.onboarding_policies (
    onboarding_policies_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    allow_member_self_registration boolean DEFAULT true NOT NULL,
    allow_ally_self_registration boolean DEFAULT true NOT NULL,
    member_approval_mode character varying(30) DEFAULT 'MANUAL'::character varying NOT NULL,
    member_activation_mode character varying(30) DEFAULT 'ON_PAYMENT_APPROVED'::character varying NOT NULL,
    ally_approval_mode character varying(30) DEFAULT 'MANUAL_WITH_AGREEMENT'::character varying NOT NULL,
    ally_publish_mode character varying(30) DEFAULT 'MANUAL'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    member_draft_retention_days integer DEFAULT 15 NOT NULL,
    ally_draft_retention_days integer DEFAULT 30 NOT NULL,
    CONSTRAINT chk_onboarding_policies_ally_approval_mode CHECK (((ally_approval_mode)::text = ANY ((ARRAY['MANUAL_WITH_AGREEMENT'::character varying, 'AUTO_ON_SUBMIT'::character varying])::text[]))),
    CONSTRAINT chk_onboarding_policies_ally_draft_retention CHECK ((ally_draft_retention_days >= 1)),
    CONSTRAINT chk_onboarding_policies_ally_publish_mode CHECK (((ally_publish_mode)::text = ANY ((ARRAY['MANUAL'::character varying, 'ON_APPROVAL'::character varying])::text[]))),
    CONSTRAINT chk_onboarding_policies_member_activation_mode CHECK (((member_activation_mode)::text = ANY ((ARRAY['ON_PAYMENT_APPROVED'::character varying, 'ON_PAYMENT_REPORTED'::character varying, 'IMMEDIATE'::character varying])::text[]))),
    CONSTRAINT chk_onboarding_policies_member_approval_mode CHECK (((member_approval_mode)::text = ANY ((ARRAY['MANUAL'::character varying, 'AUTO_IF_PAYMENT_REPORTED'::character varying, 'AUTO_ON_SUBMIT'::character varying])::text[]))),
    CONSTRAINT chk_onboarding_policies_member_draft_retention CHECK ((member_draft_retention_days >= 1))
);

--
-- Name: TABLE onboarding_policies; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.onboarding_policies IS 'Single active row: which self-registrations are open and how requests are approved and activated (hub ADR 0037).';

--
-- Name: COLUMN onboarding_policies.member_draft_retention_days; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.onboarding_policies.member_draft_retention_days IS 'Días sin cambios tras los cuales se descarta un borrador de afiliación abandonado';

--
-- Name: COLUMN onboarding_policies.ally_draft_retention_days; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.onboarding_policies.ally_draft_retention_days IS 'Días sin cambios tras los cuales se descarta un borrador de aliado abandonado';

--
-- Name: onboarding_policies_onboarding_policies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.onboarding_policies ALTER COLUMN onboarding_policies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.onboarding_policies_onboarding_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: organizations; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.organizations (
    organizations_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(150) NOT NULL,
    legal_name character varying(200),
    tax_identifier character varying(20),
    logo_key character varying(255),
    official_currency_id bigint NOT NULL,
    reference_currency_id bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    whatsapp character varying(30),
    instagram character varying(255),
    facebook character varying(255),
    auto_approve_commissions boolean DEFAULT false NOT NULL,
    registry_state character varying(80),
    signing_city character varying(80),
    service_area character varying(150),
    address text,
    email app.citext,
    phones character varying(100),
    signatory_name character varying(200),
    signatory_document_type character varying(1),
    signatory_document_number character varying(20),
    signatory_position character varying(100),
    CONSTRAINT chk_organizations_currencies_distinct CHECK ((official_currency_id <> reference_currency_id)),
    CONSTRAINT chk_organizations_signatory_document CHECK ((((signatory_document_type IS NULL) AND (signatory_document_number IS NULL)) OR ((signatory_document_type IS NOT NULL) AND (signatory_document_number IS NOT NULL)))),
    CONSTRAINT chk_organizations_signatory_document_type CHECK (((signatory_document_type IS NULL) OR ((signatory_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[]))))
);

--
-- Name: COLUMN organizations.registry_state; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.registry_state IS 'Estado del Registro Mercantil donde está inscrita la organización ({{org.registryState}} en los contratos)';

--
-- Name: COLUMN organizations.signing_city; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.signing_city IS 'Ciudad de firma de los contratos ({{org.city}})';

--
-- Name: COLUMN organizations.service_area; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.service_area IS 'Zona de cobertura de la red de aliados ({{org.serviceArea}})';

--
-- Name: COLUMN organizations.address; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.address IS 'Dirección de la organización ({{org.address}}, pie institucional de los contratos)';

--
-- Name: COLUMN organizations.email; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.email IS 'Correo de contacto de la organización ({{org.email}})';

--
-- Name: COLUMN organizations.phones; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.phones IS 'Teléfonos de contacto tal como se imprimen en los contratos ({{org.phones}})';

--
-- Name: COLUMN organizations.signatory_name; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.signatory_name IS 'Nombre del firmante autorizado de la empresa ({{org.signatoryName}})';

--
-- Name: COLUMN organizations.signatory_document_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.signatory_document_type IS 'Tipo de documento del firmante: J, V, E, G o P';

--
-- Name: COLUMN organizations.signatory_document_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.signatory_document_number IS 'Número de documento del firmante; con el tipo forma {{org.signatoryDocument}}';

--
-- Name: COLUMN organizations.signatory_position; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.organizations.signatory_position IS 'Cargo del firmante autorizado ({{org.signatoryPosition}})';

--
-- Name: organizations_organizations_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.organizations ALTER COLUMN organizations_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.organizations_organizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payer_bank_account_payment_methods; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payer_bank_account_payment_methods (
    payer_bank_account_id bigint NOT NULL,
    payment_method_id bigint NOT NULL
);

--
-- Name: TABLE payer_bank_account_payment_methods; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.payer_bank_account_payment_methods IS 'Métodos de pago que acepta cada cuenta propia de una persona; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';

--
-- Name: payer_bank_accounts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payer_bank_accounts (
    payer_bank_accounts_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id bigint NOT NULL,
    bank_id bigint,
    account_type character varying(40),
    account_code character varying(40),
    account_identifier character varying(120),
    identification character varying(80),
    alias character varying(80),
    last_used_at timestamp with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    phone character varying(30),
    email character varying(160)
);

--
-- Name: TABLE payer_bank_accounts; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.payer_bank_accounts IS 'Cuentas de origen que quien paga decidió recordar para no reescribirlas en el siguiente pago (hoy solo personas).';

--
-- Name: COLUMN payer_bank_accounts.phone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payer_bank_accounts.phone IS 'Teléfono de la cuenta (pago móvil).';

--
-- Name: COLUMN payer_bank_accounts.email; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payer_bank_accounts.email IS 'Correo de la cuenta (Zelle y similares).';

--
-- Name: payer_bank_accounts_payer_bank_accounts_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payer_bank_accounts ALTER COLUMN payer_bank_accounts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payer_bank_accounts_payer_bank_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payment_categories; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payment_categories (
    payment_categories_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(80) NOT NULL,
    description character varying(255),
    direction character varying(10) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT payment_categories_direction_check CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))
);

--
-- Name: payment_categories_payment_categories_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payment_categories ALTER COLUMN payment_categories_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payment_categories_payment_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payment_credit_claims; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payment_credit_claims (
    payment_credit_claims_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    payment_id bigint NOT NULL,
    reason character varying(20) NOT NULL,
    claimed_amount numeric(12,2) NOT NULL,
    comment character varying(500),
    claim_status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    opened_by bigint,
    opened_at timestamp with time zone DEFAULT now() NOT NULL,
    resolved_by bigint,
    resolved_at timestamp with time zone,
    resolution_note character varying(500),
    refunded_amount numeric(12,2),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_payment_credit_claims_amount CHECK ((claimed_amount > (0)::numeric)),
    CONSTRAINT chk_payment_credit_claims_reason CHECK (((reason)::text = ANY ((ARRAY['ERROR'::character varying, 'OVERPAID'::character varying, 'REFUND_REQUEST'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT chk_payment_credit_claims_status CHECK (((claim_status)::text = ANY ((ARRAY['OPEN'::character varying, 'KEPT_AS_CREDIT'::character varying, 'REFUNDED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'USED_BY_AGREEMENT'::character varying])::text[])))
);

--
-- Name: TABLE payment_credit_claims; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.payment_credit_claims IS 'Reclamo sobre el saldo restante de un pago aprobado (error, pago de más, reembolso); congela ese monto de la aplicación automática.';

--
-- Name: payment_credit_claims_payment_credit_claims_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payment_credit_claims ALTER COLUMN payment_credit_claims_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payment_credit_claims_payment_credit_claims_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payment_lines; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payment_lines (
    payment_line_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    payment_id bigint NOT NULL,
    payment_type_id bigint NOT NULL,
    bank_id bigint,
    amount numeric(10,2) NOT NULL,
    currency_id bigint NOT NULL,
    reference_number character varying(80),
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by bigint,
    reviewed_at timestamp with time zone,
    review_reason text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    identification character varying(80),
    bank_account_type character varying(40),
    bank_account_code character varying(40),
    bank_account_identifier character varying(120),
    phone character varying(40),
    email character varying(160),
    company_bank_account_id bigint,
    payout_account_id bigint,
    CONSTRAINT chk_payment_lines_rejection_has_reason CHECK ((((status)::text <> 'REJECTED'::text) OR (review_reason IS NOT NULL))),
    CONSTRAINT chk_payment_lines_review_consistency CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])) AND (reviewed_at IS NULL) AND (reviewed_by IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by IS NOT NULL)))),
    CONSTRAINT payment_lines_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT payment_lines_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

--
-- Name: COLUMN payment_lines.company_bank_account_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payment_lines.company_bank_account_id IS 'Cuenta de la empresa en la que el pagador indica haber pagado (opcional).';

--
-- Name: COLUMN payment_lines.payout_account_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payment_lines.payout_account_id IS 'Cuenta de cobro del asesor a la que se pagó (pagos de salida); la cuenta de origen es company_bank_account_id.';

--
-- Name: payment_lines_payment_line_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payment_lines ALTER COLUMN payment_line_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payment_lines_payment_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payment_methods; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payment_methods (
    payment_methods_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(80) NOT NULL,
    is_mandatory_bank_account boolean DEFAULT false NOT NULL,
    is_mandatory_phone boolean DEFAULT false NOT NULL,
    is_mandatory_email boolean DEFAULT false NOT NULL,
    is_mandatory_reference_number boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    description character varying(255),
    is_mandatory_identification boolean DEFAULT false NOT NULL,
    is_mandatory_account_type boolean DEFAULT false NOT NULL,
    is_mandatory_account_code boolean DEFAULT false NOT NULL,
    is_mandatory_bank boolean DEFAULT false NOT NULL,
    requires_approval boolean DEFAULT true NOT NULL
);

--
-- Name: payment_methods_payment_methods_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payment_methods ALTER COLUMN payment_methods_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payment_methods_payment_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: payments; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.payments (
    payments_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    membership_id bigint,
    payer_user_id bigint,
    amount numeric(10,2) NOT NULL,
    payment_date timestamp with time zone NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    inscription boolean DEFAULT false NOT NULL,
    applied_period date,
    support_file_url character varying(500),
    support_file_name character varying(255),
    support_file_content_type character varying(100),
    support_file_size_bytes bigint,
    admin_notes text,
    reviewed_by bigint,
    reviewed_at timestamp with time zone,
    review_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    corporate_contract_id bigint,
    discount_amount numeric(10,2),
    discount_reason text,
    discounted_by bigint,
    discounted_at timestamp with time zone,
    currency_id bigint NOT NULL,
    exchange_rate_used numeric(18,8),
    exchange_rate_date date,
    direction character varying(10) NOT NULL,
    payment_type_id bigint NOT NULL,
    person_id bigint NOT NULL,
    promoter_id bigint,
    campaign_id bigint,
    coverage_through_period date,
    beneficiary_person_id bigint,
    beneficiary_document character varying(30),
    beneficiary_contract_number character varying(20),
    CONSTRAINT chk_payments_date_not_future CHECK ((payment_date <= (CURRENT_TIMESTAMP + '1 day'::interval))),
    CONSTRAINT chk_payments_direction CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[]))),
    CONSTRAINT chk_payments_discount_amount CHECK (((discount_amount IS NULL) OR (discount_amount >= (0)::numeric))),
    CONSTRAINT chk_payments_discount_coherence CHECK (((discount_amount IS NULL) OR ((discount_reason IS NOT NULL) AND (discounted_at IS NOT NULL)))),
    CONSTRAINT chk_payments_discount_not_exceed CHECK (((discount_amount IS NULL) OR (discount_amount <= amount))),
    CONSTRAINT chk_payments_exchange_rate_paired CHECK ((((exchange_rate_used IS NULL) AND (exchange_rate_date IS NULL)) OR ((exchange_rate_used IS NOT NULL) AND (exchange_rate_date IS NOT NULL)))),
    CONSTRAINT chk_payments_inscription_no_period CHECK (((NOT inscription) OR (applied_period IS NULL))),
    CONSTRAINT chk_payments_membership_by_direction CHECK ((((direction)::text = 'IN'::text) OR (((direction)::text = 'OUT'::text) AND (membership_id IS NULL)))),
    CONSTRAINT chk_payments_rejection_has_reason CHECK ((((status)::text <> 'REJECTED'::text) OR (review_reason IS NOT NULL))),
    CONSTRAINT chk_payments_review_consistency CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])) AND (reviewed_at IS NULL) AND (reviewed_by IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])) AND (reviewed_at IS NOT NULL) AND (reviewed_by IS NOT NULL)))),
    CONSTRAINT payments_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT payments_exchange_rate_used_check CHECK (((exchange_rate_used IS NULL) OR (exchange_rate_used > (0)::numeric))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

--
-- Name: COLUMN payments.beneficiary_person_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payments.beneficiary_person_id IS 'Persona a quien se acredita el pago cuando quien paga no es el titular; NULL = se acredita a quien paga.';

--
-- Name: COLUMN payments.beneficiary_document; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payments.beneficiary_document IS 'Documento del beneficiario al momento del pago (referencia para cotejar con el banco).';

--
-- Name: COLUMN payments.beneficiary_contract_number; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.payments.beneficiary_contract_number IS 'Número de contrato de la membresía indicada al momento del pago.';

--
-- Name: payments_payments_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.payments ALTER COLUMN payments_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.payments_payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: permission_domains; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.permission_domains (
    permission_domains_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    icon character varying(60),
    description character varying(255),
    display_order integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: permission_domains_permission_domains_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.permission_domains ALTER COLUMN permission_domains_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.permission_domains_permission_domains_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: permissions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.permissions (
    permissions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    domain_id bigint NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: permissions_permissions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.permissions ALTER COLUMN permissions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.permissions_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: person_credit_entries; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.person_credit_entries (
    person_credit_entries_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id bigint NOT NULL,
    payment_id bigint NOT NULL,
    membership_id bigint,
    currency_id bigint NOT NULL,
    entry_type character varying(20) NOT NULL,
    amount numeric(12,2) NOT NULL,
    note character varying(255),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    charge_id bigint,
    converted_amount numeric(12,2),
    converted_currency_id bigint,
    exchange_rate_used numeric(18,8),
    exchange_rate_date date,
    CONSTRAINT chk_person_credit_entries_sign CHECK (((((entry_type)::text = 'CREDIT'::text) AND (amount > (0)::numeric)) OR (((entry_type)::text <> 'CREDIT'::text) AND (amount < (0)::numeric)))),
    CONSTRAINT chk_person_credit_entries_type CHECK (((entry_type)::text = ANY ((ARRAY['CREDIT'::character varying, 'APPLIED'::character varying, 'REVERSED'::character varying])::text[])))
);

--
-- Name: TABLE person_credit_entries; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.person_credit_entries IS 'Libro de saldo a favor por persona y moneda: CREDIT (pago aprobado sin membresía) / APPLIED (asignado a una membresía), importe con signo.';

--
-- Name: person_credit_entries_person_credit_entries_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.person_credit_entries ALTER COLUMN person_credit_entries_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.person_credit_entries_person_credit_entries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: person_professional_licenses; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.person_professional_licenses (
    person_professional_licenses_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    person_id bigint NOT NULL,
    licensing_body_id bigint NOT NULL,
    license_number character varying(40) NOT NULL,
    verification_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    verified_by uuid,
    verified_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_person_professional_licenses_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))),
    CONSTRAINT ck_person_professional_licenses_verification CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_person_professional_licenses_verified_stamp CHECK (((((verification_status)::text = 'PENDING'::text) AND (verified_at IS NULL)) OR (((verification_status)::text <> 'PENDING'::text) AND (verified_at IS NOT NULL))))
);

--
-- Name: person_professional_licenses_person_professional_licenses_i_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.person_professional_licenses ALTER COLUMN person_professional_licenses_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.person_professional_licenses_person_professional_licenses_i_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: persons_persons_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.persons ALTER COLUMN persons_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.persons_persons_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: plans_plans_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.plans ALTER COLUMN plans_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.plans_plans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: product_categories; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.product_categories (
    product_categories_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: TABLE product_categories; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.product_categories IS 'Clasificación de productos y servicios ofrecidos por aliados (antes service_categories, ADR 0026).';

--
-- Name: product_categories_product_categories_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.product_categories ALTER COLUMN product_categories_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.product_categories_product_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: products; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.products (
    products_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40),
    name character varying(200) NOT NULL,
    description text,
    product_type character varying(20) DEFAULT 'SERVICE'::character varying NOT NULL,
    product_category_id bigint NOT NULL,
    owner_ally_id bigint,
    requires_appointment boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    is_published boolean DEFAULT true NOT NULL,
    published_at timestamp with time zone,
    logo_url character varying(500),
    rating_avg numeric(3,2),
    rating_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_products_type CHECK (((product_type)::text = ANY ((ARRAY['SERVICE'::character varying, 'PRODUCT'::character varying])::text[])))
);

--
-- Name: TABLE products; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.products IS 'Catálogo maestro de productos y servicios (ADR 0026). owner_ally_id NULL = general; con valor = exclusivo de ese aliado.';

--
-- Name: COLUMN products.product_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.products.product_type IS 'SERVICE (consulta, examen, hospedaje…) o PRODUCT (lente, medicamento, repuesto…).';

--
-- Name: COLUMN products.is_published; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.products.is_published IS 'Aparece en el catálogo general y en la comparativa entre aliados. Sin publicar, se ve solo desde cada aliado que publica su oferta (ADR 0026).';

--
-- Name: COLUMN products.logo_url; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.products.logo_url IS 'URL de la imagen principal (media_items), sincronizada por el servicio de media; no editar a mano (ADR 0027).';

--
-- Name: COLUMN products.rating_avg; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.products.rating_avg IS 'Promedio de product_rating en todas las ofertas del producto; NULL sin votos.';

--
-- Name: products_products_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.products ALTER COLUMN products_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.products_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_bonus_awards; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_bonus_awards (
    promoter_bonus_awards_id bigint NOT NULL,
    uuid uuid NOT NULL,
    bonus_rule_id bigint NOT NULL,
    promoter_id bigint NOT NULL,
    window_start date NOT NULL,
    window_end date NOT NULL,
    blocks_awarded integer NOT NULL,
    metric_count integer NOT NULL,
    reward_type character varying(20) NOT NULL,
    flat_amount numeric(10,2),
    reward_pct numeric(5,2),
    basis_amount numeric(12,2),
    amount numeric(12,2) NOT NULL,
    rule_name_snapshot character varying(150) NOT NULL,
    evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
    payout_reference character varying(120),
    paid_at timestamp with time zone,
    voided_at timestamp with time zone,
    void_reason text,
    admin_notes text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    reward_currency_id bigint NOT NULL,
    exchange_rate_used numeric(18,8),
    exchange_rate_date date,
    cut_kind character varying(20) NOT NULL,
    cut_sequence smallint NOT NULL,
    cut_start date NOT NULL,
    cut_end date NOT NULL,
    CONSTRAINT chk_bonus_award_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_bonus_award_blocks_positive CHECK ((blocks_awarded > 0)),
    CONSTRAINT chk_bonus_award_paid CHECK ((((status)::text <> 'PAID'::text) OR ((paid_at IS NOT NULL) AND (payout_reference IS NOT NULL)))),
    CONSTRAINT chk_bonus_award_reward CHECK (((((reward_type)::text = 'FLAT'::text) AND (flat_amount IS NOT NULL) AND (reward_pct IS NULL)) OR (((reward_type)::text = 'PERCENTAGE'::text) AND (reward_pct IS NOT NULL) AND (flat_amount IS NULL)))),
    CONSTRAINT chk_bonus_award_reward_type CHECK (((reward_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[]))),
    CONSTRAINT chk_bonus_award_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[]))),
    CONSTRAINT chk_bonus_award_voided CHECK ((((status)::text <> 'VOIDED'::text) OR ((voided_at IS NOT NULL) AND (void_reason IS NOT NULL)))),
    CONSTRAINT chk_bonus_awards_exchange_rate_paired CHECK ((((exchange_rate_used IS NULL) AND (exchange_rate_date IS NULL)) OR ((exchange_rate_used IS NOT NULL) AND (exchange_rate_date IS NOT NULL)))),
    CONSTRAINT chk_promoter_bonus_awards_cut_kind CHECK (((cut_kind)::text = ANY ((ARRAY['PARTIAL'::character varying, 'RETROACTIVE'::character varying, 'FINAL'::character varying])::text[]))),
    CONSTRAINT promoter_bonus_awards_exchange_rate_used_check CHECK (((exchange_rate_used IS NULL) OR (exchange_rate_used > (0)::numeric)))
);

--
-- Name: promoter_bonus_awards_promoter_bonus_awards_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_bonus_awards ALTER COLUMN promoter_bonus_awards_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_bonus_awards_promoter_bonus_awards_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_hierarchy_overrides; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_hierarchy_overrides (
    promoter_hierarchy_overrides_id bigint NOT NULL,
    uuid uuid NOT NULL,
    promoter_id bigint NOT NULL,
    source_commission_id bigint,
    source_override_id bigint,
    category character varying(20) NOT NULL,
    basis_amount numeric(10,2) NOT NULL,
    tier_id bigint NOT NULL,
    amount numeric(10,2) NOT NULL,
    currency_id bigint NOT NULL,
    period_strategy character varying(20) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    earned_at timestamp with time zone DEFAULT now() NOT NULL,
    voided_at timestamp with time zone,
    void_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    payout_reference character varying(120),
    paid_at timestamp with time zone,
    payout_payment_id bigint,
    CONSTRAINT chk_promoter_hierarchy_overrides_category CHECK (((category)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'COLLECTION'::character varying])::text[]))),
    CONSTRAINT chk_promoter_hierarchy_overrides_source_xor CHECK (((source_commission_id IS NOT NULL) <> (source_override_id IS NOT NULL))),
    CONSTRAINT chk_promoter_hierarchy_overrides_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'VOIDED'::character varying])::text[])))
);

--
-- Name: promoter_hierarchy_overrides_promoter_hierarchy_overrides_i_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_hierarchy_overrides ALTER COLUMN promoter_hierarchy_overrides_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_hierarchy_overrides_promoter_hierarchy_overrides_i_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_member_contacts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_member_contacts (
    promoter_member_contacts_id bigint NOT NULL,
    uuid uuid NOT NULL,
    promoter_id bigint NOT NULL,
    member_id bigint NOT NULL,
    type character varying(20) NOT NULL,
    note text,
    promised_amount numeric(10,2),
    promised_at_date date,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    promised_currency_id bigint,
    CONSTRAINT chk_promoter_member_contacts_promised_currency_paired CHECK ((((promised_amount IS NULL) AND (promised_currency_id IS NULL)) OR ((promised_amount IS NOT NULL) AND (promised_currency_id IS NOT NULL)))),
    CONSTRAINT promoter_member_contacts_amount_positive CHECK (((promised_amount IS NULL) OR (promised_amount > (0)::numeric))),
    CONSTRAINT promoter_member_contacts_promise_coherence CHECK (((((type)::text = 'PAYMENT_PROMISE'::text) AND (promised_amount IS NOT NULL) AND (promised_at_date IS NOT NULL)) OR (((type)::text <> 'PAYMENT_PROMISE'::text) AND (promised_amount IS NULL) AND (promised_at_date IS NULL)))),
    CONSTRAINT promoter_member_contacts_type_check CHECK (((type)::text = ANY ((ARRAY['REMINDER'::character varying, 'PAYMENT_PROMISE'::character varying, 'NOTE'::character varying])::text[])))
);

--
-- Name: promoter_member_contacts_promoter_member_contacts_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_member_contacts ALTER COLUMN promoter_member_contacts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_member_contacts_promoter_member_contacts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_payout_account_payment_methods; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_payout_account_payment_methods (
    promoter_payout_account_id bigint NOT NULL,
    payment_method_id bigint NOT NULL
);

--
-- Name: TABLE promoter_payout_account_payment_methods; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.promoter_payout_account_payment_methods IS 'Métodos de pago que acepta cada cuenta de cobro del asesor; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';

--
-- Name: promoter_payout_accounts; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_payout_accounts (
    promoter_payout_accounts_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    promoter_id bigint NOT NULL,
    label character varying(60),
    bank_id bigint,
    account_type character varying(20),
    account_number character varying(34),
    account_identifier character varying(120),
    holder_name character varying(150),
    holder_document_type character varying(1),
    holder_document_number character varying(20),
    phone character varying(30),
    email character varying(160),
    is_default boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_promoter_payout_accounts_account_type CHECK (((account_type IS NULL) OR ((account_type)::text = ANY ((ARRAY['CORRIENTE'::character varying, 'AHORRO'::character varying])::text[])))),
    CONSTRAINT ck_promoter_payout_accounts_holder_document_pair CHECK ((((holder_document_type IS NULL) AND (holder_document_number IS NULL)) OR ((holder_document_type IS NOT NULL) AND (holder_document_number IS NOT NULL)))),
    CONSTRAINT ck_promoter_payout_accounts_holder_document_type CHECK (((holder_document_type IS NULL) OR ((holder_document_type)::text = ANY ((ARRAY['J'::character varying, 'V'::character varying, 'E'::character varying, 'G'::character varying, 'P'::character varying])::text[])))),
    CONSTRAINT ck_promoter_payout_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);

--
-- Name: TABLE promoter_payout_accounts; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.promoter_payout_accounts IS 'Cuentas de cobro del asesor (expediente, sección V); N por asesor, una predeterminada';

--
-- Name: COLUMN promoter_payout_accounts.account_type; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoter_payout_accounts.account_type IS 'Tipo de cuenta bancaria: CORRIENTE o AHORRO';

--
-- Name: COLUMN promoter_payout_accounts.account_identifier; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoter_payout_accounts.account_identifier IS 'Identificador libre (cuenta Ubii, cuenta internacional, billetera)';

--
-- Name: COLUMN promoter_payout_accounts.is_default; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoter_payout_accounts.is_default IS 'Cuenta predeterminada para prellenar pagos de salida (máximo una activa por asesor)';

--
-- Name: promoter_payout_accounts_promoter_payout_accounts_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_payout_accounts ALTER COLUMN promoter_payout_accounts_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_payout_accounts_promoter_payout_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_ranks; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_ranks (
    promoter_ranks_id bigint NOT NULL,
    uuid uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    hierarchy_level integer NOT NULL,
    max_subordinates integer,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    parent_rank_id bigint,
    max_discount_pct numeric(5,2),
    CONSTRAINT chk_promoter_ranks_max_discount_pct CHECK (((max_discount_pct IS NULL) OR ((max_discount_pct >= (0)::numeric) AND (max_discount_pct <= (100)::numeric))))
);

--
-- Name: promoter_ranks_promoter_ranks_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_ranks ALTER COLUMN promoter_ranks_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_ranks_promoter_ranks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_references; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_references (
    promoter_references_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    promoter_id bigint NOT NULL,
    first_name character varying(50) NOT NULL,
    last_name character varying(50) NOT NULL,
    document_type character varying(1),
    document_number character varying(20),
    phone character varying(30),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_promoter_references_document_pair CHECK ((((document_type IS NULL) AND (document_number IS NULL)) OR ((document_type IS NOT NULL) AND (document_number IS NOT NULL)))),
    CONSTRAINT ck_promoter_references_document_type CHECK (((document_type IS NULL) OR ((document_type)::text = ANY ((ARRAY['V'::character varying, 'E'::character varying])::text[])))),
    CONSTRAINT ck_promoter_references_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);

--
-- Name: TABLE promoter_references; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON TABLE app.promoter_references IS 'Referencias personales del asesor (expediente, sección IV)';

--
-- Name: promoter_references_promoter_references_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_references ALTER COLUMN promoter_references_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_references_promoter_references_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_supervisor_assignments; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_supervisor_assignments (
    promoter_supervisor_assignments_id bigint NOT NULL,
    uuid uuid NOT NULL,
    promoter_id bigint NOT NULL,
    from_supervisor_id bigint,
    to_supervisor_id bigint,
    actor_user_id bigint,
    reason text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: promoter_supervisor_assignmen_promoter_supervisor_assignmen_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_supervisor_assignments ALTER COLUMN promoter_supervisor_assignments_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_supervisor_assignmen_promoter_supervisor_assignmen_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoter_types; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoter_types (
    promoter_types_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    generates_hierarchy_override boolean DEFAULT true NOT NULL,
    max_discount_pct numeric(5,2),
    CONSTRAINT chk_promoter_types_max_discount_pct CHECK (((max_discount_pct IS NULL) OR ((max_discount_pct >= (0)::numeric) AND (max_discount_pct <= (100)::numeric))))
);

--
-- Name: COLUMN promoter_types.generates_hierarchy_override; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoter_types.generates_hierarchy_override IS 'FALSE = a commission earned by a promoter of this type never cascades a hierarchy override up to their supervisor/coordinador chain (V102). Default TRUE preserves today''s behavior for every existing type.';

--
-- Name: promoter_types_promoter_types_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoter_types ALTER COLUMN promoter_types_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoter_types_promoter_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promoters; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promoters (
    promoters_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id bigint,
    person_id bigint,
    display_name character varying(120) NOT NULL,
    description text,
    referral_code character varying(20) NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    email app.citext,
    phone character varying(30),
    total_referrals integer DEFAULT 0 NOT NULL,
    total_commission_paid numeric(12,2) DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    promoter_type_id bigint,
    rank_id bigint NOT NULL,
    supervisor_id bigint,
    joined_at date,
    assigned_zone character varying(120),
    education_level character varying(20),
    profession character varying(120),
    years_of_experience smallint,
    last_company character varying(150),
    last_position character varying(100),
    vehicle character varying(120),
    CONSTRAINT chk_promoters_referral_code_format CHECK (((referral_code)::text ~ '^[A-Z0-9-]{4,20}$'::text)),
    CONSTRAINT chk_promoters_system_or_user CHECK (((is_system = true) OR ((user_id IS NOT NULL) AND (person_id IS NOT NULL)))),
    CONSTRAINT ck_promoters_education_level CHECK (((education_level IS NULL) OR ((education_level)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SECONDARY'::character varying, 'TECHNICAL'::character varying, 'UNIVERSITY'::character varying, 'POSTGRADUATE'::character varying])::text[])))),
    CONSTRAINT ck_promoters_years_of_experience CHECK (((years_of_experience IS NULL) OR (years_of_experience >= 0))),
    CONSTRAINT promoters_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying])::text[]))),
    CONSTRAINT promoters_total_commission_paid_check CHECK ((total_commission_paid >= (0)::numeric)),
    CONSTRAINT promoters_total_referrals_check CHECK ((total_referrals >= 0))
);

--
-- Name: COLUMN promoters.joined_at; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoters.joined_at IS 'Fecha de ingreso del asesor (expediente, sección I)';

--
-- Name: COLUMN promoters.assigned_zone; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoters.assigned_zone IS 'Región/zona asignada (texto libre)';

--
-- Name: COLUMN promoters.education_level; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoters.education_level IS 'Nivel académico: PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY, POSTGRADUATE';

--
-- Name: COLUMN promoters.vehicle; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.promoters.vehicle IS 'Vehículo propio (texto libre: marca/modelo/año, o vacío si no tiene)';

--
-- Name: promoters_promoters_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promoters ALTER COLUMN promoters_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promoters_promoters_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: promotion_plans; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promotion_plans (
    promotion_id bigint NOT NULL,
    plan_id bigint NOT NULL
);

--
-- Name: promotions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.promotions (
    promotions_id bigint NOT NULL,
    uuid uuid NOT NULL,
    campaign_id bigint NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    kind character varying(20) NOT NULL,
    discount_pct numeric(5,2) NOT NULL,
    applies_to character varying(20) NOT NULL,
    cycles integer,
    covers_extra_beneficiaries boolean DEFAULT false NOT NULL,
    max_redemptions integer,
    redemptions_count integer DEFAULT 0 NOT NULL,
    requires_code boolean DEFAULT false NOT NULL,
    accepts_promoter_code boolean DEFAULT false NOT NULL,
    accepts_member_code boolean DEFAULT false NOT NULL,
    accepts_ally_code boolean DEFAULT false NOT NULL,
    referrer_reward_pct numeric(5,2),
    referrer_reward_cycles integer,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_promotions_applies_to CHECK (((applies_to)::text = ANY ((ARRAY['INSCRIPTION'::character varying, 'MONTHLY'::character varying, 'BOTH'::character varying])::text[]))),
    CONSTRAINT chk_promotions_code_owner CHECK (((NOT requires_code) OR accepts_promoter_code OR accepts_member_code OR accepts_ally_code)),
    CONSTRAINT chk_promotions_cycles CHECK (((cycles IS NULL) OR (cycles >= 1))),
    CONSTRAINT chk_promotions_discount_pct CHECK (((discount_pct > (0)::numeric) AND (discount_pct <= (100)::numeric))),
    CONSTRAINT chk_promotions_kind CHECK (((kind)::text = ANY ((ARRAY['ACQUISITION'::character varying, 'RECOVERY'::character varying])::text[]))),
    CONSTRAINT chk_promotions_max_redemptions CHECK (((max_redemptions IS NULL) OR (max_redemptions >= 1))),
    CONSTRAINT chk_promotions_recovery_monthly CHECK ((((kind)::text <> 'RECOVERY'::text) OR ((applies_to)::text = 'MONTHLY'::text))),
    CONSTRAINT chk_promotions_redemptions_count CHECK ((redemptions_count >= 0)),
    CONSTRAINT chk_promotions_referrer_reward CHECK ((((referrer_reward_pct IS NULL) AND (referrer_reward_cycles IS NULL)) OR ((referrer_reward_pct > (0)::numeric) AND (referrer_reward_pct <= (100)::numeric) AND (referrer_reward_cycles >= 1) AND accepts_member_code)))
);

--
-- Name: promotions_promotions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.promotions ALTER COLUMN promotions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.promotions_promotions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: referrals; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.referrals (
    referrals_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    referrer_member_id bigint NOT NULL,
    referred_member_id bigint,
    referral_code character varying(20) NOT NULL,
    enrolled_at timestamp with time zone,
    expires_at timestamp with time zone,
    reward_pct numeric(5,2),
    reward_flat_amount numeric(10,2),
    reward_payment_id bigint,
    reward_granted_at timestamp with time zone,
    admin_notes text,
    void_reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'PENDING_ENROLLMENT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    reward_currency_id bigint,
    CONSTRAINT chk_referrals_expired_unreferred CHECK ((((status)::text <> 'EXPIRED'::text) OR (referred_member_id IS NULL))),
    CONSTRAINT chk_referrals_not_self CHECK (((referred_member_id IS NULL) OR (referred_member_id <> referrer_member_id))),
    CONSTRAINT chk_referrals_pending_no_referred CHECK ((((status)::text <> 'PENDING_ENROLLMENT'::text) OR ((referred_member_id IS NULL) AND (enrolled_at IS NULL)))),
    CONSTRAINT chk_referrals_registered_has_referred CHECK ((((status)::text <> ALL ((ARRAY['REGISTERED'::character varying, 'REWARD_GRANTED'::character varying, 'VOIDED'::character varying])::text[])) OR ((referred_member_id IS NOT NULL) AND (enrolled_at IS NOT NULL)))),
    CONSTRAINT chk_referrals_reward_consistency CHECK ((((status)::text <> 'REWARD_GRANTED'::text) OR ((reward_payment_id IS NOT NULL) AND (reward_granted_at IS NOT NULL)))),
    CONSTRAINT chk_referrals_reward_currency_paired CHECK (((reward_flat_amount IS NULL) OR (reward_currency_id IS NOT NULL))),
    CONSTRAINT chk_referrals_reward_pct_xor_flat CHECK ((((reward_pct IS NULL) AND (reward_flat_amount IS NULL)) OR ((reward_pct IS NOT NULL) AND (reward_flat_amount IS NULL)) OR ((reward_pct IS NULL) AND (reward_flat_amount IS NOT NULL)))),
    CONSTRAINT chk_referrals_void_has_reason CHECK ((((status)::text <> 'VOIDED'::text) OR (void_reason IS NOT NULL))),
    CONSTRAINT referrals_reward_flat_amount_check CHECK (((reward_flat_amount IS NULL) OR (reward_flat_amount >= (0)::numeric))),
    CONSTRAINT referrals_reward_pct_check CHECK (((reward_pct IS NULL) OR ((reward_pct >= (0)::numeric) AND (reward_pct <= (100)::numeric)))),
    CONSTRAINT referrals_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_ENROLLMENT'::character varying, 'REGISTERED'::character varying, 'EXPIRED'::character varying, 'REWARD_GRANTED'::character varying, 'VOIDED'::character varying])::text[])))
);

--
-- Name: referrals_referrals_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.referrals ALTER COLUMN referrals_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.referrals_referrals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: report_audit_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.report_audit_log (
    report_audit_log_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    report_type character varying(80) NOT NULL,
    entity_key character varying(80),
    entity_id bigint,
    entity_uuid uuid,
    entity_identifier character varying(120),
    format character varying(10) NOT NULL,
    parameters_json jsonb,
    actor_id bigint,
    login_audit_log_id bigint,
    attached_file_id bigint,
    file_name character varying(255),
    size_bytes bigint,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_report_audit_format CHECK (((format)::text = ANY ((ARRAY['PDF'::character varying, 'XLSX'::character varying])::text[])))
);

--
-- Name: report_audit_log_report_audit_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.report_audit_log ALTER COLUMN report_audit_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.report_audit_log_report_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: report_share; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.report_share (
    report_share_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    report_audit_log_id bigint NOT NULL,
    share_token character varying(120) NOT NULL,
    share_link character varying(500),
    recipients jsonb,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: report_share_report_share_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.report_share ALTER COLUMN report_share_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.report_share_report_share_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: role_permissions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.role_permissions (
    role_permissions_id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: role_permissions_role_permissions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.role_permissions ALTER COLUMN role_permissions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.role_permissions_role_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: roles; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.roles (
    roles_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(50) NOT NULL,
    description character varying(200),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: roles_roles_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.roles ALTER COLUMN roles_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.roles_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: scheduled_job_runs; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.scheduled_job_runs (
    scheduled_job_runs_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    scheduled_job_id bigint NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    finished_at timestamp with time zone,
    duration_ms bigint,
    outcome character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    triggered_by character varying(20) NOT NULL,
    triggered_by_user_uuid uuid,
    summary jsonb,
    error_message text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    attempt_count integer DEFAULT 1 NOT NULL,
    CONSTRAINT chk_runs_finished_after_started CHECK (((finished_at IS NULL) OR (finished_at >= started_at))),
    CONSTRAINT chk_runs_manual_has_user CHECK ((((triggered_by)::text <> 'MANUAL'::text) OR (triggered_by_user_uuid IS NOT NULL))),
    CONSTRAINT chk_runs_terminal_has_finished_at CHECK ((((outcome)::text = 'RUNNING'::text) OR (finished_at IS NOT NULL))),
    CONSTRAINT scheduled_job_runs_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'TIMEOUT'::character varying, 'SKIPPED_CONCURRENT'::character varying, 'CANCELED'::character varying])::text[]))),
    CONSTRAINT scheduled_job_runs_triggered_by_check CHECK (((triggered_by)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'MANUAL'::character varying, 'STARTUP'::character varying])::text[])))
);

--
-- Name: scheduled_job_runs_scheduled_job_runs_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.scheduled_job_runs ALTER COLUMN scheduled_job_runs_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.scheduled_job_runs_scheduled_job_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: scheduled_jobs; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.scheduled_jobs (
    scheduled_jobs_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(80) NOT NULL,
    display_name character varying(120) NOT NULL,
    description text,
    cron_expression character varying(120) NOT NULL,
    timezone character varying(60) DEFAULT 'America/Caracas'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    allow_concurrent boolean DEFAULT false NOT NULL,
    max_sync_seconds integer DEFAULT 30 NOT NULL,
    lock_held boolean DEFAULT false NOT NULL,
    last_run_at timestamp with time zone,
    last_run_status character varying(20),
    next_run_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50) DEFAULT 'ENABLED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    max_retry_attempts integer DEFAULT 5 NOT NULL,
    retry_delay_seconds integer DEFAULT 10 NOT NULL,
    CONSTRAINT scheduled_jobs_max_retry_attempts_check CHECK (((max_retry_attempts >= 0) AND (max_retry_attempts <= 10))),
    CONSTRAINT scheduled_jobs_max_sync_seconds_check CHECK ((max_sync_seconds >= 0)),
    CONSTRAINT scheduled_jobs_retry_delay_seconds_check CHECK (((retry_delay_seconds >= 0) AND (retry_delay_seconds <= 3600))),
    CONSTRAINT scheduled_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['ENABLED'::character varying, 'DISABLED'::character varying])::text[])))
);

--
-- Name: scheduled_jobs_scheduled_jobs_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.scheduled_jobs ALTER COLUMN scheduled_jobs_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.scheduled_jobs_scheduled_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: security_policies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.security_policies (
    security_policies_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    max_login_attempts integer DEFAULT 5 NOT NULL,
    lockout_duration_minutes integer DEFAULT 30 NOT NULL,
    days_password_expires integer DEFAULT 90 NOT NULL,
    password_history_count integer DEFAULT 5 NOT NULL,
    max_concurrent_sessions integer DEFAULT 3 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_security_policies_days_password_expires CHECK ((days_password_expires >= 0)),
    CONSTRAINT chk_security_policies_lockout_duration_minutes CHECK ((lockout_duration_minutes >= 1)),
    CONSTRAINT chk_security_policies_max_concurrent_sessions CHECK ((max_concurrent_sessions >= 1)),
    CONSTRAINT chk_security_policies_max_login_attempts CHECK ((max_login_attempts >= 1)),
    CONSTRAINT chk_security_policies_password_history_count CHECK ((password_history_count >= 0))
);

--
-- Name: security_policies_security_policies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.security_policies ALTER COLUMN security_policies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.security_policies_security_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: states; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.states (
    states_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    country_id bigint NOT NULL,
    code character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: states_states_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.states ALTER COLUMN states_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.states_states_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: subsidies; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.subsidies (
    subsidies_id bigint NOT NULL,
    uuid uuid NOT NULL,
    member_id bigint NOT NULL,
    monthly_percentage numeric(5,2),
    inscription_percentage numeric(5,2),
    max_exonerated_beneficiaries integer,
    reason text NOT NULL,
    authorized_by bigint,
    valid_from date NOT NULL,
    valid_until date,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_subsidies_covers_something CHECK (((monthly_percentage IS NOT NULL) OR (inscription_percentage IS NOT NULL))),
    CONSTRAINT chk_subsidies_inscription_pct CHECK (((inscription_percentage IS NULL) OR ((inscription_percentage >= (0)::numeric) AND (inscription_percentage <= (100)::numeric)))),
    CONSTRAINT chk_subsidies_max_exon CHECK (((max_exonerated_beneficiaries IS NULL) OR (max_exonerated_beneficiaries >= 0))),
    CONSTRAINT chk_subsidies_monthly_pct CHECK (((monthly_percentage IS NULL) OR ((monthly_percentage >= (0)::numeric) AND (monthly_percentage <= (100)::numeric)))),
    CONSTRAINT chk_subsidies_valid_window CHECK (((valid_until IS NULL) OR (valid_until >= valid_from)))
);

--
-- Name: subsidies_subsidies_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.subsidies ALTER COLUMN subsidies_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.subsidies_subsidies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: subsidy_audit_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.subsidy_audit_log (
    subsidy_audit_log_id bigint NOT NULL,
    uuid uuid NOT NULL,
    subsidy_id bigint NOT NULL,
    action character varying(20) NOT NULL,
    actor_id bigint,
    before_json jsonb,
    after_json jsonb,
    reason text,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_subsidy_audit_action CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'MODIFIED'::character varying, 'REVOKED'::character varying])::text[])))
);

--
-- Name: subsidy_audit_log_subsidy_audit_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.subsidy_audit_log ALTER COLUMN subsidy_audit_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.subsidy_audit_log_subsidy_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: subsidy_beneficiaries; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.subsidy_beneficiaries (
    subsidy_beneficiaries_id bigint NOT NULL,
    uuid uuid NOT NULL,
    subsidy_id bigint NOT NULL,
    beneficiary_id bigint NOT NULL,
    monthly_percentage numeric(5,2),
    inscription_percentage numeric(5,2),
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT chk_subsidy_ben_covers_something CHECK (((monthly_percentage IS NOT NULL) OR (inscription_percentage IS NOT NULL))),
    CONSTRAINT chk_subsidy_ben_inscription_pct CHECK (((inscription_percentage IS NULL) OR ((inscription_percentage >= (0)::numeric) AND (inscription_percentage <= (100)::numeric)))),
    CONSTRAINT chk_subsidy_ben_monthly_pct CHECK (((monthly_percentage IS NULL) OR ((monthly_percentage >= (0)::numeric) AND (monthly_percentage <= (100)::numeric))))
);

--
-- Name: subsidy_beneficiaries_subsidy_beneficiaries_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.subsidy_beneficiaries ALTER COLUMN subsidy_beneficiaries_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.subsidy_beneficiaries_subsidy_beneficiaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: system_configs; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.system_configs (
    system_configs_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    report_footer text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    report_audit_mode character varying(20) DEFAULT 'PER_ENTITY'::character varying NOT NULL,
    login_audit_enabled boolean DEFAULT true NOT NULL,
    login_session_expiration_days integer DEFAULT 30 NOT NULL,
    default_sort jsonb,
    audit_create_mode character varying(20) DEFAULT 'PER_ENTITY'::character varying NOT NULL,
    audit_update_mode character varying(20) DEFAULT 'PER_ENTITY'::character varying NOT NULL,
    audit_delete_mode character varying(20) DEFAULT 'PER_ENTITY'::character varying NOT NULL,
    capture_before_after_mode character varying(20) DEFAULT 'PER_ENTITY'::character varying NOT NULL,
    patient_medical_access_enabled boolean DEFAULT false NOT NULL,
    pdf_max_concurrent_renders integer DEFAULT 5 NOT NULL,
    pdf_render_wait_seconds integer DEFAULT 10 NOT NULL,
    CONSTRAINT chk_system_configs_audit_create_mode CHECK (((audit_create_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[]))),
    CONSTRAINT chk_system_configs_audit_delete_mode CHECK (((audit_delete_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[]))),
    CONSTRAINT chk_system_configs_audit_update_mode CHECK (((audit_update_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[]))),
    CONSTRAINT chk_system_configs_capture_before_after_mode CHECK (((capture_before_after_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[]))),
    CONSTRAINT chk_system_configs_login_session_expiration_days CHECK ((login_session_expiration_days > 0)),
    CONSTRAINT chk_system_configs_pdf_max_concurrent_renders CHECK ((pdf_max_concurrent_renders >= '-1'::integer)),
    CONSTRAINT chk_system_configs_pdf_render_wait_seconds CHECK ((pdf_render_wait_seconds >= '-1'::integer)),
    CONSTRAINT chk_system_configs_report_audit_mode CHECK (((report_audit_mode)::text = ANY ((ARRAY['PER_ENTITY'::character varying, 'FORCE_ENABLED'::character varying, 'FORCE_DISABLED'::character varying])::text[])))
);

--
-- Name: COLUMN system_configs.default_sort; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.system_configs.default_sort IS 'Global fallback default sort, same shape as entity_config.default_sort: [{"field":"createdAt","direction":"DESC"}]. Restricted to common columns (id, uuid, createdAt, updatedAt, createdBy, updatedBy) since it is not validated against any single entity''s field set. NULL means "no global default configured" — callers fall back to createdAt DESC in code.';

--
-- Name: COLUMN system_configs.patient_medical_access_enabled; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.system_configs.patient_medical_access_enabled IS 'Activa el acceso de los aliados médicos a la historia de sus pacientes (ADR 0032). Apagado hasta que legal confirme la cláusula del Acuerdo B2C.';

--
-- Name: COLUMN system_configs.pdf_max_concurrent_renders; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.system_configs.pdf_max_concurrent_renders IS 'Max generic PDF documents rendered at the same time; 0 or -1 = no cap';

--
-- Name: COLUMN system_configs.pdf_render_wait_seconds; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.system_configs.pdf_render_wait_seconds IS 'Seconds a PDF request waits for a free render slot before 503; 0 or -1 = no time limit';

--
-- Name: system_configs_system_configs_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.system_configs ALTER COLUMN system_configs_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.system_configs_system_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: terms_acceptances; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.terms_acceptances (
    terms_acceptances_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id bigint NOT NULL,
    terms_versions_id bigint NOT NULL,
    accepted_at timestamp with time zone DEFAULT now() NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    ip_address character varying(45),
    user_agent character varying(400),
    channel character varying(20) DEFAULT 'PORTAL'::character varying NOT NULL,
    membership_id bigint,
    ally_agreement_id bigint,
    rendered_content text,
    content_sha256 character varying(64),
    CONSTRAINT chk_terms_acceptances_channel CHECK (((channel)::text = ANY ((ARRAY['PORTAL'::character varying, 'SELF_SERVICE'::character varying, 'ASSISTED'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_terms_acceptances_content_sha256 CHECK (((content_sha256 IS NULL) OR ((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)))
);

--
-- Name: COLUMN terms_acceptances.ip_address; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.ip_address IS 'IP del cliente al aceptar (IPv4 o IPv6)';

--
-- Name: COLUMN terms_acceptances.user_agent; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.user_agent IS 'User-Agent del navegador al aceptar (truncado a 400)';

--
-- Name: COLUMN terms_acceptances.channel; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.channel IS 'Canal de aceptación: PORTAL (modal en la app), SELF_SERVICE (autoafiliación pública), ASSISTED (cargado por un asesor), ADMIN';

--
-- Name: COLUMN terms_acceptances.membership_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.membership_id IS 'Membresía para la que se aceptó (autoafiliación); NULL en aceptaciones desde el portal';

--
-- Name: COLUMN terms_acceptances.ally_agreement_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.ally_agreement_id IS 'Convenio de aliado para el que se aceptó el contrato ALIADO; NULL para AFILIADO/ASESOR y para aceptaciones ALIADO anteriores a V189 que no se pudieron vincular a un único convenio';

--
-- Name: COLUMN terms_acceptances.rendered_content; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.rendered_content IS 'Texto del contrato tal como se mostró y aceptó (variables ya resueltas); NULL en aceptaciones anteriores a V189';

--
-- Name: COLUMN terms_acceptances.content_sha256; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_acceptances.content_sha256 IS 'SHA-256 en hexadecimal (UTF-8) de rendered_content, para probar que el texto aceptado no se alteró';

--
-- Name: terms_acceptances_terms_acceptances_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.terms_acceptances ALTER COLUMN terms_acceptances_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.terms_acceptances_terms_acceptances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: terms_versions; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.terms_versions (
    terms_versions_id bigint NOT NULL,
    uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    term_type character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    content_markdown text NOT NULL,
    is_public boolean DEFAULT false NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    status character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    ally_type_id bigint,
    segment character varying(3),
    ally_id bigint,
    is_mandatory boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_terms_versions_scope_only_aliado CHECK ((((term_type)::text = 'ALIADO'::text) OR ((ally_type_id IS NULL) AND (segment IS NULL) AND (ally_id IS NULL)))),
    CONSTRAINT chk_terms_versions_segment CHECK (((segment IS NULL) OR ((segment)::text = ANY ((ARRAY['B2B'::character varying, 'B2P'::character varying])::text[])))),
    CONSTRAINT chk_terms_versions_term_type CHECK (((term_type)::text = ANY ((ARRAY['AFILIADO'::character varying, 'ASESOR'::character varying, 'ALIADO'::character varying])::text[])))
);

--
-- Name: COLUMN terms_versions.is_public; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_versions.is_public IS 'Visible en el endpoint público de la web (GET /v1/public/terms/{type}); NO decide si se pide aceptación';

--
-- Name: COLUMN terms_versions.ally_type_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_versions.ally_type_id IS 'Solo ALIADO: plantilla restringida a aliados de este tipo; NULL = cualquier tipo';

--
-- Name: COLUMN terms_versions.segment; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_versions.segment IS 'Solo ALIADO: plantilla restringida al segmento B2B (Comercial) o B2P (Profesional); NULL = cualquier segmento';

--
-- Name: COLUMN terms_versions.ally_id; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_versions.ally_id IS 'Solo ALIADO: plantilla hecha para un aliado específico; NULL = cualquier aliado';

--
-- Name: COLUMN terms_versions.is_mandatory; Type: COMMENT; Schema: app; Owner: -
--

COMMENT ON COLUMN app.terms_versions.is_mandatory IS 'TRUE = debe aceptarse para usar la plataforma; FALSE = se puede posponer (bloquea solo el módulo del contrato)';

--
-- Name: terms_versions_terms_versions_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.terms_versions ALTER COLUMN terms_versions_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.terms_versions_terms_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: user_password_history; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.user_password_history (
    user_password_history_id bigint NOT NULL,
    user_id bigint NOT NULL,
    password_hash character varying(255) NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: user_password_history_user_password_history_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.user_password_history ALTER COLUMN user_password_history_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.user_password_history_user_password_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: user_roles; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.user_roles (
    user_roles_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_by uuid,
    expires_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid
);

--
-- Name: user_roles_user_roles_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.user_roles ALTER COLUMN user_roles_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.user_roles_user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: user_sessions_log; Type: TABLE; Schema: app; Owner: -
--

CREATE TABLE app.user_sessions_log (
    user_sessions_log_id bigint NOT NULL,
    user_id bigint NOT NULL,
    jti character varying(36) NOT NULL,
    ip_address inet NOT NULL,
    user_agent character varying(500),
    login_locale character varying(10),
    login_at timestamp with time zone DEFAULT now() NOT NULL,
    logout_at timestamp with time zone,
    logout_reason character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_sessions_log_logout_reason_check CHECK (((logout_reason)::text = ANY ((ARRAY['user_logout'::character varying, 'token_expired'::character varying, 'forced'::character varying, 'role_switch'::character varying])::text[])))
);

--
-- Name: user_sessions_log_user_sessions_log_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.user_sessions_log ALTER COLUMN user_sessions_log_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.user_sessions_log_user_sessions_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: users_users_id_seq; Type: SEQUENCE; Schema: app; Owner: -
--

ALTER TABLE app.users ALTER COLUMN users_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.users_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: v_report_commissions; Type: VIEW; Schema: app; Owner: -
--

CREATE VIEW app.v_report_commissions AS
 SELECT c.commissions_id,
    c.uuid AS commission_uuid,
    c.status AS commission_status,
    c.applies_to,
    c.period_strategy,
    c.period_start,
    c.period_end,
    c.earned_at,
    c.amount AS commission_amount,
    cur.code AS currency,
    cur.symbol AS currency_symbol,
    c.calculation_basis,
    c.commission_pct,
    c.flat_amount,
    COALESCE(c.tier_name_snapshot, 'Sin Nivel'::character varying) AS tier_name,
    c.payout_payment_id,
    payout_p.uuid AS payout_payment_uuid,
    payout_p.payment_date AS payout_date,
    payout_p.status AS payout_payment_status,
    payout_line.payout_method_code,
    payout_line.payout_method_name,
    payout_line.payout_bank_name,
    COALESCE(payout_line.reference_number, (c.payout_reference)::text) AS payout_reference,
    c.paid_at,
    c.exchange_rate_at_earned,
    c.earned_rate_date,
    c.exchange_rate_at_paid,
    c.paid_rate_date,
    c.voided_at,
    c.void_reason,
    c.admin_notes,
    c.created_at AS commission_created_at,
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    pr.display_name AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    COALESCE(pr_p.full_name, pr.display_name) AS promoter_person_name,
    COALESCE((((pr_p.document_type)::text || '-'::text) || (pr_p.document_number)::text), 'N/A'::text) AS promoter_document,
    COALESCE(pr.phone, pr_p.phone, 'N/A'::character varying) AS promoter_phone,
    COALESCE((pr.email)::text, (pr_p.email)::text, 'N/A'::text) AS promoter_email,
    COALESCE(pt.name, 'Estándar'::character varying) AS promoter_type_name,
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (((m_p.document_type)::text || '-'::text) || (m_p.document_number)::text) AS member_document,
    COALESCE(m_p.phone, 'N/A'::character varying) AS member_phone,
    COALESCE((m_p.email)::text, 'N/A'::text) AS member_email,
    p.payments_id,
    p.uuid AS payment_uuid,
    p.payment_date,
    p.received_at AS payment_received_at,
    origin_line.payment_method,
    origin_line.payment_method_name,
    origin_line.payment_bank_name,
    origin_line.payment_reference,
    p.amount AS payment_amount,
    p.status AS payment_status,
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type
   FROM ((((((((((((app.commissions c
     JOIN app.currencies cur ON ((c.currency_id = cur.currencies_id)))
     JOIN app.promoters pr ON ((c.promoter_id = pr.promoters_id)))
     LEFT JOIN app.persons pr_p ON ((pr.person_id = pr_p.persons_id)))
     LEFT JOIN app.promoter_types pt ON ((pr.promoter_type_id = pt.promoter_types_id)))
     JOIN app.members m ON ((c.member_id = m.members_id)))
     JOIN app.persons m_p ON ((m.person_id = m_p.persons_id)))
     JOIN app.payments p ON ((c.payment_id = p.payments_id)))
     LEFT JOIN LATERAL ( SELECT string_agg(DISTINCT (origin_pm.code)::text, ', '::text) AS payment_method,
            string_agg(DISTINCT (origin_pm.name)::text, ', '::text) AS payment_method_name,
            string_agg(DISTINCT (origin_b.short_name)::text, ', '::text) AS payment_bank_name,
            string_agg(DISTINCT (pl_1.reference_number)::text, ', '::text) AS payment_reference
           FROM ((app.payment_lines pl_1
             LEFT JOIN app.payment_methods origin_pm ON ((origin_pm.payment_methods_id = pl_1.payment_type_id)))
             LEFT JOIN app.banks origin_b ON ((origin_b.banks_id = pl_1.bank_id)))
          WHERE (pl_1.payment_id = p.payments_id)) origin_line ON (true))
     LEFT JOIN app.memberships ms ON ((p.membership_id = ms.memberships_id)))
     LEFT JOIN app.plans pl ON ((ms.plan_id = pl.plans_id)))
     LEFT JOIN app.payments payout_p ON ((payout_p.payments_id = c.payout_payment_id)))
     LEFT JOIN LATERAL ( SELECT string_agg(DISTINCT (payout_pm.code)::text, ', '::text) AS payout_method_code,
            string_agg(DISTINCT (payout_pm.name)::text, ', '::text) AS payout_method_name,
            string_agg(DISTINCT (payout_b.short_name)::text, ', '::text) AS payout_bank_name,
            string_agg(DISTINCT (pol.reference_number)::text, ', '::text) AS reference_number
           FROM ((app.payment_lines pol
             LEFT JOIN app.payment_methods payout_pm ON ((payout_pm.payment_methods_id = pol.payment_type_id)))
             LEFT JOIN app.banks payout_b ON ((payout_b.banks_id = pol.bank_id)))
          WHERE (pol.payment_id = payout_p.payments_id)) payout_line ON (true))
  WHERE (c.is_active = true);

--
-- Name: v_report_payments; Type: VIEW; Schema: app; Owner: -
--

CREATE VIEW app.v_report_payments AS
 SELECT p.payments_id,
    p.uuid AS payment_uuid,
    p.direction,
    p.payment_date,
    p.received_at,
    p.status AS payment_status,
    pc.payment_categories_id,
    pc.code AS payment_category_code,
    pc.name AS payment_category_name,
    pc.direction AS payment_category_direction,
    COALESCE(pc.name, (
        CASE
            WHEN p.inscription THEN 'Inscripción'::text
            ELSE 'Mensualidad'::text
        END)::character varying) AS concept_label,
    pline.payment_line_id,
    pline.uuid AS payment_line_uuid,
    pm.payment_methods_id,
    pm.code AS payment_method,
    pm.name AS payment_method_name,
    b.banks_id,
    b.code AS bank_code,
    b.short_name AS bank_name,
    pline.reference_number,
    pline.amount AS line_amount,
    pline.status AS line_status,
    COALESCE(pline.amount, p.amount) AS gross_amount,
    cur.code AS currency,
    cur.symbol AS currency_symbol,
        CASE
            WHEN ((p.amount > (0)::numeric) AND (p.discount_amount IS NOT NULL)) THEN round((p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount)), 2)
            ELSE 0.00
        END AS discount_amount,
    p.discount_reason,
    (COALESCE(pline.amount, p.amount) -
        CASE
            WHEN ((p.amount > (0)::numeric) AND (p.discount_amount IS NOT NULL)) THEN round((p.discount_amount * (COALESCE(pline.amount, p.amount) / p.amount)), 2)
            ELSE 0.00
        END) AS net_amount,
    p.exchange_rate_used,
    p.exchange_rate_date,
    p.inscription,
    p.applied_period,
    p.admin_notes,
    p.reviewed_at,
    p.review_reason,
    p.support_file_name,
    p.created_at AS payment_created_at,
    COALESCE(ru_p.full_name, 'Sin Revisar'::character varying) AS reviewer_name,
    du_p.full_name AS discounter_name,
    per.persons_id,
    per.uuid AS person_uuid,
    per.full_name AS person_name,
    (((per.document_type)::text || '-'::text) || (per.document_number)::text) AS person_document,
    COALESCE(per.phone, 'N/A'::character varying) AS person_phone,
    COALESCE((per.email)::text, 'N/A'::text) AS person_email,
    pr.promoters_id,
    pr.uuid AS promoter_uuid,
    COALESCE(pr.display_name, 'Directo / Sin Promotor'::character varying) AS promoter_name,
    pr.referral_code AS promoter_referral_code,
    ms.memberships_id,
    ms.uuid AS membership_uuid,
    ms.status AS membership_status,
    ms.monthly_fee AS plan_monthly_fee,
    pl.plans_id,
    pl.code AS plan_code,
    pl.name AS plan_name,
    pl.type AS plan_type,
    m.members_id,
    m.uuid AS member_uuid,
    m_p.full_name AS member_name,
    (((m_p.document_type)::text || '-'::text) || (m_p.document_number)::text) AS member_document,
    COALESCE(m_p.phone, 'N/A'::character varying) AS member_phone,
    COALESCE((m_p.email)::text, 'N/A'::text) AS member_email
   FROM (((((((((((((((app.payments p
     JOIN app.currencies cur ON ((p.currency_id = cur.currencies_id)))
     JOIN app.payment_categories pc ON ((p.payment_type_id = pc.payment_categories_id)))
     LEFT JOIN app.payment_lines pline ON ((pline.payment_id = p.payments_id)))
     LEFT JOIN app.payment_methods pm ON ((pm.payment_methods_id = pline.payment_type_id)))
     LEFT JOIN app.banks b ON ((b.banks_id = pline.bank_id)))
     LEFT JOIN app.persons per ON ((p.person_id = per.persons_id)))
     LEFT JOIN app.promoters pr ON ((p.promoter_id = pr.promoters_id)))
     LEFT JOIN app.memberships ms ON ((p.membership_id = ms.memberships_id)))
     LEFT JOIN app.plans pl ON ((ms.plan_id = pl.plans_id)))
     LEFT JOIN app.members m ON ((ms.member_id = m.members_id)))
     LEFT JOIN app.persons m_p ON ((m.person_id = m_p.persons_id)))
     LEFT JOIN app.users ru ON ((p.reviewed_by = ru.users_id)))
     LEFT JOIN app.persons ru_p ON ((ru.person_id = ru_p.persons_id)))
     LEFT JOIN app.users du ON ((p.discounted_by = du.users_id)))
     LEFT JOIN app.persons du_p ON ((du.person_id = du_p.persons_id)))
  WHERE (p.is_active = true);

--
-- Name: allies ally_number; Type: DEFAULT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies ALTER COLUMN ally_number SET DEFAULT nextval('app.allies_ally_number_seq'::regclass);

--
-- Name: affiliation_request_logs affiliation_request_logs_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_request_logs
    ADD CONSTRAINT affiliation_request_logs_pkey PRIMARY KEY (affiliation_request_logs_id);

--
-- Name: affiliation_requests affiliation_requests_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_pkey PRIMARY KEY (affiliation_requests_id);

--
-- Name: affiliation_requests affiliation_requests_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_uuid_key UNIQUE (uuid);

--
-- Name: allies allies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_pkey PRIMARY KEY (allies_id);

--
-- Name: allies allies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_uuid_key UNIQUE (uuid);

--
-- Name: ally_agreements ally_agreements_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_agreements
    ADD CONSTRAINT ally_agreements_pkey PRIMARY KEY (ally_agreements_id);

--
-- Name: ally_agreements ally_agreements_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_agreements
    ADD CONSTRAINT ally_agreements_uuid_key UNIQUE (uuid);

--
-- Name: ally_ally_types ally_ally_types_ally_id_ally_type_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_ally_types
    ADD CONSTRAINT ally_ally_types_ally_id_ally_type_id_key UNIQUE (ally_id, ally_type_id);

--
-- Name: ally_ally_types ally_ally_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_ally_types
    ADD CONSTRAINT ally_ally_types_pkey PRIMARY KEY (ally_ally_types_id);

--
-- Name: ally_opening_hours ally_opening_hours_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_opening_hours
    ADD CONSTRAINT ally_opening_hours_pkey PRIMARY KEY (ally_opening_hours_id);

--
-- Name: ally_opening_hours ally_opening_hours_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_opening_hours
    ADD CONSTRAINT ally_opening_hours_uuid_key UNIQUE (uuid);

--
-- Name: ally_patient_links ally_patient_links_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_pkey PRIMARY KEY (ally_patient_links_id);

--
-- Name: ally_patient_links ally_patient_links_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_uuid_key UNIQUE (uuid);

--
-- Name: ally_product_review_log ally_product_review_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_product_review_log
    ADD CONSTRAINT ally_product_review_log_pkey PRIMARY KEY (ally_product_review_log_id);

--
-- Name: ally_products ally_products_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_pkey PRIMARY KEY (ally_products_id);

--
-- Name: ally_products ally_products_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_uuid_key UNIQUE (uuid);

--
-- Name: ally_professions ally_professions_ally_id_profession_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_professions
    ADD CONSTRAINT ally_professions_ally_id_profession_id_key UNIQUE (ally_id, profession_id);

--
-- Name: ally_professions ally_specialties_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_professions
    ADD CONSTRAINT ally_specialties_pkey PRIMARY KEY (ally_specialties_id);

--
-- Name: ally_types ally_types_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_types
    ADD CONSTRAINT ally_types_code_key UNIQUE (code);

--
-- Name: ally_types ally_types_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_types
    ADD CONSTRAINT ally_types_name_key UNIQUE (name);

--
-- Name: ally_types ally_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_types
    ADD CONSTRAINT ally_types_pkey PRIMARY KEY (ally_types_id);

--
-- Name: ally_types ally_types_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_types
    ADD CONSTRAINT ally_types_uuid_key UNIQUE (uuid);

--
-- Name: ally_users ally_users_ally_id_user_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_users
    ADD CONSTRAINT ally_users_ally_id_user_id_key UNIQUE (ally_id, user_id);

--
-- Name: ally_users ally_users_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_users
    ADD CONSTRAINT ally_users_pkey PRIMARY KEY (ally_users_id);

--
-- Name: ally_users ally_users_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_users
    ADD CONSTRAINT ally_users_uuid_key UNIQUE (uuid);

--
-- Name: attached_files attached_files_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.attached_files
    ADD CONSTRAINT attached_files_pkey PRIMARY KEY (attached_files_id);

--
-- Name: attached_files attached_files_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.attached_files
    ADD CONSTRAINT attached_files_uuid_key UNIQUE (uuid);

--
-- Name: entity_config audit_entity_config_entity_key_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.entity_config
    ADD CONSTRAINT audit_entity_config_entity_key_key UNIQUE (entity_key);

--
-- Name: entity_config audit_entity_config_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.entity_config
    ADD CONSTRAINT audit_entity_config_pkey PRIMARY KEY (entity_config_id);

--
-- Name: entity_config audit_entity_config_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.entity_config
    ADD CONSTRAINT audit_entity_config_uuid_key UNIQUE (uuid);

--
-- Name: banks banks_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.banks
    ADD CONSTRAINT banks_code_key UNIQUE (code);

--
-- Name: banks banks_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.banks
    ADD CONSTRAINT banks_pkey PRIMARY KEY (banks_id);

--
-- Name: banks banks_tax_document_type_tax_document_number_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.banks
    ADD CONSTRAINT banks_tax_document_type_tax_document_number_key UNIQUE (tax_document_type, tax_document_number);

--
-- Name: banks banks_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.banks
    ADD CONSTRAINT banks_uuid_key UNIQUE (uuid);

--
-- Name: beneficiaries beneficiaries_member_id_person_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_member_id_person_id_key UNIQUE (member_id, person_id);

--
-- Name: beneficiaries beneficiaries_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_pkey PRIMARY KEY (beneficiaries_id);

--
-- Name: beneficiaries beneficiaries_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_uuid_key UNIQUE (uuid);

--
-- Name: benefit_usages benefit_usages_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_pkey PRIMARY KEY (benefit_usages_id);

--
-- Name: benefit_usages benefit_usages_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_uuid_key UNIQUE (uuid);

--
-- Name: campaign_promoters campaign_promoters_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_promoters
    ADD CONSTRAINT campaign_promoters_pkey PRIMARY KEY (campaign_promoters_id);

--
-- Name: campaign_promoters campaign_promoters_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_promoters
    ADD CONSTRAINT campaign_promoters_uuid_key UNIQUE (uuid);

--
-- Name: campaign_transaction_exceptions campaign_transaction_exceptions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_exceptions
    ADD CONSTRAINT campaign_transaction_exceptions_pkey PRIMARY KEY (campaign_transaction_exceptions_id);

--
-- Name: campaign_transaction_exceptions campaign_transaction_exceptions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_exceptions
    ADD CONSTRAINT campaign_transaction_exceptions_uuid_key UNIQUE (uuid);

--
-- Name: campaign_transaction_links campaign_transaction_links_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_links
    ADD CONSTRAINT campaign_transaction_links_pkey PRIMARY KEY (campaign_transaction_links_id);

--
-- Name: campaign_transaction_links campaign_transaction_links_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_links
    ADD CONSTRAINT campaign_transaction_links_uuid_key UNIQUE (uuid);

--
-- Name: campaigns campaigns_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaigns
    ADD CONSTRAINT campaigns_pkey PRIMARY KEY (campaigns_id);

--
-- Name: campaigns campaigns_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaigns
    ADD CONSTRAINT campaigns_uuid_key UNIQUE (uuid);

--
-- Name: cities cities_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (cities_id);

--
-- Name: cities cities_state_id_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.cities
    ADD CONSTRAINT cities_state_id_name_key UNIQUE (state_id, name);

--
-- Name: cities cities_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.cities
    ADD CONSTRAINT cities_uuid_key UNIQUE (uuid);

--
-- Name: collection_commission_tier_promoter_types collection_commission_tier_pr_collection_commission_tier_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tier_promoter_types
    ADD CONSTRAINT collection_commission_tier_pr_collection_commission_tier_id_key UNIQUE (collection_commission_tier_id, promoter_type_id);

--
-- Name: collection_commission_tier_promoter_types collection_commission_tier_promoter_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tier_promoter_types
    ADD CONSTRAINT collection_commission_tier_promoter_types_pkey PRIMARY KEY (collection_commission_tier_promoter_types_id);

--
-- Name: collection_commission_tiers collection_commission_tiers_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tiers
    ADD CONSTRAINT collection_commission_tiers_pkey PRIMARY KEY (collection_commission_tiers_id);

--
-- Name: collection_commission_tiers collection_commission_tiers_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tiers
    ADD CONSTRAINT collection_commission_tiers_uuid_key UNIQUE (uuid);

--
-- Name: commission_bonus_rule_promoter_types commission_bonus_rule_promote_commission_bonus_rule_id_prom_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rule_promoter_types
    ADD CONSTRAINT commission_bonus_rule_promote_commission_bonus_rule_id_prom_key UNIQUE (commission_bonus_rule_id, promoter_type_id);

--
-- Name: commission_bonus_rule_promoter_types commission_bonus_rule_promoter_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rule_promoter_types
    ADD CONSTRAINT commission_bonus_rule_promoter_types_pkey PRIMARY KEY (commission_bonus_rule_promoter_types_id);

--
-- Name: commission_bonus_rules commission_bonus_rules_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_pkey PRIMARY KEY (commission_bonus_rules_id);

--
-- Name: commission_bonus_rules commission_bonus_rules_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_uuid_key UNIQUE (uuid);

--
-- Name: commission_retroactive_topup_cuts commission_retroactive_topup_cuts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT commission_retroactive_topup_cuts_pkey PRIMARY KEY (commission_retroactive_topup_cuts_id);

--
-- Name: commission_retroactive_topup_cuts commission_retroactive_topup_cuts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT commission_retroactive_topup_cuts_uuid_key UNIQUE (uuid);

--
-- Name: commission_retroactive_topups commission_retroactive_topups_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT commission_retroactive_topups_pkey PRIMARY KEY (commission_retroactive_topups_id);

--
-- Name: commission_retroactive_topups commission_retroactive_topups_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT commission_retroactive_topups_uuid_key UNIQUE (uuid);

--
-- Name: commission_tier_promoter_types commission_tier_promoter_type_commission_tier_id_promoter_t_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tier_promoter_types
    ADD CONSTRAINT commission_tier_promoter_type_commission_tier_id_promoter_t_key UNIQUE (commission_tier_id, promoter_type_id);

--
-- Name: commission_tier_promoter_types commission_tier_promoter_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tier_promoter_types
    ADD CONSTRAINT commission_tier_promoter_types_pkey PRIMARY KEY (commission_tier_promoter_types_id);

--
-- Name: commission_tiers commission_tiers_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tiers
    ADD CONSTRAINT commission_tiers_pkey PRIMARY KEY (commission_tiers_id);

--
-- Name: commission_tiers commission_tiers_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tiers
    ADD CONSTRAINT commission_tiers_uuid_key UNIQUE (uuid);

--
-- Name: commissions commissions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_pkey PRIMARY KEY (commissions_id);

--
-- Name: commissions commissions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_uuid_key UNIQUE (uuid);

--
-- Name: company_bank_account_payment_methods company_bank_account_payment_methods_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_account_payment_methods
    ADD CONSTRAINT company_bank_account_payment_methods_pkey PRIMARY KEY (company_bank_account_id, payment_method_id);

--
-- Name: company_bank_accounts company_bank_accounts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_pkey PRIMARY KEY (company_bank_accounts_id);

--
-- Name: company_bank_accounts company_bank_accounts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_award_settlements competitive_commission_award_settlements_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award_settlements_pkey PRIMARY KEY (competitive_commission_award_settlements_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award_settlements_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award_settlements_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_awards competitive_commission_awards_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_pkey PRIMARY KEY (competitive_commission_awards_id);

--
-- Name: competitive_commission_awards competitive_commission_awards_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_decisions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_decisions_pkey PRIMARY KEY (competitive_commission_manual_decisions_id);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_decisions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_decisions_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_rule_positions competitive_commission_rule_positions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_positions
    ADD CONSTRAINT competitive_commission_rule_positions_pkey PRIMARY KEY (competitive_commission_rule_positions_id);

--
-- Name: competitive_commission_rule_positions competitive_commission_rule_positions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_positions
    ADD CONSTRAINT competitive_commission_rule_positions_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_rule_promoter_types competitive_commission_rule_promoter_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_promoter_types
    ADD CONSTRAINT competitive_commission_rule_promoter_types_pkey PRIMARY KEY (competitive_commission_rule_id, promoter_type_id);

--
-- Name: competitive_commission_rule_ranks competitive_commission_rule_ranks_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_ranks
    ADD CONSTRAINT competitive_commission_rule_ranks_pkey PRIMARY KEY (competitive_commission_rule_id, rank_id);

--
-- Name: competitive_commission_rules competitive_commission_rules_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rules
    ADD CONSTRAINT competitive_commission_rules_pkey PRIMARY KEY (competitive_commission_rules_id);

--
-- Name: competitive_commission_rules competitive_commission_rules_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rules
    ADD CONSTRAINT competitive_commission_rules_uuid_key UNIQUE (uuid);

--
-- Name: competitive_commission_tie_candidates competitive_commission_tie_candidates_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_tie_candidates
    ADD CONSTRAINT competitive_commission_tie_candidates_pkey PRIMARY KEY (competitive_commission_tie_id, promoter_id);

--
-- Name: competitive_commission_ties competitive_commission_ties_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_ties
    ADD CONSTRAINT competitive_commission_ties_pkey PRIMARY KEY (competitive_commission_ties_id);

--
-- Name: competitive_commission_ties competitive_commission_ties_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_ties
    ADD CONSTRAINT competitive_commission_ties_uuid_key UNIQUE (uuid);

--
-- Name: contact_messages contact_messages_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.contact_messages
    ADD CONSTRAINT contact_messages_pkey PRIMARY KEY (contact_messages_id);

--
-- Name: contact_messages contact_messages_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.contact_messages
    ADD CONSTRAINT contact_messages_uuid_key UNIQUE (uuid);

--
-- Name: corporate_contracts corporate_contracts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.corporate_contracts
    ADD CONSTRAINT corporate_contracts_pkey PRIMARY KEY (corporate_contracts_id);

--
-- Name: corporate_contracts corporate_contracts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.corporate_contracts
    ADD CONSTRAINT corporate_contracts_uuid_key UNIQUE (uuid);

--
-- Name: countries countries_iso_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.countries
    ADD CONSTRAINT countries_iso_code_key UNIQUE (iso_code);

--
-- Name: countries countries_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.countries
    ADD CONSTRAINT countries_name_key UNIQUE (name);

--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (countries_id);

--
-- Name: countries countries_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.countries
    ADD CONSTRAINT countries_uuid_key UNIQUE (uuid);

--
-- Name: currencies currencies_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.currencies
    ADD CONSTRAINT currencies_code_key UNIQUE (code);

--
-- Name: currencies currencies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.currencies
    ADD CONSTRAINT currencies_pkey PRIMARY KEY (currencies_id);

--
-- Name: currencies currencies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.currencies
    ADD CONSTRAINT currencies_uuid_key UNIQUE (uuid);

--
-- Name: data_change_audit_log data_change_audit_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.data_change_audit_log
    ADD CONSTRAINT data_change_audit_log_pkey PRIMARY KEY (data_change_audit_log_id);

--
-- Name: data_change_audit_log data_change_audit_log_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.data_change_audit_log
    ADD CONSTRAINT data_change_audit_log_uuid_key UNIQUE (uuid);

--
-- Name: document_types document_types_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.document_types
    ADD CONSTRAINT document_types_code_key UNIQUE (code);

--
-- Name: document_types document_types_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.document_types
    ADD CONSTRAINT document_types_name_key UNIQUE (name);

--
-- Name: document_types document_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.document_types
    ADD CONSTRAINT document_types_pkey PRIMARY KEY (document_types_id);

--
-- Name: document_types document_types_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.document_types
    ADD CONSTRAINT document_types_uuid_key UNIQUE (uuid);

--
-- Name: exchange_rates exchange_rates_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.exchange_rates
    ADD CONSTRAINT exchange_rates_pkey PRIMARY KEY (exchange_rates_id);

--
-- Name: exchange_rates exchange_rates_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.exchange_rates
    ADD CONSTRAINT exchange_rates_uuid_key UNIQUE (uuid);

--
-- Name: file_download_links file_download_links_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.file_download_links
    ADD CONSTRAINT file_download_links_pkey PRIMARY KEY (file_download_links_id);

--
-- Name: file_download_links file_download_links_token_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.file_download_links
    ADD CONSTRAINT file_download_links_token_key UNIQUE (token);

--
-- Name: file_type_policies file_type_policies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.file_type_policies
    ADD CONSTRAINT file_type_policies_pkey PRIMARY KEY (file_type_policies_id);

--
-- Name: file_type_policies file_type_policies_visibility_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.file_type_policies
    ADD CONSTRAINT file_type_policies_visibility_key UNIQUE (visibility);

--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);

--
-- Name: genders genders_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.genders
    ADD CONSTRAINT genders_code_key UNIQUE (code);

--
-- Name: genders genders_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.genders
    ADD CONSTRAINT genders_name_key UNIQUE (name);

--
-- Name: genders genders_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.genders
    ADD CONSTRAINT genders_pkey PRIMARY KEY (genders_id);

--
-- Name: genders genders_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.genders
    ADD CONSTRAINT genders_uuid_key UNIQUE (uuid);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_pkey PRIMARY KEY (hierarchy_override_tiers_id);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_uuid_key UNIQUE (uuid);

--
-- Name: holidays holidays_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.holidays
    ADD CONSTRAINT holidays_pkey PRIMARY KEY (holidays_id);

--
-- Name: holidays holidays_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.holidays
    ADD CONSTRAINT holidays_uuid_key UNIQUE (uuid);

--
-- Name: licensing_bodies licensing_bodies_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.licensing_bodies
    ADD CONSTRAINT licensing_bodies_code_key UNIQUE (code);

--
-- Name: licensing_bodies licensing_bodies_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.licensing_bodies
    ADD CONSTRAINT licensing_bodies_name_key UNIQUE (name);

--
-- Name: licensing_bodies licensing_bodies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.licensing_bodies
    ADD CONSTRAINT licensing_bodies_pkey PRIMARY KEY (licensing_bodies_id);

--
-- Name: licensing_bodies licensing_bodies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.licensing_bodies
    ADD CONSTRAINT licensing_bodies_uuid_key UNIQUE (uuid);

--
-- Name: login_audit_log login_audit_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.login_audit_log
    ADD CONSTRAINT login_audit_log_pkey PRIMARY KEY (login_audit_log_id);

--
-- Name: login_audit_log login_audit_log_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.login_audit_log
    ADD CONSTRAINT login_audit_log_uuid_key UNIQUE (uuid);

--
-- Name: marital_statuses marital_statuses_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.marital_statuses
    ADD CONSTRAINT marital_statuses_code_key UNIQUE (code);

--
-- Name: marital_statuses marital_statuses_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.marital_statuses
    ADD CONSTRAINT marital_statuses_name_key UNIQUE (name);

--
-- Name: marital_statuses marital_statuses_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.marital_statuses
    ADD CONSTRAINT marital_statuses_pkey PRIMARY KEY (marital_statuses_id);

--
-- Name: marital_statuses marital_statuses_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.marital_statuses
    ADD CONSTRAINT marital_statuses_uuid_key UNIQUE (uuid);

--
-- Name: media_items media_items_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.media_items
    ADD CONSTRAINT media_items_pkey PRIMARY KEY (media_items_id);

--
-- Name: media_items media_items_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.media_items
    ADD CONSTRAINT media_items_uuid_key UNIQUE (uuid);

--
-- Name: medical_record_access_log medical_record_access_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_pkey PRIMARY KEY (medical_record_access_log_id);

--
-- Name: medical_record_access_log medical_record_access_log_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_uuid_key UNIQUE (uuid);

--
-- Name: medical_records medical_records_person_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_records
    ADD CONSTRAINT medical_records_person_id_key UNIQUE (person_id);

--
-- Name: medical_records medical_records_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_records
    ADD CONSTRAINT medical_records_pkey PRIMARY KEY (medical_records_id);

--
-- Name: medical_records medical_records_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_records
    ADD CONSTRAINT medical_records_uuid_key UNIQUE (uuid);

--
-- Name: professions medical_specialties_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.professions
    ADD CONSTRAINT medical_specialties_code_key UNIQUE (code);

--
-- Name: professions medical_specialties_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.professions
    ADD CONSTRAINT medical_specialties_name_key UNIQUE (name);

--
-- Name: professions medical_specialties_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.professions
    ADD CONSTRAINT medical_specialties_pkey PRIMARY KEY (professions_id);

--
-- Name: professions medical_specialties_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.professions
    ADD CONSTRAINT medical_specialties_uuid_key UNIQUE (uuid);

--
-- Name: member_documents member_documents_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_documents
    ADD CONSTRAINT member_documents_pkey PRIMARY KEY (member_documents_id);

--
-- Name: member_documents member_documents_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_documents
    ADD CONSTRAINT member_documents_uuid_key UNIQUE (uuid);

--
-- Name: member_promoter_assignments member_promoter_assignments_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_pkey PRIMARY KEY (member_promoter_assignments_id);

--
-- Name: member_promoter_assignments member_promoter_assignments_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_uuid_key UNIQUE (uuid);

--
-- Name: members members_person_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_person_id_key UNIQUE (person_id);

--
-- Name: members members_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_pkey PRIMARY KEY (members_id);

--
-- Name: members members_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_uuid_key UNIQUE (uuid);

--
-- Name: membership_charges membership_charges_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_pkey PRIMARY KEY (membership_charges_id);

--
-- Name: membership_charges membership_charges_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_uuid_key UNIQUE (uuid);

--
-- Name: membership_promotions membership_promotions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_pkey PRIMARY KEY (membership_promotions_id);

--
-- Name: membership_promotions membership_promotions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_uuid_key UNIQUE (uuid);

--
-- Name: memberships memberships_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_pkey PRIMARY KEY (memberships_id);

--
-- Name: memberships memberships_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_uuid_key UNIQUE (uuid);

--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (notifications_id);

--
-- Name: notifications notifications_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.notifications
    ADD CONSTRAINT notifications_uuid_key UNIQUE (uuid);

--
-- Name: occupations occupations_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.occupations
    ADD CONSTRAINT occupations_name_key UNIQUE (name);

--
-- Name: occupations occupations_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.occupations
    ADD CONSTRAINT occupations_pkey PRIMARY KEY (occupations_id);

--
-- Name: occupations occupations_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.occupations
    ADD CONSTRAINT occupations_uuid_key UNIQUE (uuid);

--
-- Name: onboarding_policies onboarding_policies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.onboarding_policies
    ADD CONSTRAINT onboarding_policies_pkey PRIMARY KEY (onboarding_policies_id);

--
-- Name: onboarding_policies onboarding_policies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.onboarding_policies
    ADD CONSTRAINT onboarding_policies_uuid_key UNIQUE (uuid);

--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (organizations_id);

--
-- Name: organizations organizations_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.organizations
    ADD CONSTRAINT organizations_uuid_key UNIQUE (uuid);

--
-- Name: payer_bank_account_payment_methods payer_bank_account_payment_methods_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_account_payment_methods
    ADD CONSTRAINT payer_bank_account_payment_methods_pkey PRIMARY KEY (payer_bank_account_id, payment_method_id);

--
-- Name: payer_bank_accounts payer_bank_accounts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_accounts
    ADD CONSTRAINT payer_bank_accounts_pkey PRIMARY KEY (payer_bank_accounts_id);

--
-- Name: payer_bank_accounts payer_bank_accounts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_accounts
    ADD CONSTRAINT payer_bank_accounts_uuid_key UNIQUE (uuid);

--
-- Name: payment_categories payment_categories_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_categories
    ADD CONSTRAINT payment_categories_code_key UNIQUE (code);

--
-- Name: payment_categories payment_categories_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_categories
    ADD CONSTRAINT payment_categories_pkey PRIMARY KEY (payment_categories_id);

--
-- Name: payment_categories payment_categories_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_categories
    ADD CONSTRAINT payment_categories_uuid_key UNIQUE (uuid);

--
-- Name: payment_credit_claims payment_credit_claims_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_credit_claims
    ADD CONSTRAINT payment_credit_claims_pkey PRIMARY KEY (payment_credit_claims_id);

--
-- Name: payment_credit_claims payment_credit_claims_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_credit_claims
    ADD CONSTRAINT payment_credit_claims_uuid_key UNIQUE (uuid);

--
-- Name: payment_lines payment_lines_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_pkey PRIMARY KEY (payment_line_id);

--
-- Name: payment_lines payment_lines_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_uuid_key UNIQUE (uuid);

--
-- Name: payment_methods payment_methods_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_methods
    ADD CONSTRAINT payment_methods_code_key UNIQUE (code);

--
-- Name: payment_methods payment_methods_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (payment_methods_id);

--
-- Name: payment_methods payment_methods_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_methods
    ADD CONSTRAINT payment_methods_uuid_key UNIQUE (uuid);

--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payments_id);

--
-- Name: payments payments_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_uuid_key UNIQUE (uuid);

--
-- Name: permission_domains permission_domains_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permission_domains
    ADD CONSTRAINT permission_domains_code_key UNIQUE (code);

--
-- Name: permission_domains permission_domains_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permission_domains
    ADD CONSTRAINT permission_domains_name_key UNIQUE (name);

--
-- Name: permission_domains permission_domains_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permission_domains
    ADD CONSTRAINT permission_domains_pkey PRIMARY KEY (permission_domains_id);

--
-- Name: permission_domains permission_domains_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permission_domains
    ADD CONSTRAINT permission_domains_uuid_key UNIQUE (uuid);

--
-- Name: permissions permissions_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permissions
    ADD CONSTRAINT permissions_name_key UNIQUE (name);

--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permissions_id);

--
-- Name: permissions permissions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permissions
    ADD CONSTRAINT permissions_uuid_key UNIQUE (uuid);

--
-- Name: person_credit_entries person_credit_entries_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_pkey PRIMARY KEY (person_credit_entries_id);

--
-- Name: person_credit_entries person_credit_entries_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_uuid_key UNIQUE (uuid);

--
-- Name: person_professional_licenses person_professional_licenses_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_professional_licenses
    ADD CONSTRAINT person_professional_licenses_pkey PRIMARY KEY (person_professional_licenses_id);

--
-- Name: person_professional_licenses person_professional_licenses_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_professional_licenses
    ADD CONSTRAINT person_professional_licenses_uuid_key UNIQUE (uuid);

--
-- Name: persons persons_document_type_document_number_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_document_type_document_number_key UNIQUE (document_type, document_number);

--
-- Name: persons persons_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_pkey PRIMARY KEY (persons_id);

--
-- Name: persons persons_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_uuid_key UNIQUE (uuid);

--
-- Name: plans plans_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.plans
    ADD CONSTRAINT plans_code_key UNIQUE (code);

--
-- Name: plans plans_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.plans
    ADD CONSTRAINT plans_pkey PRIMARY KEY (plans_id);

--
-- Name: plans plans_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.plans
    ADD CONSTRAINT plans_uuid_key UNIQUE (uuid);

--
-- Name: product_categories product_categories_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.product_categories
    ADD CONSTRAINT product_categories_code_key UNIQUE (code);

--
-- Name: product_categories product_categories_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.product_categories
    ADD CONSTRAINT product_categories_name_key UNIQUE (name);

--
-- Name: product_categories product_categories_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.product_categories
    ADD CONSTRAINT product_categories_pkey PRIMARY KEY (product_categories_id);

--
-- Name: product_categories product_categories_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.product_categories
    ADD CONSTRAINT product_categories_uuid_key UNIQUE (uuid);

--
-- Name: products products_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.products
    ADD CONSTRAINT products_code_key UNIQUE (code);

--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (products_id);

--
-- Name: products products_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.products
    ADD CONSTRAINT products_uuid_key UNIQUE (uuid);

--
-- Name: promoter_bonus_awards promoter_bonus_awards_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_bonus_awards
    ADD CONSTRAINT promoter_bonus_awards_pkey PRIMARY KEY (promoter_bonus_awards_id);

--
-- Name: promoter_bonus_awards promoter_bonus_awards_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_bonus_awards
    ADD CONSTRAINT promoter_bonus_awards_uuid_key UNIQUE (uuid);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_pkey PRIMARY KEY (promoter_hierarchy_overrides_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_uuid_key UNIQUE (uuid);

--
-- Name: promoter_member_contacts promoter_member_contacts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_member_contacts
    ADD CONSTRAINT promoter_member_contacts_pkey PRIMARY KEY (promoter_member_contacts_id);

--
-- Name: promoter_member_contacts promoter_member_contacts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_member_contacts
    ADD CONSTRAINT promoter_member_contacts_uuid_key UNIQUE (uuid);

--
-- Name: promoter_payout_account_payment_methods promoter_payout_account_payment_methods_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_account_payment_methods
    ADD CONSTRAINT promoter_payout_account_payment_methods_pkey PRIMARY KEY (promoter_payout_account_id, payment_method_id);

--
-- Name: promoter_payout_accounts promoter_payout_accounts_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_accounts
    ADD CONSTRAINT promoter_payout_accounts_pkey PRIMARY KEY (promoter_payout_accounts_id);

--
-- Name: promoter_payout_accounts promoter_payout_accounts_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_accounts
    ADD CONSTRAINT promoter_payout_accounts_uuid_key UNIQUE (uuid);

--
-- Name: promoter_ranks promoter_ranks_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_code_key UNIQUE (code);

--
-- Name: promoter_ranks promoter_ranks_hierarchy_level_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_hierarchy_level_key UNIQUE (hierarchy_level) DEFERRABLE INITIALLY DEFERRED;

--
-- Name: promoter_ranks promoter_ranks_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_name_key UNIQUE (name);

--
-- Name: promoter_ranks promoter_ranks_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_pkey PRIMARY KEY (promoter_ranks_id);

--
-- Name: promoter_ranks promoter_ranks_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_uuid_key UNIQUE (uuid);

--
-- Name: promoter_references promoter_references_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_references
    ADD CONSTRAINT promoter_references_pkey PRIMARY KEY (promoter_references_id);

--
-- Name: promoter_references promoter_references_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_references
    ADD CONSTRAINT promoter_references_uuid_key UNIQUE (uuid);

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_pkey PRIMARY KEY (promoter_supervisor_assignments_id);

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_uuid_key UNIQUE (uuid);

--
-- Name: promoter_types promoter_types_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_types
    ADD CONSTRAINT promoter_types_code_key UNIQUE (code);

--
-- Name: promoter_types promoter_types_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_types
    ADD CONSTRAINT promoter_types_name_key UNIQUE (name);

--
-- Name: promoter_types promoter_types_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_types
    ADD CONSTRAINT promoter_types_pkey PRIMARY KEY (promoter_types_id);

--
-- Name: promoter_types promoter_types_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_types
    ADD CONSTRAINT promoter_types_uuid_key UNIQUE (uuid);

--
-- Name: promoters promoters_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_pkey PRIMARY KEY (promoters_id);

--
-- Name: promoters promoters_referral_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_referral_code_key UNIQUE (referral_code);

--
-- Name: promoters promoters_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_uuid_key UNIQUE (uuid);

--
-- Name: promotion_plans promotion_plans_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotion_plans
    ADD CONSTRAINT promotion_plans_pkey PRIMARY KEY (promotion_id, plan_id);

--
-- Name: promotions promotions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotions
    ADD CONSTRAINT promotions_pkey PRIMARY KEY (promotions_id);

--
-- Name: promotions promotions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotions
    ADD CONSTRAINT promotions_uuid_key UNIQUE (uuid);

--
-- Name: referrals referrals_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_pkey PRIMARY KEY (referrals_id);

--
-- Name: referrals referrals_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_uuid_key UNIQUE (uuid);

--
-- Name: report_audit_log report_audit_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_audit_log
    ADD CONSTRAINT report_audit_log_pkey PRIMARY KEY (report_audit_log_id);

--
-- Name: report_audit_log report_audit_log_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_audit_log
    ADD CONSTRAINT report_audit_log_uuid_key UNIQUE (uuid);

--
-- Name: report_share report_share_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_share
    ADD CONSTRAINT report_share_pkey PRIMARY KEY (report_share_id);

--
-- Name: report_share report_share_share_token_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_share
    ADD CONSTRAINT report_share_share_token_key UNIQUE (share_token);

--
-- Name: report_share report_share_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_share
    ADD CONSTRAINT report_share_uuid_key UNIQUE (uuid);

--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_permissions_id);

--
-- Name: role_permissions role_permissions_role_id_permission_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.role_permissions
    ADD CONSTRAINT role_permissions_role_id_permission_id_key UNIQUE (role_id, permission_id);

--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);

--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (roles_id);

--
-- Name: roles roles_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.roles
    ADD CONSTRAINT roles_uuid_key UNIQUE (uuid);

--
-- Name: scheduled_job_runs scheduled_job_runs_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_job_runs
    ADD CONSTRAINT scheduled_job_runs_pkey PRIMARY KEY (scheduled_job_runs_id);

--
-- Name: scheduled_job_runs scheduled_job_runs_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_job_runs
    ADD CONSTRAINT scheduled_job_runs_uuid_key UNIQUE (uuid);

--
-- Name: scheduled_jobs scheduled_jobs_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_jobs
    ADD CONSTRAINT scheduled_jobs_code_key UNIQUE (code);

--
-- Name: scheduled_jobs scheduled_jobs_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_jobs
    ADD CONSTRAINT scheduled_jobs_pkey PRIMARY KEY (scheduled_jobs_id);

--
-- Name: scheduled_jobs scheduled_jobs_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_jobs
    ADD CONSTRAINT scheduled_jobs_uuid_key UNIQUE (uuid);

--
-- Name: security_policies security_policies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.security_policies
    ADD CONSTRAINT security_policies_pkey PRIMARY KEY (security_policies_id);

--
-- Name: security_policies security_policies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.security_policies
    ADD CONSTRAINT security_policies_uuid_key UNIQUE (uuid);

--
-- Name: states states_country_id_code_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.states
    ADD CONSTRAINT states_country_id_code_key UNIQUE (country_id, code);

--
-- Name: states states_country_id_name_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.states
    ADD CONSTRAINT states_country_id_name_key UNIQUE (country_id, name);

--
-- Name: states states_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.states
    ADD CONSTRAINT states_pkey PRIMARY KEY (states_id);

--
-- Name: states states_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.states
    ADD CONSTRAINT states_uuid_key UNIQUE (uuid);

--
-- Name: subsidies subsidies_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidies
    ADD CONSTRAINT subsidies_pkey PRIMARY KEY (subsidies_id);

--
-- Name: subsidies subsidies_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidies
    ADD CONSTRAINT subsidies_uuid_key UNIQUE (uuid);

--
-- Name: subsidy_audit_log subsidy_audit_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_audit_log
    ADD CONSTRAINT subsidy_audit_log_pkey PRIMARY KEY (subsidy_audit_log_id);

--
-- Name: subsidy_audit_log subsidy_audit_log_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_audit_log
    ADD CONSTRAINT subsidy_audit_log_uuid_key UNIQUE (uuid);

--
-- Name: subsidy_beneficiaries subsidy_beneficiaries_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_beneficiaries
    ADD CONSTRAINT subsidy_beneficiaries_pkey PRIMARY KEY (subsidy_beneficiaries_id);

--
-- Name: subsidy_beneficiaries subsidy_beneficiaries_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_beneficiaries
    ADD CONSTRAINT subsidy_beneficiaries_uuid_key UNIQUE (uuid);

--
-- Name: system_configs system_configs_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.system_configs
    ADD CONSTRAINT system_configs_pkey PRIMARY KEY (system_configs_id);

--
-- Name: system_configs system_configs_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.system_configs
    ADD CONSTRAINT system_configs_uuid_key UNIQUE (uuid);

--
-- Name: terms_acceptances terms_acceptances_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_pkey PRIMARY KEY (terms_acceptances_id);

--
-- Name: terms_acceptances terms_acceptances_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_uuid_key UNIQUE (uuid);

--
-- Name: terms_versions terms_versions_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_versions
    ADD CONSTRAINT terms_versions_pkey PRIMARY KEY (terms_versions_id);

--
-- Name: terms_versions terms_versions_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_versions
    ADD CONSTRAINT terms_versions_uuid_key UNIQUE (uuid);

--
-- Name: users uniq_users_person; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT uniq_users_person UNIQUE (person_id);

--
-- Name: allies uq_allies_ally_number; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT uq_allies_ally_number UNIQUE (ally_number);

--
-- Name: ally_opening_hours uq_ally_opening_hours_start; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_opening_hours
    ADD CONSTRAINT uq_ally_opening_hours_start UNIQUE (ally_id, day_of_week, opens_at);

--
-- Name: ally_products uq_ally_products_ally_product; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT uq_ally_products_ally_product UNIQUE (ally_id, product_id);

--
-- Name: campaign_promoters uq_campaign_promoters_campaign_promoter; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_promoters
    ADD CONSTRAINT uq_campaign_promoters_campaign_promoter UNIQUE (campaign_id, promoter_id);

--
-- Name: competitive_commission_award_settlements uq_ccas_cut; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT uq_ccas_cut UNIQUE (competitive_commission_rule_id, promoter_id, period_start, cut_kind, cut_sequence);

--
-- Name: commission_retroactive_topup_cuts uq_commission_retroactive_topup_cuts_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT uq_commission_retroactive_topup_cuts_key UNIQUE (promoter_id, ledger_type, accrual_period_start, accrual_period_end, cut_sequence);

--
-- Name: commission_retroactive_topups uq_commission_retroactive_topups_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT uq_commission_retroactive_topups_key UNIQUE (promoter_id, ledger_type, period_start, period_end);

--
-- Name: subsidy_beneficiaries uq_subsidy_beneficiaries; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_beneficiaries
    ADD CONSTRAINT uq_subsidy_beneficiaries UNIQUE (subsidy_id, beneficiary_id);

--
-- Name: user_password_history user_password_history_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_password_history
    ADD CONSTRAINT user_password_history_pkey PRIMARY KEY (user_password_history_id);

--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_roles_id);

--
-- Name: user_roles user_roles_user_id_role_id_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_roles
    ADD CONSTRAINT user_roles_user_id_role_id_key UNIQUE (user_id, role_id);

--
-- Name: user_sessions_log user_sessions_log_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_sessions_log
    ADD CONSTRAINT user_sessions_log_pkey PRIMARY KEY (user_sessions_log_id);

--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (users_id);

--
-- Name: users users_uuid_key; Type: CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT users_uuid_key UNIQUE (uuid);

--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON app.flyway_schema_history USING btree (success);

--
-- Name: idx_affiliation_request_logs_request; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_request_logs_request ON app.affiliation_request_logs USING btree (affiliation_request_id, action_at DESC);

--
-- Name: idx_affiliation_requests_channel; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_requests_channel ON app.affiliation_requests USING btree (channel);

--
-- Name: idx_affiliation_requests_payment_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_requests_payment_id ON app.affiliation_requests USING btree (payment_id) WHERE (payment_id IS NOT NULL);

--
-- Name: idx_affiliation_requests_plan; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_requests_plan ON app.affiliation_requests USING btree (plan_id);

--
-- Name: idx_affiliation_requests_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_requests_promoter ON app.affiliation_requests USING btree (promoter_id) WHERE (promoter_id IS NOT NULL);

--
-- Name: idx_affiliation_requests_status_created; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_affiliation_requests_status_created ON app.affiliation_requests USING btree (status, created_at DESC);

--
-- Name: idx_allies_captured_by_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_captured_by_promoter ON app.allies USING btree (captured_by_promoter_id) WHERE (captured_by_promoter_id IS NOT NULL);

--
-- Name: idx_allies_city; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_city ON app.allies USING btree (city_id) WHERE (city_id IS NOT NULL);

--
-- Name: idx_allies_host_ally_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_host_ally_id ON app.allies USING btree (host_ally_id) WHERE (host_ally_id IS NOT NULL);

--
-- Name: idx_allies_name_unaccent; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_name_unaccent ON app.allies USING gin (app.immutable_unaccent(lower((name)::text)) app.gin_trgm_ops);

--
-- Name: idx_allies_person_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_person_id ON app.allies USING btree (person_id) WHERE (person_id IS NOT NULL);

--
-- Name: idx_allies_published; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_published ON app.allies USING btree (is_published) WHERE is_published;

--
-- Name: idx_allies_registration_pending; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_registration_pending ON app.allies USING btree (created_at DESC) WHERE ((registration_status)::text = 'PENDING'::text);

--
-- Name: idx_allies_segment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_allies_segment ON app.allies USING btree (segment);

--
-- Name: idx_ally_agreements_ally_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_agreements_ally_status ON app.ally_agreements USING btree (ally_id, status);

--
-- Name: idx_ally_agreements_terms_version; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_agreements_terms_version ON app.ally_agreements USING btree (terms_version_id) WHERE (terms_version_id IS NOT NULL);

--
-- Name: idx_ally_ally_types_ally_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_ally_types_ally_type ON app.ally_ally_types USING btree (ally_type_id);

--
-- Name: idx_ally_opening_hours_day; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_opening_hours_day ON app.ally_opening_hours USING btree (day_of_week, opens_at, closes_at);

--
-- Name: idx_ally_patient_links_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_patient_links_member ON app.ally_patient_links USING btree (member_id);

--
-- Name: idx_ally_product_review_log_product; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_product_review_log_product ON app.ally_product_review_log USING btree (ally_product_id, action_at DESC);

--
-- Name: idx_ally_products_ally; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_products_ally ON app.ally_products USING btree (ally_id);

--
-- Name: idx_ally_products_pending_queue; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_products_pending_queue ON app.ally_products USING btree (created_at) WHERE ((review_status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'IN_REVIEW'::character varying])::text[]));

--
-- Name: idx_ally_products_product; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_products_product ON app.ally_products USING btree (product_id);

--
-- Name: idx_ally_products_published; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_products_published ON app.ally_products USING btree (ally_id) WHERE (is_published AND ((review_status)::text = 'APPROVED'::text));

--
-- Name: idx_ally_products_review_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_products_review_status ON app.ally_products USING btree (review_status);

--
-- Name: idx_ally_professions_profession; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_professions_profession ON app.ally_professions USING btree (profession_id);

--
-- Name: idx_ally_types_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_types_is_active ON app.ally_types USING btree (is_active) WHERE is_active;

--
-- Name: idx_ally_users_user; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ally_users_user ON app.ally_users USING btree (user_id);

--
-- Name: idx_attached_files_owner; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_attached_files_owner ON app.attached_files USING btree (owner_table, owner_uuid);

--
-- Name: idx_attached_files_uploader; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_attached_files_uploader ON app.attached_files USING btree (uploaded_by);

--
-- Name: idx_audit_entity_config_key; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_audit_entity_config_key ON app.entity_config USING btree (entity_key);

--
-- Name: idx_banks_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_banks_is_active ON app.banks USING btree (is_active) WHERE is_active;

--
-- Name: idx_beneficiaries_extra_unpaid; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_beneficiaries_extra_unpaid ON app.beneficiaries USING btree (member_id) WHERE ((is_active = true) AND (extra_inscription_paid = false));

--
-- Name: idx_beneficiaries_member_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_beneficiaries_member_active ON app.beneficiaries USING btree (member_id) WHERE (is_active = true);

--
-- Name: idx_beneficiaries_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_beneficiaries_person ON app.beneficiaries USING btree (person_id);

--
-- Name: idx_benefit_usages_active_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_active_status ON app.benefit_usages USING btree (status, usage_date DESC) WHERE is_active;

--
-- Name: idx_benefit_usages_ally_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_ally_date ON app.benefit_usages USING btree (ally_id, usage_date DESC);

--
-- Name: idx_benefit_usages_ally_product_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_ally_product_date ON app.benefit_usages USING btree (ally_product_id, usage_date DESC) WHERE (ally_product_id IS NOT NULL);

--
-- Name: idx_benefit_usages_ally_product_rated; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_ally_product_rated ON app.benefit_usages USING btree (ally_product_id) WHERE ((product_rating IS NOT NULL) AND is_active);

--
-- Name: idx_benefit_usages_ally_rated; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_ally_rated ON app.benefit_usages USING btree (ally_id) WHERE ((rated_at IS NOT NULL) AND is_active);

--
-- Name: idx_benefit_usages_beneficiary; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_beneficiary ON app.benefit_usages USING btree (beneficiary_id, usage_date DESC) WHERE (beneficiary_id IS NOT NULL);

--
-- Name: idx_benefit_usages_category_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_category_date ON app.benefit_usages USING btree (product_category_id, usage_date DESC) WHERE is_active;

--
-- Name: idx_benefit_usages_membership_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_benefit_usages_membership_date ON app.benefit_usages USING btree (membership_id, usage_date DESC);

--
-- Name: idx_bonus_awards_promoter_created; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_bonus_awards_promoter_created ON app.promoter_bonus_awards USING btree (promoter_id, created_at DESC) WHERE is_active;

--
-- Name: idx_bonus_awards_status_created; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_bonus_awards_status_created ON app.promoter_bonus_awards USING btree (status, created_at DESC) WHERE is_active;

--
-- Name: idx_campaign_promoters_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_promoters_campaign ON app.campaign_promoters USING btree (campaign_id);

--
-- Name: idx_campaign_promoters_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_promoters_promoter ON app.campaign_promoters USING btree (promoter_id);

--
-- Name: idx_campaign_transaction_exceptions_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_transaction_exceptions_campaign ON app.campaign_transaction_exceptions USING btree (campaign_id);

--
-- Name: idx_campaign_transaction_links_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_transaction_links_campaign ON app.campaign_transaction_links USING btree (campaign_id);

--
-- Name: idx_campaign_transaction_links_membership; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_transaction_links_membership ON app.campaign_transaction_links USING btree (membership_id) WHERE (membership_id IS NOT NULL);

--
-- Name: idx_campaign_transaction_links_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaign_transaction_links_payment ON app.campaign_transaction_links USING btree (payment_id) WHERE (payment_id IS NOT NULL);

--
-- Name: idx_campaigns_active_window; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaigns_active_window ON app.campaigns USING btree (is_active, starts_at, ends_at);

--
-- Name: idx_campaigns_exclusivity_group; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_campaigns_exclusivity_group ON app.campaigns USING btree (exclusivity_group) WHERE (exclusivity_group IS NOT NULL);

--
-- Name: idx_cca_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_cca_promoter ON app.competitive_commission_awards USING btree (promoter_id, created_at DESC);

--
-- Name: idx_cca_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_cca_status ON app.competitive_commission_awards USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PROVISIONAL'::character varying, 'PENDING'::character varying])::text[]));

--
-- Name: idx_ccas_award; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ccas_award ON app.competitive_commission_award_settlements USING btree (competitive_commission_award_id);

--
-- Name: idx_ccas_pending; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ccas_pending ON app.competitive_commission_award_settlements USING btree (status) WHERE ((status)::text = 'PENDING'::text);

--
-- Name: idx_ccmd_rule_period; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ccmd_rule_period ON app.competitive_commission_manual_decisions USING btree (competitive_commission_rule_id, period_start) WHERE (is_active AND ((status)::text = 'ACTIVE'::text));

--
-- Name: idx_ccr_active_eval; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ccr_active_eval ON app.competitive_commission_rules USING btree (accrual_period_strategy) WHERE is_active;

--
-- Name: idx_ccr_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_ccr_campaign ON app.competitive_commission_rules USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_cities_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_cities_is_active ON app.cities USING btree (is_active) WHERE is_active;

--
-- Name: idx_cities_state_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_cities_state_id ON app.cities USING btree (state_id);

--
-- Name: idx_collection_commission_tier_promoter_types_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_collection_commission_tier_promoter_types_type ON app.collection_commission_tier_promoter_types USING btree (promoter_type_id);

--
-- Name: idx_collection_commission_tiers_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_collection_commission_tiers_campaign ON app.collection_commission_tiers USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_collection_commission_tiers_lookup; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_collection_commission_tiers_lookup ON app.collection_commission_tiers USING btree (max_days) WHERE is_active;

--
-- Name: idx_commission_bonus_rule_promoter_types_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_bonus_rule_promoter_types_type ON app.commission_bonus_rule_promoter_types USING btree (promoter_type_id);

--
-- Name: idx_commission_bonus_rules_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_bonus_rules_active ON app.commission_bonus_rules USING btree (created_at DESC) WHERE is_active;

--
-- Name: idx_commission_bonus_rules_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_bonus_rules_campaign ON app.commission_bonus_rules USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_commission_retroactive_topup_cuts_promoter_period; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_retroactive_topup_cuts_promoter_period ON app.commission_retroactive_topup_cuts USING btree (promoter_id, ledger_type, accrual_period_start, accrual_period_end);

--
-- Name: idx_commission_retroactive_topups_payout_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_retroactive_topups_payout_payment ON app.commission_retroactive_topups USING btree (payout_payment_id);

--
-- Name: idx_commission_retroactive_topups_promoter_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_retroactive_topups_promoter_status ON app.commission_retroactive_topups USING btree (promoter_id, status, period_start, period_end);

--
-- Name: idx_commission_tier_promoter_types_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_tier_promoter_types_type ON app.commission_tier_promoter_types USING btree (promoter_type_id);

--
-- Name: idx_commission_tiers_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_tiers_campaign ON app.commission_tiers USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_commission_tiers_lookup; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commission_tiers_lookup ON app.commission_tiers USING btree (plan_type, applies_to, threshold_count DESC) WHERE is_active;

--
-- Name: idx_commissions_collection_tier; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_collection_tier ON app.commissions USING btree (collection_tier_id) WHERE (collection_tier_id IS NOT NULL);

--
-- Name: idx_commissions_currency; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_currency ON app.commissions USING btree (currency_id);

--
-- Name: idx_commissions_member_earned; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_member_earned ON app.commissions USING btree (member_id, earned_at DESC) WHERE is_active;

--
-- Name: idx_commissions_payment_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_payment_id ON app.commissions USING btree (payment_id);

--
-- Name: idx_commissions_payout_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_payout_payment ON app.commissions USING btree (payout_payment_id);

--
-- Name: idx_commissions_promoter_status_period; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_promoter_status_period ON app.commissions USING btree (promoter_id, status, period_start) WHERE is_active;

--
-- Name: idx_commissions_status_approval; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_status_approval ON app.commissions USING btree (status) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying])::text[]));

--
-- Name: idx_commissions_tier; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_commissions_tier ON app.commissions USING btree (commission_tier_id) WHERE (commission_tier_id IS NOT NULL);

--
-- Name: idx_company_account_methods_method; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_company_account_methods_method ON app.company_bank_account_payment_methods USING btree (payment_method_id);

--
-- Name: idx_contact_messages_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_contact_messages_is_active ON app.contact_messages USING btree (is_active) WHERE is_active;

--
-- Name: idx_contact_messages_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_contact_messages_status ON app.contact_messages USING btree (status);

--
-- Name: idx_corporate_contracts_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_corporate_contracts_active ON app.corporate_contracts USING btree (created_at DESC) WHERE is_active;

--
-- Name: idx_corporate_contracts_plan; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_corporate_contracts_plan ON app.corporate_contracts USING btree (plan_id);

--
-- Name: idx_countries_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_countries_is_active ON app.countries USING btree (is_active) WHERE is_active;

--
-- Name: idx_currencies_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_currencies_is_active ON app.currencies USING btree (is_active) WHERE is_active;

--
-- Name: idx_data_change_actor; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_data_change_actor ON app.data_change_audit_log USING btree (actor_id);

--
-- Name: idx_data_change_entity; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_data_change_entity ON app.data_change_audit_log USING btree (entity_key, entity_uuid);

--
-- Name: idx_data_change_entity_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_data_change_entity_id ON app.data_change_audit_log USING btree (entity_key, entity_id);

--
-- Name: idx_data_change_login_audit; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_data_change_login_audit ON app.data_change_audit_log USING btree (login_audit_log_id);

--
-- Name: idx_data_change_occurred_at; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_data_change_occurred_at ON app.data_change_audit_log USING btree (occurred_at DESC);

--
-- Name: idx_document_types_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_document_types_is_active ON app.document_types USING btree (is_active) WHERE is_active;

--
-- Name: idx_exchange_rates_pair_valid_from; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_exchange_rates_pair_valid_from ON app.exchange_rates USING btree (base_currency_id, quote_currency_id, valid_from DESC);

--
-- Name: idx_file_download_links_resource; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_file_download_links_resource ON app.file_download_links USING btree (resource_table, resource_uuid);

--
-- Name: idx_genders_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_genders_is_active ON app.genders USING btree (is_active) WHERE is_active;

--
-- Name: idx_hierarchy_override_tiers_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_hierarchy_override_tiers_campaign ON app.hierarchy_override_tiers USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_hierarchy_override_tiers_rank_category; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_hierarchy_override_tiers_rank_category ON app.hierarchy_override_tiers USING btree (rank_id, category, threshold_count DESC);

--
-- Name: idx_holidays_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_holidays_is_active ON app.holidays USING btree (is_active) WHERE is_active;

--
-- Name: idx_holidays_scope; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_holidays_scope ON app.holidays USING btree (country_id, state_id, city_id);

--
-- Name: idx_licensing_bodies_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_licensing_bodies_is_active ON app.licensing_bodies USING btree (is_active) WHERE is_active;

--
-- Name: idx_login_audit_active_expiry; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_active_expiry ON app.login_audit_log USING btree (session_expires_at) WHERE ((session_status)::text = 'ACTIVE'::text);

--
-- Name: idx_login_audit_active_user; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_active_user ON app.login_audit_log USING btree (user_id) WHERE ((session_status)::text = 'ACTIVE'::text);

--
-- Name: idx_login_audit_attempted_at; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_attempted_at ON app.login_audit_log USING btree (attempted_at DESC);

--
-- Name: idx_login_audit_created_at; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_created_at ON app.login_audit_log USING btree (created_at DESC);

--
-- Name: idx_login_audit_email_attempted; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_email_attempted ON app.login_audit_log USING btree (attempted_email, attempted_at DESC);

--
-- Name: idx_login_audit_failed; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_failed ON app.login_audit_log USING btree (attempted_at DESC) WHERE ((result)::text <> 'SUCCESS'::text);

--
-- Name: idx_login_audit_user_attempted; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_login_audit_user_attempted ON app.login_audit_log USING btree (user_id, attempted_at DESC);

--
-- Name: idx_marital_statuses_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_marital_statuses_is_active ON app.marital_statuses USING btree (is_active) WHERE is_active;

--
-- Name: idx_media_items_owner; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_media_items_owner ON app.media_items USING btree (owner_table, owner_uuid, sort_order) WHERE is_active;

--
-- Name: idx_medical_record_access_log_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_medical_record_access_log_member ON app.medical_record_access_log USING btree (member_id, accessed_at DESC);

--
-- Name: idx_member_documents_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_member_documents_member ON app.member_documents USING btree (member_id);

--
-- Name: idx_member_documents_member_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_member_documents_member_type ON app.member_documents USING btree (member_id, document_type);

--
-- Name: idx_member_promoter_assignments_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_member_promoter_assignments_member ON app.member_promoter_assignments USING btree (member_id, created_at DESC);

--
-- Name: idx_members_corporate_contract; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_members_corporate_contract ON app.members USING btree (corporate_contract_id) WHERE (corporate_contract_id IS NOT NULL);

--
-- Name: idx_members_enrolled_at; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_members_enrolled_at ON app.members USING btree (enrolled_at);

--
-- Name: idx_members_occupation; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_members_occupation ON app.members USING btree (occupation_id) WHERE (occupation_id IS NOT NULL);

--
-- Name: idx_members_promoter_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_members_promoter_id ON app.members USING btree (promoter_id) WHERE (promoter_id IS NOT NULL);

--
-- Name: idx_membership_charges_status_due_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_membership_charges_status_due_date ON app.membership_charges USING btree (status, due_date);

--
-- Name: idx_membership_promotions_promotion; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_membership_promotions_promotion ON app.membership_promotions USING btree (promotion_id);

--
-- Name: idx_memberships_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_memberships_campaign ON app.memberships USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_memberships_currency; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_memberships_currency ON app.memberships USING btree (currency_id);

--
-- Name: idx_memberships_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_memberships_member ON app.memberships USING btree (member_id);

--
-- Name: idx_memberships_status_next_due; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_memberships_status_next_due ON app.memberships USING btree (status, next_due_date) WHERE (is_active = true);

--
-- Name: idx_memberships_term_expiry; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_memberships_term_expiry ON app.memberships USING btree (expires_at) WHERE ((is_active = true) AND (reinscription_required = true));

--
-- Name: idx_notifications_dead_letter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_notifications_dead_letter ON app.notifications USING btree (created_at) WHERE ((status)::text = 'DEAD_LETTER'::text);

--
-- Name: idx_notifications_pending_due; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_notifications_pending_due ON app.notifications USING btree (scheduled_for) WHERE (((status)::text = 'PENDING'::text) AND is_active);

--
-- Name: idx_notifications_recipient_created; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_notifications_recipient_created ON app.notifications USING btree (recipient_user_id, created_at DESC) WHERE ((recipient_user_id IS NOT NULL) AND is_active);

--
-- Name: idx_notifications_retry_due; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_notifications_retry_due ON app.notifications USING btree (next_retry_at) WHERE (((status)::text = 'FAILED'::text) AND (next_retry_at IS NOT NULL) AND is_active);

--
-- Name: idx_notifications_source; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_notifications_source ON app.notifications USING btree (source_module, source_entity_uuid, template_code) WHERE (source_entity_uuid IS NOT NULL);

--
-- Name: idx_occupations_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_occupations_is_active ON app.occupations USING btree (is_active) WHERE is_active;

--
-- Name: idx_organizations_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_organizations_is_active ON app.organizations USING btree (is_active) WHERE is_active;

--
-- Name: idx_payer_account_methods_method; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payer_account_methods_method ON app.payer_bank_account_payment_methods USING btree (payment_method_id);

--
-- Name: idx_payer_bank_accounts_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payer_bank_accounts_person ON app.payer_bank_accounts USING btree (person_id, last_used_at DESC) WHERE is_active;

--
-- Name: idx_payment_categories_direction; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payment_categories_direction ON app.payment_categories USING btree (direction) WHERE is_active;

--
-- Name: idx_payment_credit_claims_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payment_credit_claims_status ON app.payment_credit_claims USING btree (claim_status, opened_at DESC);

--
-- Name: idx_payment_lines_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payment_lines_payment ON app.payment_lines USING btree (payment_id);

--
-- Name: idx_payment_lines_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payment_lines_status ON app.payment_lines USING btree (status) WHERE is_active;

--
-- Name: idx_payments_approved_period; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_approved_period ON app.payments USING btree (applied_period) WHERE (is_active AND ((status)::text = 'APPROVED'::text));

--
-- Name: idx_payments_beneficiary_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_beneficiary_person ON app.payments USING btree (beneficiary_person_id) WHERE (beneficiary_person_id IS NOT NULL);

--
-- Name: idx_payments_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_campaign ON app.payments USING btree (campaign_id) WHERE (campaign_id IS NOT NULL);

--
-- Name: idx_payments_corporate_contract; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_corporate_contract ON app.payments USING btree (corporate_contract_id) WHERE (corporate_contract_id IS NOT NULL);

--
-- Name: idx_payments_currency; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_currency ON app.payments USING btree (currency_id);

--
-- Name: idx_payments_direction_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_direction_status ON app.payments USING btree (direction, status);

--
-- Name: idx_payments_membership_date; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_membership_date ON app.payments USING btree (membership_id, payment_date DESC);

--
-- Name: idx_payments_payment_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_payment_type ON app.payments USING btree (payment_type_id);

--
-- Name: idx_payments_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_person ON app.payments USING btree (person_id);

--
-- Name: idx_payments_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_promoter ON app.payments USING btree (promoter_id);

--
-- Name: idx_payments_status_received; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payments_status_received ON app.payments USING btree (status, received_at DESC) WHERE is_active;

--
-- Name: idx_payout_account_methods_method; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_payout_account_methods_method ON app.promoter_payout_account_payment_methods USING btree (payment_method_id);

--
-- Name: idx_permission_domains_display_order; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_permission_domains_display_order ON app.permission_domains USING btree (display_order) WHERE is_active;

--
-- Name: idx_permission_domains_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_permission_domains_is_active ON app.permission_domains USING btree (is_active) WHERE is_active;

--
-- Name: idx_permissions_domain_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_permissions_domain_id ON app.permissions USING btree (domain_id);

--
-- Name: idx_permissions_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_permissions_is_active ON app.permissions USING btree (is_active) WHERE is_active;

--
-- Name: idx_person_credit_entries_charge; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_person_credit_entries_charge ON app.person_credit_entries USING btree (charge_id) WHERE (charge_id IS NOT NULL);

--
-- Name: idx_person_credit_entries_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_person_credit_entries_payment ON app.person_credit_entries USING btree (payment_id);

--
-- Name: idx_person_credit_entries_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_person_credit_entries_person ON app.person_credit_entries USING btree (person_id, currency_id) WHERE is_active;

--
-- Name: idx_person_professional_licenses_person; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_person_professional_licenses_person ON app.person_professional_licenses USING btree (person_id);

--
-- Name: idx_persons_city_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_persons_city_id ON app.persons USING btree (city_id) WHERE (city_id IS NOT NULL);

--
-- Name: idx_persons_email; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_persons_email ON app.persons USING btree (email) WHERE (email IS NOT NULL);

--
-- Name: idx_persons_full_name_unaccent; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_persons_full_name_unaccent ON app.persons USING gin (app.immutable_unaccent(lower((full_name)::text)) app.gin_trgm_ops);

--
-- Name: idx_persons_last_name; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_persons_last_name ON app.persons USING btree (last_name);

--
-- Name: idx_plans_currency; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_plans_currency ON app.plans USING btree (currency_id);

--
-- Name: idx_plans_published; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_plans_published ON app.plans USING btree (is_active) WHERE (is_active AND is_published);

--
-- Name: idx_plans_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_plans_type ON app.plans USING btree (type);

--
-- Name: idx_product_categories_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_product_categories_is_active ON app.product_categories USING btree (is_active) WHERE is_active;

--
-- Name: idx_products_category; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_products_category ON app.products USING btree (product_category_id);

--
-- Name: idx_products_name_trgm; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_products_name_trgm ON app.products USING gin (app.immutable_unaccent(lower((name)::text)) app.gin_trgm_ops);

--
-- Name: idx_products_owner_ally; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_products_owner_ally ON app.products USING btree (owner_ally_id) WHERE (owner_ally_id IS NOT NULL);

--
-- Name: idx_products_published; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_products_published ON app.products USING btree (products_id) WHERE (is_published AND is_active);

--
-- Name: idx_products_type; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_products_type ON app.products USING btree (product_type);

--
-- Name: idx_professions_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_professions_is_active ON app.professions USING btree (is_active) WHERE is_active;

--
-- Name: idx_professions_parent; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_professions_parent ON app.professions USING btree (parent_id);

--
-- Name: idx_promoter_hierarchy_overrides_payout_payment; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_hierarchy_overrides_payout_payment ON app.promoter_hierarchy_overrides USING btree (payout_payment_id);

--
-- Name: idx_promoter_hierarchy_overrides_promoter_period; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_hierarchy_overrides_promoter_period ON app.promoter_hierarchy_overrides USING btree (promoter_id, status, period_start, period_end);

--
-- Name: idx_promoter_hierarchy_overrides_source_commission; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_hierarchy_overrides_source_commission ON app.promoter_hierarchy_overrides USING btree (source_commission_id);

--
-- Name: idx_promoter_hierarchy_overrides_source_override; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_hierarchy_overrides_source_override ON app.promoter_hierarchy_overrides USING btree (source_override_id);

--
-- Name: idx_promoter_member_contacts_promoter_member; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_member_contacts_promoter_member ON app.promoter_member_contacts USING btree (promoter_id, member_id, created_at DESC);

--
-- Name: idx_promoter_payout_accounts_bank; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_payout_accounts_bank ON app.promoter_payout_accounts USING btree (bank_id) WHERE (bank_id IS NOT NULL);

--
-- Name: idx_promoter_payout_accounts_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_payout_accounts_promoter ON app.promoter_payout_accounts USING btree (promoter_id);

--
-- Name: idx_promoter_ranks_parent; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_ranks_parent ON app.promoter_ranks USING btree (parent_rank_id);

--
-- Name: idx_promoter_references_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_references_promoter ON app.promoter_references USING btree (promoter_id);

--
-- Name: idx_promoter_supervisor_assignments_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_supervisor_assignments_promoter ON app.promoter_supervisor_assignments USING btree (promoter_id, created_at DESC);

--
-- Name: idx_promoter_types_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoter_types_is_active ON app.promoter_types USING btree (is_active) WHERE is_active;

--
-- Name: idx_promoters_promoter_type_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoters_promoter_type_id ON app.promoters USING btree (promoter_type_id) WHERE (promoter_type_id IS NOT NULL);

--
-- Name: idx_promoters_rank; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoters_rank ON app.promoters USING btree (rank_id);

--
-- Name: idx_promoters_real_leaderboard; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoters_real_leaderboard ON app.promoters USING btree (total_commission_paid DESC) WHERE ((is_active = true) AND (is_system = false));

--
-- Name: idx_promoters_supervisor; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promoters_supervisor ON app.promoters USING btree (supervisor_id);

--
-- Name: idx_promotions_campaign; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_promotions_campaign ON app.promotions USING btree (campaign_id) WHERE is_active;

--
-- Name: idx_referrals_code; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_referrals_code ON app.referrals USING btree (referral_code);

--
-- Name: idx_referrals_pending_expiration; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_referrals_pending_expiration ON app.referrals USING btree (expires_at) WHERE (((status)::text = 'PENDING_ENROLLMENT'::text) AND (expires_at IS NOT NULL));

--
-- Name: idx_referrals_referred; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_referrals_referred ON app.referrals USING btree (referred_member_id) WHERE (referred_member_id IS NOT NULL);

--
-- Name: idx_referrals_referrer_created; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_referrals_referrer_created ON app.referrals USING btree (referrer_member_id, created_at DESC) WHERE is_active;

--
-- Name: idx_report_audit_actor; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_actor ON app.report_audit_log USING btree (actor_id);

--
-- Name: idx_report_audit_entity; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_entity ON app.report_audit_log USING btree (entity_key, entity_identifier);

--
-- Name: idx_report_audit_entity_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_entity_id ON app.report_audit_log USING btree (entity_key, entity_id);

--
-- Name: idx_report_audit_file; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_file ON app.report_audit_log USING btree (attached_file_id);

--
-- Name: idx_report_audit_generated_at; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_generated_at ON app.report_audit_log USING btree (generated_at DESC);

--
-- Name: idx_report_audit_login_audit; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_audit_login_audit ON app.report_audit_log USING btree (login_audit_log_id);

--
-- Name: idx_report_share_expires; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_share_expires ON app.report_share USING btree (expires_at);

--
-- Name: idx_report_share_report; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_share_report ON app.report_share USING btree (report_audit_log_id);

--
-- Name: idx_report_share_token; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_report_share_token ON app.report_share USING btree (share_token);

--
-- Name: idx_role_permissions_permission_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_role_permissions_permission_id ON app.role_permissions USING btree (permission_id);

--
-- Name: idx_role_permissions_role_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_role_permissions_role_id ON app.role_permissions USING btree (role_id);

--
-- Name: idx_roles_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_roles_is_active ON app.roles USING btree (is_active) WHERE is_active;

--
-- Name: idx_scheduled_job_runs_job_started; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_scheduled_job_runs_job_started ON app.scheduled_job_runs USING btree (scheduled_job_id, started_at DESC);

--
-- Name: idx_scheduled_job_runs_running; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_scheduled_job_runs_running ON app.scheduled_job_runs USING btree (scheduled_job_id) WHERE ((outcome)::text = 'RUNNING'::text);

--
-- Name: idx_scheduled_jobs_enabled; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_scheduled_jobs_enabled ON app.scheduled_jobs USING btree (enabled) WHERE (is_active AND enabled);

--
-- Name: idx_states_country_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_states_country_id ON app.states USING btree (country_id);

--
-- Name: idx_states_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_states_is_active ON app.states USING btree (is_active) WHERE is_active;

--
-- Name: idx_subsidies_member_window; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_subsidies_member_window ON app.subsidies USING btree (member_id, valid_from, valid_until) WHERE is_active;

--
-- Name: idx_subsidy_audit_log_subsidy; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_subsidy_audit_log_subsidy ON app.subsidy_audit_log USING btree (subsidy_id, created_at DESC);

--
-- Name: idx_subsidy_beneficiaries_beneficiary; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_subsidy_beneficiaries_beneficiary ON app.subsidy_beneficiaries USING btree (beneficiary_id) WHERE is_active;

--
-- Name: idx_subsidy_beneficiaries_subsidy; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_subsidy_beneficiaries_subsidy ON app.subsidy_beneficiaries USING btree (subsidy_id);

--
-- Name: idx_terms_acceptances_ally_agreement; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_terms_acceptances_ally_agreement ON app.terms_acceptances USING btree (ally_agreement_id) WHERE (ally_agreement_id IS NOT NULL);

--
-- Name: idx_terms_acceptances_membership; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_terms_acceptances_membership ON app.terms_acceptances USING btree (membership_id) WHERE (membership_id IS NOT NULL);

--
-- Name: idx_terms_acceptances_user; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_terms_acceptances_user ON app.terms_acceptances USING btree (user_id);

--
-- Name: idx_terms_versions_scope_valid_from; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_terms_versions_scope_valid_from ON app.terms_versions USING btree (term_type, ally_type_id, segment, ally_id, valid_from DESC);

--
-- Name: idx_terms_versions_type_valid_from; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_terms_versions_type_valid_from ON app.terms_versions USING btree (term_type, valid_from DESC);

--
-- Name: idx_user_password_history_user_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_user_password_history_user_id ON app.user_password_history USING btree (user_id, created_at DESC);

--
-- Name: idx_user_roles_role_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_user_roles_role_id ON app.user_roles USING btree (role_id);

--
-- Name: idx_user_roles_user_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_user_roles_user_id ON app.user_roles USING btree (user_id);

--
-- Name: idx_user_sessions_log_jti; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_user_sessions_log_jti ON app.user_sessions_log USING btree (jti);

--
-- Name: idx_user_sessions_log_user_id; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_user_sessions_log_user_id ON app.user_sessions_log USING btree (user_id);

--
-- Name: idx_users_default_role; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_users_default_role ON app.users USING btree (default_role_id);

--
-- Name: idx_users_is_active; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_users_is_active ON app.users USING btree (is_active) WHERE is_active;

--
-- Name: idx_users_locked_until; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_users_locked_until ON app.users USING btree (locked_until) WHERE (locked_until IS NOT NULL);

--
-- Name: idx_users_login_token_hash; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_users_login_token_hash ON app.users USING btree (login_token_hash) WHERE (login_token_hash IS NOT NULL);

--
-- Name: idx_users_status; Type: INDEX; Schema: app; Owner: -
--

CREATE INDEX idx_users_status ON app.users USING btree (status);

--
-- Name: uniq_allies_tax_document; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_allies_tax_document ON app.allies USING btree (tax_document_type, tax_document_number) WHERE ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL));

--
-- Name: uniq_ally_patient_links_active; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_ally_patient_links_active ON app.ally_patient_links USING btree (doctor_person_id, member_id) WHERE ((status)::text = 'ACTIVE'::text);

--
-- Name: uniq_ally_users_one_primary_per_ally; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_ally_users_one_primary_per_ally ON app.ally_users USING btree (ally_id) WHERE ((is_primary = true) AND (is_active = true));

--
-- Name: uniq_commissions_payment_promoter_active; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_commissions_payment_promoter_active ON app.commissions USING btree (payment_id, promoter_id) WHERE ((status)::text <> 'VOIDED'::text);

--
-- Name: uniq_members_referral_code; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_members_referral_code ON app.members USING btree (referral_code) WHERE (referral_code IS NOT NULL);

--
-- Name: uniq_membership_charges_membership_period; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_membership_charges_membership_period ON app.membership_charges USING btree (membership_id, period_start);

--
-- Name: uniq_memberships_one_active_per_member; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_memberships_one_active_per_member ON app.memberships USING btree (member_id) WHERE (is_active = true);

--
-- Name: uniq_person_professional_licenses_body_number; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_person_professional_licenses_body_number ON app.person_professional_licenses USING btree (licensing_body_id, license_number) WHERE is_active;

--
-- Name: uniq_persons_tax_document; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_persons_tax_document ON app.persons USING btree (tax_document_type, tax_document_number) WHERE ((tax_document_type IS NOT NULL) AND (tax_document_number IS NOT NULL));

--
-- Name: uniq_promoters_system_row; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_promoters_system_row ON app.promoters USING btree ((true)) WHERE (is_system = true);

--
-- Name: uniq_promoters_user_id; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_promoters_user_id ON app.promoters USING btree (user_id) WHERE ((user_id IS NOT NULL) AND (is_active = true));

--
-- Name: uniq_referrals_referrer_referred_active; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uniq_referrals_referrer_referred_active ON app.referrals USING btree (referrer_member_id, referred_member_id) WHERE ((referred_member_id IS NOT NULL) AND ((status)::text <> ALL ((ARRAY['EXPIRED'::character varying, 'VOIDED'::character varying])::text[])));

--
-- Name: uq_affiliation_requests_open_user; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_affiliation_requests_open_user ON app.affiliation_requests USING btree (user_id) WHERE ((user_id IS NOT NULL) AND ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying])::text[])));

--
-- Name: uq_affiliation_requests_pending_document; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_affiliation_requests_pending_document ON app.affiliation_requests USING btree (document_type, document_number) WHERE ((status)::text = 'PENDING'::text);

--
-- Name: uq_affiliation_requests_pending_email; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_affiliation_requests_pending_email ON app.affiliation_requests USING btree (lower((email)::text)) WHERE ((status)::text = 'PENDING'::text);

--
-- Name: uq_allies_referral_code; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_allies_referral_code ON app.allies USING btree (referral_code) WHERE (referral_code IS NOT NULL);

--
-- Name: uq_beneficiaries_user; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_beneficiaries_user ON app.beneficiaries USING btree (user_id) WHERE (user_id IS NOT NULL);

--
-- Name: uq_cca_rule_period_position; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_cca_rule_period_position ON app.competitive_commission_awards USING btree (competitive_commission_rule_id, period_start, award_position) WHERE (is_active AND ((status)::text <> 'VOIDED'::text) AND (tie_group_size = 1));

--
-- Name: uq_cca_rule_period_promoter; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_cca_rule_period_promoter ON app.competitive_commission_awards USING btree (competitive_commission_rule_id, period_start, promoter_id) WHERE (is_active AND ((status)::text <> 'VOIDED'::text));

--
-- Name: uq_ccr_group_priority; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_ccr_group_priority ON app.competitive_commission_rules USING btree (competition_group, group_priority) WHERE (is_active AND (competition_group IS NOT NULL));

--
-- Name: uq_ccrp_rule_from; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_ccrp_rule_from ON app.competitive_commission_rule_positions USING btree (competitive_commission_rule_id, position_from);

--
-- Name: uq_cct_open; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_cct_open ON app.competitive_commission_ties USING btree (competitive_commission_rule_id, period_start, position_from) WHERE (is_active AND ((status)::text = ANY ((ARRAY['OPEN'::character varying, 'STALE'::character varying])::text[])));

--
-- Name: uq_company_bank_accounts_number; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_company_bank_accounts_number ON app.company_bank_accounts USING btree (bank_id, account_number) WHERE is_active;

--
-- Name: uq_exchange_rates_pair_operation_date; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_exchange_rates_pair_operation_date ON app.exchange_rates USING btree (base_currency_id, quote_currency_id, operation_date);

--
-- Name: uq_media_items_one_primary; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_media_items_one_primary ON app.media_items USING btree (owner_table, owner_uuid) WHERE (is_primary AND is_active);

--
-- Name: uq_membership_promotions_one_active; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_membership_promotions_one_active ON app.membership_promotions USING btree (membership_id) WHERE (((status)::text = 'ACTIVE'::text) AND is_active);

--
-- Name: uq_memberships_contract_number; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_memberships_contract_number ON app.memberships USING btree (contract_number);

--
-- Name: uq_payer_bank_accounts_identity; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_payer_bank_accounts_identity ON app.payer_bank_accounts USING btree (person_id, COALESCE(bank_id, (0)::bigint), COALESCE(account_identifier, phone, email, ''::character varying)) WHERE is_active;

--
-- Name: uq_payment_credit_claims_one_open; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_payment_credit_claims_one_open ON app.payment_credit_claims USING btree (payment_id) WHERE ((claim_status)::text = 'OPEN'::text);

--
-- Name: uq_promoter_bonus_awards_cut; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_promoter_bonus_awards_cut ON app.promoter_bonus_awards USING btree (bonus_rule_id, promoter_id, window_start, cut_kind, cut_sequence);

--
-- Name: uq_promoter_payout_accounts_default; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_promoter_payout_accounts_default ON app.promoter_payout_accounts USING btree (promoter_id) WHERE (is_default AND is_active);

--
-- Name: uq_terms_acceptances_user_version; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_terms_acceptances_user_version ON app.terms_acceptances USING btree (user_id, terms_versions_id) WHERE (ally_agreement_id IS NULL);

--
-- Name: uq_terms_acceptances_user_version_agreement; Type: INDEX; Schema: app; Owner: -
--

CREATE UNIQUE INDEX uq_terms_acceptances_user_version_agreement ON app.terms_acceptances USING btree (user_id, terms_versions_id, ally_agreement_id) WHERE (ally_agreement_id IS NOT NULL);

--
-- Name: affiliation_requests trg_affiliation_requests_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_affiliation_requests_updated_at BEFORE UPDATE ON app.affiliation_requests FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: allies trg_allies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_allies_updated_at BEFORE UPDATE ON app.allies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_agreements trg_ally_agreements_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_agreements_updated_at BEFORE UPDATE ON app.ally_agreements FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_opening_hours trg_ally_opening_hours_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_opening_hours_updated_at BEFORE UPDATE ON app.ally_opening_hours FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_patient_links trg_ally_patient_links_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_patient_links_updated_at BEFORE UPDATE ON app.ally_patient_links FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_products trg_ally_products_exclusive_owner; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_products_exclusive_owner BEFORE INSERT OR UPDATE OF ally_id, product_id ON app.ally_products FOR EACH ROW EXECUTE FUNCTION app.enforce_exclusive_product_owner();

--
-- Name: ally_products trg_ally_products_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_products_updated_at BEFORE UPDATE ON app.ally_products FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_types trg_ally_types_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_types_updated_at BEFORE UPDATE ON app.ally_types FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: ally_users trg_ally_users_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_ally_users_updated_at BEFORE UPDATE ON app.ally_users FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: attached_files trg_attached_files_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_attached_files_updated_at BEFORE UPDATE ON app.attached_files FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: entity_config trg_audit_entity_config_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_audit_entity_config_updated_at BEFORE UPDATE ON app.entity_config FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: banks trg_banks_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_banks_updated_at BEFORE UPDATE ON app.banks FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: beneficiaries trg_beneficiaries_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_beneficiaries_updated_at BEFORE UPDATE ON app.beneficiaries FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: benefit_usages trg_benefit_usages_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_benefit_usages_updated_at BEFORE UPDATE ON app.benefit_usages FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: campaign_promoters trg_campaign_promoters_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_campaign_promoters_updated_at BEFORE UPDATE ON app.campaign_promoters FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: campaign_transaction_exceptions trg_campaign_transaction_exceptions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_campaign_transaction_exceptions_updated_at BEFORE UPDATE ON app.campaign_transaction_exceptions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: campaign_transaction_links trg_campaign_transaction_links_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_campaign_transaction_links_updated_at BEFORE UPDATE ON app.campaign_transaction_links FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: campaigns trg_campaigns_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_campaigns_updated_at BEFORE UPDATE ON app.campaigns FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: cities trg_cities_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_cities_updated_at BEFORE UPDATE ON app.cities FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: collection_commission_tiers trg_collection_commission_tiers_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_collection_commission_tiers_updated_at BEFORE UPDATE ON app.collection_commission_tiers FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: commission_bonus_rules trg_commission_bonus_rules_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_commission_bonus_rules_updated_at BEFORE UPDATE ON app.commission_bonus_rules FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: commission_retroactive_topup_cuts trg_commission_retroactive_topup_cuts_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_commission_retroactive_topup_cuts_updated_at BEFORE UPDATE ON app.commission_retroactive_topup_cuts FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: commission_retroactive_topups trg_commission_retroactive_topups_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_commission_retroactive_topups_updated_at BEFORE UPDATE ON app.commission_retroactive_topups FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: commission_tiers trg_commission_tiers_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_commission_tiers_updated_at BEFORE UPDATE ON app.commission_tiers FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: commissions trg_commissions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_commissions_updated_at BEFORE UPDATE ON app.commissions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: company_bank_accounts trg_company_bank_accounts_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_company_bank_accounts_updated_at BEFORE UPDATE ON app.company_bank_accounts FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_award_settlements trg_competitive_commission_award_settlements_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_award_settlements_updated_at BEFORE UPDATE ON app.competitive_commission_award_settlements FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_awards trg_competitive_commission_awards_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_awards_updated_at BEFORE UPDATE ON app.competitive_commission_awards FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_manual_decisions trg_competitive_commission_manual_decisions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_manual_decisions_updated_at BEFORE UPDATE ON app.competitive_commission_manual_decisions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_rule_positions trg_competitive_commission_rule_positions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_rule_positions_updated_at BEFORE UPDATE ON app.competitive_commission_rule_positions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_rules trg_competitive_commission_rules_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_rules_updated_at BEFORE UPDATE ON app.competitive_commission_rules FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: competitive_commission_ties trg_competitive_commission_ties_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_competitive_commission_ties_updated_at BEFORE UPDATE ON app.competitive_commission_ties FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: contact_messages trg_contact_messages_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_contact_messages_updated_at BEFORE UPDATE ON app.contact_messages FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: corporate_contracts trg_corporate_contracts_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_corporate_contracts_updated_at BEFORE UPDATE ON app.corporate_contracts FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: countries trg_countries_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_countries_updated_at BEFORE UPDATE ON app.countries FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: currencies trg_currencies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_currencies_updated_at BEFORE UPDATE ON app.currencies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: document_types trg_document_types_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_document_types_updated_at BEFORE UPDATE ON app.document_types FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: exchange_rates trg_exchange_rates_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_exchange_rates_updated_at BEFORE UPDATE ON app.exchange_rates FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: file_type_policies trg_file_type_policies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_file_type_policies_updated_at BEFORE UPDATE ON app.file_type_policies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: genders trg_genders_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_genders_updated_at BEFORE UPDATE ON app.genders FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: hierarchy_override_tiers trg_hierarchy_override_tiers_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_hierarchy_override_tiers_updated_at BEFORE UPDATE ON app.hierarchy_override_tiers FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: holidays trg_holidays_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_holidays_updated_at BEFORE UPDATE ON app.holidays FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: marital_statuses trg_marital_statuses_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_marital_statuses_updated_at BEFORE UPDATE ON app.marital_statuses FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: media_items trg_media_items_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_media_items_updated_at BEFORE UPDATE ON app.media_items FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: medical_records trg_medical_records_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_medical_records_updated_at BEFORE UPDATE ON app.medical_records FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: member_documents trg_member_documents_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_member_documents_updated_at BEFORE UPDATE ON app.member_documents FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: members trg_members_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_members_updated_at BEFORE UPDATE ON app.members FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: membership_charges trg_membership_charges_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_membership_charges_updated_at BEFORE UPDATE ON app.membership_charges FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: membership_promotions trg_membership_promotions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_membership_promotions_updated_at BEFORE UPDATE ON app.membership_promotions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: memberships trg_memberships_contract_number; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_memberships_contract_number BEFORE INSERT ON app.memberships FOR EACH ROW EXECUTE FUNCTION app.set_membership_contract_number();

--
-- Name: memberships trg_memberships_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_memberships_updated_at BEFORE UPDATE ON app.memberships FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: notifications trg_notifications_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_notifications_updated_at BEFORE UPDATE ON app.notifications FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: occupations trg_occupations_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_occupations_updated_at BEFORE UPDATE ON app.occupations FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: onboarding_policies trg_onboarding_policies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_onboarding_policies_updated_at BEFORE UPDATE ON app.onboarding_policies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: organizations trg_organizations_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON app.organizations FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payer_bank_accounts trg_payer_bank_accounts_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payer_bank_accounts_updated_at BEFORE UPDATE ON app.payer_bank_accounts FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payment_categories trg_payment_categories_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payment_categories_updated_at BEFORE UPDATE ON app.payment_categories FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payment_credit_claims trg_payment_credit_claims_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payment_credit_claims_updated_at BEFORE UPDATE ON app.payment_credit_claims FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payment_lines trg_payment_lines_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payment_lines_updated_at BEFORE UPDATE ON app.payment_lines FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payment_methods trg_payment_methods_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payment_methods_updated_at BEFORE UPDATE ON app.payment_methods FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: payments trg_payments_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON app.payments FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: permission_domains trg_permission_domains_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_permission_domains_updated_at BEFORE UPDATE ON app.permission_domains FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: permissions trg_permissions_grant_system; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_permissions_grant_system AFTER INSERT ON app.permissions FOR EACH ROW EXECUTE FUNCTION app.grant_permission_to_system();

--
-- Name: permissions trg_permissions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_permissions_updated_at BEFORE UPDATE ON app.permissions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: person_credit_entries trg_person_credit_entries_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_person_credit_entries_updated_at BEFORE UPDATE ON app.person_credit_entries FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: persons trg_persons_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_persons_updated_at BEFORE UPDATE ON app.persons FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: plans trg_plans_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_plans_updated_at BEFORE UPDATE ON app.plans FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: product_categories trg_product_categories_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_product_categories_updated_at BEFORE UPDATE ON app.product_categories FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: products trg_products_exclusive_owner; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_products_exclusive_owner BEFORE UPDATE OF owner_ally_id ON app.products FOR EACH ROW EXECUTE FUNCTION app.enforce_exclusive_product_owner();

--
-- Name: products trg_products_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON app.products FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: professions trg_professions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_professions_updated_at BEFORE UPDATE ON app.professions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_bonus_awards trg_promoter_bonus_awards_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_bonus_awards_updated_at BEFORE UPDATE ON app.promoter_bonus_awards FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_hierarchy_overrides trg_promoter_hierarchy_overrides_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_hierarchy_overrides_updated_at BEFORE UPDATE ON app.promoter_hierarchy_overrides FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_payout_accounts trg_promoter_payout_accounts_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_payout_accounts_updated_at BEFORE UPDATE ON app.promoter_payout_accounts FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_ranks trg_promoter_ranks_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_ranks_updated_at BEFORE UPDATE ON app.promoter_ranks FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_references trg_promoter_references_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_references_updated_at BEFORE UPDATE ON app.promoter_references FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoter_types trg_promoter_types_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoter_types_updated_at BEFORE UPDATE ON app.promoter_types FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promoters trg_promoters_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promoters_updated_at BEFORE UPDATE ON app.promoters FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: promotions trg_promotions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_promotions_updated_at BEFORE UPDATE ON app.promotions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: referrals trg_referrals_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_referrals_updated_at BEFORE UPDATE ON app.referrals FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: role_permissions trg_role_permissions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_role_permissions_updated_at BEFORE UPDATE ON app.role_permissions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: roles trg_roles_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON app.roles FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: scheduled_job_runs trg_scheduled_job_runs_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_scheduled_job_runs_updated_at BEFORE UPDATE ON app.scheduled_job_runs FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: scheduled_jobs trg_scheduled_jobs_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_scheduled_jobs_updated_at BEFORE UPDATE ON app.scheduled_jobs FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: security_policies trg_security_policies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_security_policies_updated_at BEFORE UPDATE ON app.security_policies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: states trg_states_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_states_updated_at BEFORE UPDATE ON app.states FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: subsidies trg_subsidies_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_subsidies_updated_at BEFORE UPDATE ON app.subsidies FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: subsidy_audit_log trg_subsidy_audit_log_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_subsidy_audit_log_updated_at BEFORE UPDATE ON app.subsidy_audit_log FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: subsidy_beneficiaries trg_subsidy_beneficiaries_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_subsidy_beneficiaries_updated_at BEFORE UPDATE ON app.subsidy_beneficiaries FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: system_configs trg_system_configs_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_system_configs_updated_at BEFORE UPDATE ON app.system_configs FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: terms_acceptances trg_terms_acceptances_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_terms_acceptances_updated_at BEFORE UPDATE ON app.terms_acceptances FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: terms_versions trg_terms_versions_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_terms_versions_updated_at BEFORE UPDATE ON app.terms_versions FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: user_roles trg_user_roles_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_user_roles_updated_at BEFORE UPDATE ON app.user_roles FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: users trg_users_updated_at; Type: TRIGGER; Schema: app; Owner: -
--

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON app.users FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

--
-- Name: affiliation_request_logs affiliation_request_logs_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_request_logs
    ADD CONSTRAINT affiliation_request_logs_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES app.users(users_id);

--
-- Name: affiliation_request_logs affiliation_request_logs_affiliation_request_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_request_logs
    ADD CONSTRAINT affiliation_request_logs_affiliation_request_id_fkey FOREIGN KEY (affiliation_request_id) REFERENCES app.affiliation_requests(affiliation_requests_id) ON DELETE CASCADE;

--
-- Name: affiliation_requests affiliation_requests_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: affiliation_requests affiliation_requests_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: affiliation_requests affiliation_requests_payment_bank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_payment_bank_id_fkey FOREIGN KEY (payment_bank_id) REFERENCES app.banks(banks_id);

--
-- Name: affiliation_requests affiliation_requests_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: affiliation_requests affiliation_requests_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES app.payment_methods(payment_methods_id);

--
-- Name: affiliation_requests affiliation_requests_plan_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES app.plans(plans_id);

--
-- Name: affiliation_requests affiliation_requests_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: affiliation_requests affiliation_requests_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES app.users(users_id);

--
-- Name: affiliation_requests affiliation_requests_terms_versions_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_terms_versions_id_fkey FOREIGN KEY (terms_versions_id) REFERENCES app.terms_versions(terms_versions_id);

--
-- Name: affiliation_requests affiliation_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.affiliation_requests
    ADD CONSTRAINT affiliation_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: allies allies_captured_by_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_captured_by_promoter_id_fkey FOREIGN KEY (captured_by_promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: allies allies_city_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_city_id_fkey FOREIGN KEY (city_id) REFERENCES app.cities(cities_id);

--
-- Name: allies allies_host_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_host_ally_id_fkey FOREIGN KEY (host_ally_id) REFERENCES app.allies(allies_id);

--
-- Name: allies allies_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.allies
    ADD CONSTRAINT allies_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: ally_agreements ally_agreements_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_agreements
    ADD CONSTRAINT ally_agreements_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id);

--
-- Name: ally_agreements ally_agreements_terms_version_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_agreements
    ADD CONSTRAINT ally_agreements_terms_version_id_fkey FOREIGN KEY (terms_version_id) REFERENCES app.terms_versions(terms_versions_id);

--
-- Name: ally_ally_types ally_ally_types_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_ally_types
    ADD CONSTRAINT ally_ally_types_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: ally_ally_types ally_ally_types_ally_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_ally_types
    ADD CONSTRAINT ally_ally_types_ally_type_id_fkey FOREIGN KEY (ally_type_id) REFERENCES app.ally_types(ally_types_id);

--
-- Name: ally_opening_hours ally_opening_hours_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_opening_hours
    ADD CONSTRAINT ally_opening_hours_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: ally_patient_links ally_patient_links_created_at_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_created_at_ally_id_fkey FOREIGN KEY (created_at_ally_id) REFERENCES app.allies(allies_id);

--
-- Name: ally_patient_links ally_patient_links_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES app.users(users_id);

--
-- Name: ally_patient_links ally_patient_links_doctor_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_doctor_person_id_fkey FOREIGN KEY (doctor_person_id) REFERENCES app.persons(persons_id);

--
-- Name: ally_patient_links ally_patient_links_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_patient_links
    ADD CONSTRAINT ally_patient_links_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: ally_product_review_log ally_product_review_log_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_product_review_log
    ADD CONSTRAINT ally_product_review_log_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES app.users(users_id);

--
-- Name: ally_product_review_log ally_product_review_log_ally_product_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_product_review_log
    ADD CONSTRAINT ally_product_review_log_ally_product_id_fkey FOREIGN KEY (ally_product_id) REFERENCES app.ally_products(ally_products_id) ON DELETE CASCADE;

--
-- Name: ally_products ally_products_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: ally_products ally_products_price_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_price_currency_id_fkey FOREIGN KEY (price_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: ally_products ally_products_product_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_product_id_fkey FOREIGN KEY (product_id) REFERENCES app.products(products_id);

--
-- Name: ally_products ally_products_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_products
    ADD CONSTRAINT ally_products_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES app.users(users_id);

--
-- Name: ally_professions ally_specialties_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_professions
    ADD CONSTRAINT ally_specialties_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: ally_professions ally_specialties_medical_specialty_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_professions
    ADD CONSTRAINT ally_specialties_medical_specialty_id_fkey FOREIGN KEY (profession_id) REFERENCES app.professions(professions_id);

--
-- Name: ally_users ally_users_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_users
    ADD CONSTRAINT ally_users_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: ally_users ally_users_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.ally_users
    ADD CONSTRAINT ally_users_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: attached_files attached_files_published_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.attached_files
    ADD CONSTRAINT attached_files_published_by_fkey FOREIGN KEY (published_by) REFERENCES app.users(users_id);

--
-- Name: attached_files attached_files_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.attached_files
    ADD CONSTRAINT attached_files_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES app.users(users_id);

--
-- Name: beneficiaries beneficiaries_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id) ON DELETE CASCADE;

--
-- Name: beneficiaries beneficiaries_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: beneficiaries beneficiaries_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT beneficiaries_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: benefit_usages benefit_usages_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id);

--
-- Name: benefit_usages benefit_usages_ally_product_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_ally_product_id_fkey FOREIGN KEY (ally_product_id) REFERENCES app.ally_products(ally_products_id);

--
-- Name: benefit_usages benefit_usages_ally_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_ally_user_id_fkey FOREIGN KEY (ally_user_id) REFERENCES app.ally_users(ally_users_id);

--
-- Name: benefit_usages benefit_usages_beneficiary_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_beneficiary_id_fkey FOREIGN KEY (beneficiary_id) REFERENCES app.beneficiaries(beneficiaries_id);

--
-- Name: benefit_usages benefit_usages_consumption_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_consumption_currency_id_fkey FOREIGN KEY (consumption_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: benefit_usages benefit_usages_converted_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_converted_currency_id_fkey FOREIGN KEY (converted_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: benefit_usages benefit_usages_copay_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_copay_currency_id_fkey FOREIGN KEY (copay_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: benefit_usages benefit_usages_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: benefit_usages benefit_usages_product_category_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.benefit_usages
    ADD CONSTRAINT benefit_usages_product_category_id_fkey FOREIGN KEY (product_category_id) REFERENCES app.product_categories(product_categories_id);

--
-- Name: campaign_promoters campaign_promoters_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_promoters
    ADD CONSTRAINT campaign_promoters_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: campaign_promoters campaign_promoters_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_promoters
    ADD CONSTRAINT campaign_promoters_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: campaign_transaction_exceptions campaign_transaction_exceptions_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_exceptions
    ADD CONSTRAINT campaign_transaction_exceptions_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: campaign_transaction_exceptions campaign_transaction_exceptions_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_exceptions
    ADD CONSTRAINT campaign_transaction_exceptions_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: campaign_transaction_exceptions campaign_transaction_exceptions_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_exceptions
    ADD CONSTRAINT campaign_transaction_exceptions_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: campaign_transaction_links campaign_transaction_links_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_links
    ADD CONSTRAINT campaign_transaction_links_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: campaign_transaction_links campaign_transaction_links_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_links
    ADD CONSTRAINT campaign_transaction_links_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: campaign_transaction_links campaign_transaction_links_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaign_transaction_links
    ADD CONSTRAINT campaign_transaction_links_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: campaigns campaigns_target_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.campaigns
    ADD CONSTRAINT campaigns_target_amount_currency_id_fkey FOREIGN KEY (target_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: cities cities_state_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.cities
    ADD CONSTRAINT cities_state_id_fkey FOREIGN KEY (state_id) REFERENCES app.states(states_id);

--
-- Name: collection_commission_tier_promoter_types collection_commission_tier_pr_collection_commission_tier_i_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tier_promoter_types
    ADD CONSTRAINT collection_commission_tier_pr_collection_commission_tier_i_fkey FOREIGN KEY (collection_commission_tier_id) REFERENCES app.collection_commission_tiers(collection_commission_tiers_id) ON DELETE CASCADE;

--
-- Name: collection_commission_tier_promoter_types collection_commission_tier_promoter_types_promoter_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tier_promoter_types
    ADD CONSTRAINT collection_commission_tier_promoter_types_promoter_type_id_fkey FOREIGN KEY (promoter_type_id) REFERENCES app.promoter_types(promoter_types_id);

--
-- Name: collection_commission_tiers collection_commission_tiers_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tiers
    ADD CONSTRAINT collection_commission_tiers_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: collection_commission_tiers collection_commission_tiers_flat_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tiers
    ADD CONSTRAINT collection_commission_tiers_flat_amount_currency_id_fkey FOREIGN KEY (flat_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: collection_commission_tiers collection_commission_tiers_min_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.collection_commission_tiers
    ADD CONSTRAINT collection_commission_tiers_min_amount_currency_id_fkey FOREIGN KEY (min_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_bonus_rule_promoter_types commission_bonus_rule_promoter_ty_commission_bonus_rule_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rule_promoter_types
    ADD CONSTRAINT commission_bonus_rule_promoter_ty_commission_bonus_rule_id_fkey FOREIGN KEY (commission_bonus_rule_id) REFERENCES app.commission_bonus_rules(commission_bonus_rules_id) ON DELETE CASCADE;

--
-- Name: commission_bonus_rule_promoter_types commission_bonus_rule_promoter_types_promoter_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rule_promoter_types
    ADD CONSTRAINT commission_bonus_rule_promoter_types_promoter_type_id_fkey FOREIGN KEY (promoter_type_id) REFERENCES app.promoter_types(promoter_types_id);

--
-- Name: commission_bonus_rules commission_bonus_rules_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: commission_bonus_rules commission_bonus_rules_reward_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_reward_currency_id_fkey FOREIGN KEY (reward_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_bonus_rules commission_bonus_rules_threshold_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_threshold_currency_id_fkey FOREIGN KEY (threshold_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_retroactive_topup_cuts commission_retroactive_topup_cuts_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT commission_retroactive_topup_cuts_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_retroactive_topup_cuts commission_retroactive_topup_cuts_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT commission_retroactive_topup_cuts_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: commission_retroactive_topup_cuts commission_retroactive_topup_cuts_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topup_cuts
    ADD CONSTRAINT commission_retroactive_topup_cuts_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: commission_retroactive_topups commission_retroactive_topups_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT commission_retroactive_topups_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_retroactive_topups commission_retroactive_topups_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT commission_retroactive_topups_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: commission_retroactive_topups commission_retroactive_topups_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_retroactive_topups
    ADD CONSTRAINT commission_retroactive_topups_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: commission_tier_promoter_types commission_tier_promoter_types_commission_tier_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tier_promoter_types
    ADD CONSTRAINT commission_tier_promoter_types_commission_tier_id_fkey FOREIGN KEY (commission_tier_id) REFERENCES app.commission_tiers(commission_tiers_id) ON DELETE CASCADE;

--
-- Name: commission_tier_promoter_types commission_tier_promoter_types_promoter_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tier_promoter_types
    ADD CONSTRAINT commission_tier_promoter_types_promoter_type_id_fkey FOREIGN KEY (promoter_type_id) REFERENCES app.promoter_types(promoter_types_id);

--
-- Name: commission_tiers commission_tiers_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tiers
    ADD CONSTRAINT commission_tiers_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: commission_tiers commission_tiers_flat_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tiers
    ADD CONSTRAINT commission_tiers_flat_amount_currency_id_fkey FOREIGN KEY (flat_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commission_tiers commission_tiers_threshold_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commission_tiers
    ADD CONSTRAINT commission_tiers_threshold_amount_currency_id_fkey FOREIGN KEY (threshold_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commissions commissions_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES app.users(users_id);

--
-- Name: commissions commissions_collection_tier_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_collection_tier_id_fkey FOREIGN KEY (collection_tier_id) REFERENCES app.collection_commission_tiers(collection_commission_tiers_id);

--
-- Name: commissions commissions_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: commissions commissions_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: commissions commissions_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: commissions commissions_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: commissions commissions_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT commissions_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: company_bank_account_payment_methods company_bank_account_payment_metho_company_bank_account_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_account_payment_methods
    ADD CONSTRAINT company_bank_account_payment_metho_company_bank_account_id_fkey FOREIGN KEY (company_bank_account_id) REFERENCES app.company_bank_accounts(company_bank_accounts_id) ON DELETE CASCADE;

--
-- Name: company_bank_account_payment_methods company_bank_account_payment_methods_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_account_payment_methods
    ADD CONSTRAINT company_bank_account_payment_methods_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES app.payment_methods(payment_methods_id);

--
-- Name: company_bank_accounts company_bank_accounts_bank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES app.banks(banks_id);

--
-- Name: company_bank_accounts company_bank_accounts_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.company_bank_accounts
    ADD CONSTRAINT company_bank_accounts_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award__competitive_commission_award_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award__competitive_commission_award_fkey FOREIGN KEY (competitive_commission_award_id) REFERENCES app.competitive_commission_awards(competitive_commission_awards_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award__competitive_commission_rule__fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award__competitive_commission_rule__fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id);

--
-- Name: competitive_commission_awards competitive_commission_award_competitive_commission_rule__fkey1; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_award_competitive_commission_rule__fkey1 FOREIGN KEY (competitive_commission_rule_position_id) REFERENCES app.competitive_commission_rule_positions(competitive_commission_rule_positions_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award_settlements_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award_settlements_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award_settlements_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award_settlements_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: competitive_commission_award_settlements competitive_commission_award_settlements_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_award_settlements
    ADD CONSTRAINT competitive_commission_award_settlements_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: competitive_commission_awards competitive_commission_awards_competitive_commission_rule__fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_competitive_commission_rule__fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id);

--
-- Name: competitive_commission_awards competitive_commission_awards_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: competitive_commission_awards competitive_commission_awards_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: competitive_commission_awards competitive_commission_awards_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT competitive_commission_awards_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_competitive_commission_rule__fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_competitive_commission_rule__fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_competitive_commission_tie_i_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_competitive_commission_tie_i_fkey FOREIGN KEY (competitive_commission_tie_id) REFERENCES app.competitive_commission_ties(competitive_commission_ties_id);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_decisio_replaced_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_decisio_replaced_promoter_id_fkey FOREIGN KEY (replaced_promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: competitive_commission_manual_decisions competitive_commission_manual_decisions_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_manual_decisions
    ADD CONSTRAINT competitive_commission_manual_decisions_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: competitive_commission_rule_promoter_types competitive_commission_rule__competitive_commission_rule__fkey1; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_promoter_types
    ADD CONSTRAINT competitive_commission_rule__competitive_commission_rule__fkey1 FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE;

--
-- Name: competitive_commission_rule_positions competitive_commission_rule_p_competitive_commission_rule__fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_positions
    ADD CONSTRAINT competitive_commission_rule_p_competitive_commission_rule__fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE;

--
-- Name: competitive_commission_rule_positions competitive_commission_rule_positions_reward_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_positions
    ADD CONSTRAINT competitive_commission_rule_positions_reward_currency_id_fkey FOREIGN KEY (reward_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: competitive_commission_rule_promoter_types competitive_commission_rule_promoter_type_promoter_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_promoter_types
    ADD CONSTRAINT competitive_commission_rule_promoter_type_promoter_type_id_fkey FOREIGN KEY (promoter_type_id) REFERENCES app.promoter_types(promoter_types_id);

--
-- Name: competitive_commission_rule_ranks competitive_commission_rule_r_competitive_commission_rule__fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_ranks
    ADD CONSTRAINT competitive_commission_rule_r_competitive_commission_rule__fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id) ON DELETE CASCADE;

--
-- Name: competitive_commission_rule_ranks competitive_commission_rule_ranks_rank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rule_ranks
    ADD CONSTRAINT competitive_commission_rule_ranks_rank_id_fkey FOREIGN KEY (rank_id) REFERENCES app.promoter_ranks(promoter_ranks_id);

--
-- Name: competitive_commission_rules competitive_commission_rules_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rules
    ADD CONSTRAINT competitive_commission_rules_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: competitive_commission_rules competitive_commission_rules_threshold_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_rules
    ADD CONSTRAINT competitive_commission_rules_threshold_currency_id_fkey FOREIGN KEY (threshold_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: competitive_commission_tie_candidates competitive_commission_tie_ca_competitive_commission_tie_i_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_tie_candidates
    ADD CONSTRAINT competitive_commission_tie_ca_competitive_commission_tie_i_fkey FOREIGN KEY (competitive_commission_tie_id) REFERENCES app.competitive_commission_ties(competitive_commission_ties_id) ON DELETE CASCADE;

--
-- Name: competitive_commission_tie_candidates competitive_commission_tie_candidates_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_tie_candidates
    ADD CONSTRAINT competitive_commission_tie_candidates_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: competitive_commission_ties competitive_commission_ties_competitive_commission_rule_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_ties
    ADD CONSTRAINT competitive_commission_ties_competitive_commission_rule_id_fkey FOREIGN KEY (competitive_commission_rule_id) REFERENCES app.competitive_commission_rules(competitive_commission_rules_id);

--
-- Name: corporate_contracts corporate_contracts_contact_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.corporate_contracts
    ADD CONSTRAINT corporate_contracts_contact_user_id_fkey FOREIGN KEY (contact_user_id) REFERENCES app.users(users_id);

--
-- Name: corporate_contracts corporate_contracts_plan_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.corporate_contracts
    ADD CONSTRAINT corporate_contracts_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES app.plans(plans_id);

--
-- Name: countries countries_official_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.countries
    ADD CONSTRAINT countries_official_currency_id_fkey FOREIGN KEY (official_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: data_change_audit_log data_change_audit_log_actor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.data_change_audit_log
    ADD CONSTRAINT data_change_audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES app.users(users_id);

--
-- Name: data_change_audit_log data_change_audit_log_login_audit_log_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.data_change_audit_log
    ADD CONSTRAINT data_change_audit_log_login_audit_log_id_fkey FOREIGN KEY (login_audit_log_id) REFERENCES app.login_audit_log(login_audit_log_id);

--
-- Name: data_change_audit_log data_change_audit_log_restored_from_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.data_change_audit_log
    ADD CONSTRAINT data_change_audit_log_restored_from_id_fkey FOREIGN KEY (restored_from_id) REFERENCES app.data_change_audit_log(data_change_audit_log_id);

--
-- Name: exchange_rates exchange_rates_base_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.exchange_rates
    ADD CONSTRAINT exchange_rates_base_currency_id_fkey FOREIGN KEY (base_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: exchange_rates exchange_rates_quote_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.exchange_rates
    ADD CONSTRAINT exchange_rates_quote_currency_id_fkey FOREIGN KEY (quote_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: file_download_links file_download_links_created_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.file_download_links
    ADD CONSTRAINT file_download_links_created_by_fkey FOREIGN KEY (created_by) REFERENCES app.users(users_id);

--
-- Name: beneficiaries fk_beneficiaries_inscription_payment; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.beneficiaries
    ADD CONSTRAINT fk_beneficiaries_inscription_payment FOREIGN KEY (inscription_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: competitive_commission_awards fk_cca_manual_decision; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.competitive_commission_awards
    ADD CONSTRAINT fk_cca_manual_decision FOREIGN KEY (manual_decision_id) REFERENCES app.competitive_commission_manual_decisions(competitive_commission_manual_decisions_id);

--
-- Name: commissions fk_commissions_tier; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.commissions
    ADD CONSTRAINT fk_commissions_tier FOREIGN KEY (commission_tier_id) REFERENCES app.commission_tiers(commission_tiers_id);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_flat_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_flat_amount_currency_id_fkey FOREIGN KEY (flat_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_rank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_rank_id_fkey FOREIGN KEY (rank_id) REFERENCES app.promoter_ranks(promoter_ranks_id);

--
-- Name: hierarchy_override_tiers hierarchy_override_tiers_threshold_amount_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.hierarchy_override_tiers
    ADD CONSTRAINT hierarchy_override_tiers_threshold_amount_currency_id_fkey FOREIGN KEY (threshold_amount_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: holidays holidays_city_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.holidays
    ADD CONSTRAINT holidays_city_id_fkey FOREIGN KEY (city_id) REFERENCES app.cities(cities_id);

--
-- Name: holidays holidays_country_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.holidays
    ADD CONSTRAINT holidays_country_id_fkey FOREIGN KEY (country_id) REFERENCES app.countries(countries_id);

--
-- Name: holidays holidays_state_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.holidays
    ADD CONSTRAINT holidays_state_id_fkey FOREIGN KEY (state_id) REFERENCES app.states(states_id);

--
-- Name: login_audit_log login_audit_log_active_role_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.login_audit_log
    ADD CONSTRAINT login_audit_log_active_role_id_fkey FOREIGN KEY (active_role_id) REFERENCES app.roles(roles_id);

--
-- Name: login_audit_log login_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.login_audit_log
    ADD CONSTRAINT login_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: media_items media_items_attached_file_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.media_items
    ADD CONSTRAINT media_items_attached_file_id_fkey FOREIGN KEY (attached_file_id) REFERENCES app.attached_files(attached_files_id) ON DELETE SET NULL;

--
-- Name: medical_record_access_log medical_record_access_log_accessed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_accessed_by_user_id_fkey FOREIGN KEY (accessed_by_user_id) REFERENCES app.users(users_id);

--
-- Name: medical_record_access_log medical_record_access_log_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id);

--
-- Name: medical_record_access_log medical_record_access_log_link_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_link_id_fkey FOREIGN KEY (link_id) REFERENCES app.ally_patient_links(ally_patient_links_id);

--
-- Name: medical_record_access_log medical_record_access_log_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_record_access_log
    ADD CONSTRAINT medical_record_access_log_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: medical_records medical_records_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.medical_records
    ADD CONSTRAINT medical_records_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: member_documents member_documents_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_documents
    ADD CONSTRAINT member_documents_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id) ON DELETE CASCADE;

--
-- Name: member_documents member_documents_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_documents
    ADD CONSTRAINT member_documents_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES app.users(users_id);

--
-- Name: member_promoter_assignments member_promoter_assignments_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES app.users(users_id);

--
-- Name: member_promoter_assignments member_promoter_assignments_from_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_from_promoter_id_fkey FOREIGN KEY (from_promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: member_promoter_assignments member_promoter_assignments_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: member_promoter_assignments member_promoter_assignments_to_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.member_promoter_assignments
    ADD CONSTRAINT member_promoter_assignments_to_promoter_id_fkey FOREIGN KEY (to_promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: members members_corporate_contract_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_corporate_contract_id_fkey FOREIGN KEY (corporate_contract_id) REFERENCES app.corporate_contracts(corporate_contracts_id);

--
-- Name: members members_occupation_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_occupation_id_fkey FOREIGN KEY (occupation_id) REFERENCES app.occupations(occupations_id);

--
-- Name: members members_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: members members_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.members
    ADD CONSTRAINT members_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: membership_charges membership_charges_covered_by_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_covered_by_payment_id_fkey FOREIGN KEY (covered_by_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: membership_charges membership_charges_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: membership_charges membership_charges_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: membership_charges membership_charges_membership_promotion_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_charges
    ADD CONSTRAINT membership_charges_membership_promotion_id_fkey FOREIGN KEY (membership_promotion_id) REFERENCES app.membership_promotions(membership_promotions_id);

--
-- Name: membership_promotions membership_promotions_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES app.users(users_id);

--
-- Name: membership_promotions membership_promotions_code_owner_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_code_owner_ally_id_fkey FOREIGN KEY (code_owner_ally_id) REFERENCES app.allies(allies_id);

--
-- Name: membership_promotions membership_promotions_code_owner_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_code_owner_member_id_fkey FOREIGN KEY (code_owner_member_id) REFERENCES app.members(members_id);

--
-- Name: membership_promotions membership_promotions_code_owner_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_code_owner_promoter_id_fkey FOREIGN KEY (code_owner_promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: membership_promotions membership_promotions_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: membership_promotions membership_promotions_promotion_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.membership_promotions
    ADD CONSTRAINT membership_promotions_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES app.promotions(promotions_id);

--
-- Name: memberships memberships_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: memberships memberships_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: memberships memberships_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: memberships memberships_plan_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.memberships
    ADD CONSTRAINT memberships_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES app.plans(plans_id);

--
-- Name: notifications notifications_recipient_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.notifications
    ADD CONSTRAINT notifications_recipient_user_id_fkey FOREIGN KEY (recipient_user_id) REFERENCES app.users(users_id);

--
-- Name: organizations organizations_official_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.organizations
    ADD CONSTRAINT organizations_official_currency_id_fkey FOREIGN KEY (official_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: organizations organizations_reference_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.organizations
    ADD CONSTRAINT organizations_reference_currency_id_fkey FOREIGN KEY (reference_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: payer_bank_account_payment_methods payer_bank_account_payment_methods_payer_bank_account_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_account_payment_methods
    ADD CONSTRAINT payer_bank_account_payment_methods_payer_bank_account_id_fkey FOREIGN KEY (payer_bank_account_id) REFERENCES app.payer_bank_accounts(payer_bank_accounts_id) ON DELETE CASCADE;

--
-- Name: payer_bank_account_payment_methods payer_bank_account_payment_methods_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_account_payment_methods
    ADD CONSTRAINT payer_bank_account_payment_methods_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES app.payment_methods(payment_methods_id);

--
-- Name: payer_bank_accounts payer_bank_accounts_bank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_accounts
    ADD CONSTRAINT payer_bank_accounts_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES app.banks(banks_id);

--
-- Name: payer_bank_accounts payer_bank_accounts_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payer_bank_accounts
    ADD CONSTRAINT payer_bank_accounts_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: payment_credit_claims payment_credit_claims_opened_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_credit_claims
    ADD CONSTRAINT payment_credit_claims_opened_by_fkey FOREIGN KEY (opened_by) REFERENCES app.users(users_id);

--
-- Name: payment_credit_claims payment_credit_claims_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_credit_claims
    ADD CONSTRAINT payment_credit_claims_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: payment_credit_claims payment_credit_claims_resolved_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_credit_claims
    ADD CONSTRAINT payment_credit_claims_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES app.users(users_id);

--
-- Name: payment_lines payment_lines_bank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES app.banks(banks_id);

--
-- Name: payment_lines payment_lines_company_bank_account_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_company_bank_account_id_fkey FOREIGN KEY (company_bank_account_id) REFERENCES app.company_bank_accounts(company_bank_accounts_id);

--
-- Name: payment_lines payment_lines_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: payment_lines payment_lines_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id) ON DELETE CASCADE;

--
-- Name: payment_lines payment_lines_payment_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_payment_type_id_fkey FOREIGN KEY (payment_type_id) REFERENCES app.payment_methods(payment_methods_id);

--
-- Name: payment_lines payment_lines_payout_account_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_payout_account_id_fkey FOREIGN KEY (payout_account_id) REFERENCES app.promoter_payout_accounts(promoter_payout_accounts_id);

--
-- Name: payment_lines payment_lines_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payment_lines
    ADD CONSTRAINT payment_lines_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES app.users(users_id);

--
-- Name: payments payments_beneficiary_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_beneficiary_person_id_fkey FOREIGN KEY (beneficiary_person_id) REFERENCES app.persons(persons_id);

--
-- Name: payments payments_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: payments payments_corporate_contract_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_corporate_contract_id_fkey FOREIGN KEY (corporate_contract_id) REFERENCES app.corporate_contracts(corporate_contracts_id);

--
-- Name: payments payments_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: payments payments_discounted_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_discounted_by_fkey FOREIGN KEY (discounted_by) REFERENCES app.users(users_id);

--
-- Name: payments payments_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: payments payments_payer_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_payer_user_id_fkey FOREIGN KEY (payer_user_id) REFERENCES app.users(users_id);

--
-- Name: payments payments_payment_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_payment_type_id_fkey FOREIGN KEY (payment_type_id) REFERENCES app.payment_categories(payment_categories_id);

--
-- Name: payments payments_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: payments payments_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: payments payments_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.payments
    ADD CONSTRAINT payments_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES app.users(users_id);

--
-- Name: permissions permissions_domain_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.permissions
    ADD CONSTRAINT permissions_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES app.permission_domains(permission_domains_id);

--
-- Name: person_credit_entries person_credit_entries_charge_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_charge_id_fkey FOREIGN KEY (charge_id) REFERENCES app.membership_charges(membership_charges_id);

--
-- Name: person_credit_entries person_credit_entries_converted_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_converted_currency_id_fkey FOREIGN KEY (converted_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: person_credit_entries person_credit_entries_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: person_credit_entries person_credit_entries_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: person_credit_entries person_credit_entries_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES app.payments(payments_id);

--
-- Name: person_credit_entries person_credit_entries_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_credit_entries
    ADD CONSTRAINT person_credit_entries_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: person_professional_licenses person_professional_licenses_licensing_body_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_professional_licenses
    ADD CONSTRAINT person_professional_licenses_licensing_body_id_fkey FOREIGN KEY (licensing_body_id) REFERENCES app.licensing_bodies(licensing_bodies_id);

--
-- Name: person_professional_licenses person_professional_licenses_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.person_professional_licenses
    ADD CONSTRAINT person_professional_licenses_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: persons persons_city_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_city_id_fkey FOREIGN KEY (city_id) REFERENCES app.cities(cities_id);

--
-- Name: persons persons_gender_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_gender_id_fkey FOREIGN KEY (gender_id) REFERENCES app.genders(genders_id);

--
-- Name: persons persons_marital_status_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.persons
    ADD CONSTRAINT persons_marital_status_id_fkey FOREIGN KEY (marital_status_id) REFERENCES app.marital_statuses(marital_statuses_id);

--
-- Name: plans plans_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.plans
    ADD CONSTRAINT plans_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: products products_owner_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.products
    ADD CONSTRAINT products_owner_ally_id_fkey FOREIGN KEY (owner_ally_id) REFERENCES app.allies(allies_id) ON DELETE CASCADE;

--
-- Name: products products_product_category_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.products
    ADD CONSTRAINT products_product_category_id_fkey FOREIGN KEY (product_category_id) REFERENCES app.product_categories(product_categories_id);

--
-- Name: professions professions_parent_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.professions
    ADD CONSTRAINT professions_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES app.professions(professions_id);

--
-- Name: promoter_bonus_awards promoter_bonus_awards_bonus_rule_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_bonus_awards
    ADD CONSTRAINT promoter_bonus_awards_bonus_rule_id_fkey FOREIGN KEY (bonus_rule_id) REFERENCES app.commission_bonus_rules(commission_bonus_rules_id);

--
-- Name: promoter_bonus_awards promoter_bonus_awards_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_bonus_awards
    ADD CONSTRAINT promoter_bonus_awards_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoter_bonus_awards promoter_bonus_awards_reward_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_bonus_awards
    ADD CONSTRAINT promoter_bonus_awards_reward_currency_id_fkey FOREIGN KEY (reward_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_currency_id_fkey FOREIGN KEY (currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_payout_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_payout_payment_id_fkey FOREIGN KEY (payout_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_source_commission_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_source_commission_id_fkey FOREIGN KEY (source_commission_id) REFERENCES app.commissions(commissions_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_source_override_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_source_override_id_fkey FOREIGN KEY (source_override_id) REFERENCES app.promoter_hierarchy_overrides(promoter_hierarchy_overrides_id);

--
-- Name: promoter_hierarchy_overrides promoter_hierarchy_overrides_tier_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_hierarchy_overrides
    ADD CONSTRAINT promoter_hierarchy_overrides_tier_id_fkey FOREIGN KEY (tier_id) REFERENCES app.hierarchy_override_tiers(hierarchy_override_tiers_id);

--
-- Name: promoter_member_contacts promoter_member_contacts_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_member_contacts
    ADD CONSTRAINT promoter_member_contacts_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: promoter_member_contacts promoter_member_contacts_promised_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_member_contacts
    ADD CONSTRAINT promoter_member_contacts_promised_currency_id_fkey FOREIGN KEY (promised_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: promoter_member_contacts promoter_member_contacts_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_member_contacts
    ADD CONSTRAINT promoter_member_contacts_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoter_payout_account_payment_methods promoter_payout_account_payment_methods_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_account_payment_methods
    ADD CONSTRAINT promoter_payout_account_payment_methods_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES app.payment_methods(payment_methods_id);

--
-- Name: promoter_payout_account_payment_methods promoter_payout_account_payment_promoter_payout_account_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_account_payment_methods
    ADD CONSTRAINT promoter_payout_account_payment_promoter_payout_account_id_fkey FOREIGN KEY (promoter_payout_account_id) REFERENCES app.promoter_payout_accounts(promoter_payout_accounts_id) ON DELETE CASCADE;

--
-- Name: promoter_payout_accounts promoter_payout_accounts_bank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_accounts
    ADD CONSTRAINT promoter_payout_accounts_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES app.banks(banks_id);

--
-- Name: promoter_payout_accounts promoter_payout_accounts_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_payout_accounts
    ADD CONSTRAINT promoter_payout_accounts_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id) ON DELETE CASCADE;

--
-- Name: promoter_ranks promoter_ranks_parent_rank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_ranks
    ADD CONSTRAINT promoter_ranks_parent_rank_id_fkey FOREIGN KEY (parent_rank_id) REFERENCES app.promoter_ranks(promoter_ranks_id);

--
-- Name: promoter_references promoter_references_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_references
    ADD CONSTRAINT promoter_references_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id) ON DELETE CASCADE;

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES app.users(users_id);

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_from_supervisor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_from_supervisor_id_fkey FOREIGN KEY (from_supervisor_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_promoter_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_promoter_id_fkey FOREIGN KEY (promoter_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoter_supervisor_assignments promoter_supervisor_assignments_to_supervisor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoter_supervisor_assignments
    ADD CONSTRAINT promoter_supervisor_assignments_to_supervisor_id_fkey FOREIGN KEY (to_supervisor_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoters promoters_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: promoters promoters_promoter_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_promoter_type_id_fkey FOREIGN KEY (promoter_type_id) REFERENCES app.promoter_types(promoter_types_id);

--
-- Name: promoters promoters_rank_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_rank_id_fkey FOREIGN KEY (rank_id) REFERENCES app.promoter_ranks(promoter_ranks_id);

--
-- Name: promoters promoters_supervisor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_supervisor_id_fkey FOREIGN KEY (supervisor_id) REFERENCES app.promoters(promoters_id);

--
-- Name: promoters promoters_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promoters
    ADD CONSTRAINT promoters_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: promotion_plans promotion_plans_plan_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotion_plans
    ADD CONSTRAINT promotion_plans_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES app.plans(plans_id);

--
-- Name: promotion_plans promotion_plans_promotion_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotion_plans
    ADD CONSTRAINT promotion_plans_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES app.promotions(promotions_id) ON DELETE CASCADE;

--
-- Name: promotions promotions_campaign_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.promotions
    ADD CONSTRAINT promotions_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES app.campaigns(campaigns_id);

--
-- Name: referrals referrals_referred_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_referred_member_id_fkey FOREIGN KEY (referred_member_id) REFERENCES app.members(members_id);

--
-- Name: referrals referrals_referrer_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_referrer_member_id_fkey FOREIGN KEY (referrer_member_id) REFERENCES app.members(members_id);

--
-- Name: referrals referrals_reward_currency_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_reward_currency_id_fkey FOREIGN KEY (reward_currency_id) REFERENCES app.currencies(currencies_id);

--
-- Name: referrals referrals_reward_payment_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.referrals
    ADD CONSTRAINT referrals_reward_payment_id_fkey FOREIGN KEY (reward_payment_id) REFERENCES app.payments(payments_id);

--
-- Name: report_audit_log report_audit_log_actor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_audit_log
    ADD CONSTRAINT report_audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES app.users(users_id);

--
-- Name: report_audit_log report_audit_log_attached_file_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_audit_log
    ADD CONSTRAINT report_audit_log_attached_file_id_fkey FOREIGN KEY (attached_file_id) REFERENCES app.attached_files(attached_files_id);

--
-- Name: report_audit_log report_audit_log_login_audit_log_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_audit_log
    ADD CONSTRAINT report_audit_log_login_audit_log_id_fkey FOREIGN KEY (login_audit_log_id) REFERENCES app.login_audit_log(login_audit_log_id);

--
-- Name: report_share report_share_created_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_share
    ADD CONSTRAINT report_share_created_by_fkey FOREIGN KEY (created_by) REFERENCES app.users(users_id);

--
-- Name: report_share report_share_report_audit_log_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.report_share
    ADD CONSTRAINT report_share_report_audit_log_id_fkey FOREIGN KEY (report_audit_log_id) REFERENCES app.report_audit_log(report_audit_log_id);

--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES app.permissions(permissions_id) ON DELETE CASCADE;

--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES app.roles(roles_id);

--
-- Name: scheduled_job_runs scheduled_job_runs_scheduled_job_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.scheduled_job_runs
    ADD CONSTRAINT scheduled_job_runs_scheduled_job_id_fkey FOREIGN KEY (scheduled_job_id) REFERENCES app.scheduled_jobs(scheduled_jobs_id);

--
-- Name: states states_country_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.states
    ADD CONSTRAINT states_country_id_fkey FOREIGN KEY (country_id) REFERENCES app.countries(countries_id);

--
-- Name: subsidies subsidies_authorized_by_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidies
    ADD CONSTRAINT subsidies_authorized_by_fkey FOREIGN KEY (authorized_by) REFERENCES app.users(users_id);

--
-- Name: subsidies subsidies_member_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidies
    ADD CONSTRAINT subsidies_member_id_fkey FOREIGN KEY (member_id) REFERENCES app.members(members_id);

--
-- Name: subsidy_audit_log subsidy_audit_log_actor_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_audit_log
    ADD CONSTRAINT subsidy_audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES app.users(users_id);

--
-- Name: subsidy_audit_log subsidy_audit_log_subsidy_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_audit_log
    ADD CONSTRAINT subsidy_audit_log_subsidy_id_fkey FOREIGN KEY (subsidy_id) REFERENCES app.subsidies(subsidies_id);

--
-- Name: subsidy_beneficiaries subsidy_beneficiaries_beneficiary_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_beneficiaries
    ADD CONSTRAINT subsidy_beneficiaries_beneficiary_id_fkey FOREIGN KEY (beneficiary_id) REFERENCES app.beneficiaries(beneficiaries_id);

--
-- Name: subsidy_beneficiaries subsidy_beneficiaries_subsidy_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.subsidy_beneficiaries
    ADD CONSTRAINT subsidy_beneficiaries_subsidy_id_fkey FOREIGN KEY (subsidy_id) REFERENCES app.subsidies(subsidies_id) ON DELETE CASCADE;

--
-- Name: terms_acceptances terms_acceptances_ally_agreement_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_ally_agreement_id_fkey FOREIGN KEY (ally_agreement_id) REFERENCES app.ally_agreements(ally_agreements_id);

--
-- Name: terms_acceptances terms_acceptances_membership_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_membership_id_fkey FOREIGN KEY (membership_id) REFERENCES app.memberships(memberships_id);

--
-- Name: terms_acceptances terms_acceptances_terms_versions_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_terms_versions_id_fkey FOREIGN KEY (terms_versions_id) REFERENCES app.terms_versions(terms_versions_id);

--
-- Name: terms_acceptances terms_acceptances_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_acceptances
    ADD CONSTRAINT terms_acceptances_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: terms_versions terms_versions_ally_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_versions
    ADD CONSTRAINT terms_versions_ally_id_fkey FOREIGN KEY (ally_id) REFERENCES app.allies(allies_id);

--
-- Name: terms_versions terms_versions_ally_type_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.terms_versions
    ADD CONSTRAINT terms_versions_ally_type_id_fkey FOREIGN KEY (ally_type_id) REFERENCES app.ally_types(ally_types_id);

--
-- Name: user_password_history user_password_history_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_password_history
    ADD CONSTRAINT user_password_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES app.roles(roles_id);

--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: user_sessions_log user_sessions_log_user_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.user_sessions_log
    ADD CONSTRAINT user_sessions_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES app.users(users_id);

--
-- Name: users users_default_role_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT users_default_role_id_fkey FOREIGN KEY (default_role_id) REFERENCES app.roles(roles_id);

--
-- Name: users users_person_id_fkey; Type: FK CONSTRAINT; Schema: app; Owner: -
--

ALTER TABLE ONLY app.users
    ADD CONSTRAINT users_person_id_fkey FOREIGN KEY (person_id) REFERENCES app.persons(persons_id);

--
-- Name: SCHEMA app; Type: ACL; Schema: -; Owner: -
--

GRANT ALL ON SCHEMA app TO optibienestar360_migration;
GRANT USAGE ON SCHEMA app TO optibienestar360_app;
GRANT USAGE ON SCHEMA app TO optibienestar360_readonly;

--
-- Name: TABLE affiliation_request_logs; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.affiliation_request_logs TO optibienestar360_app;
GRANT SELECT ON TABLE app.affiliation_request_logs TO optibienestar360_readonly;

--
-- Name: SEQUENCE affiliation_request_logs_affiliation_request_logs_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.affiliation_request_logs_affiliation_request_logs_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.affiliation_request_logs_affiliation_request_logs_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE affiliation_requests; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.affiliation_requests TO optibienestar360_app;
GRANT SELECT ON TABLE app.affiliation_requests TO optibienestar360_readonly;

--
-- Name: SEQUENCE affiliation_requests_affiliation_requests_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.affiliation_requests_affiliation_requests_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.affiliation_requests_affiliation_requests_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE allies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.allies TO optibienestar360_app;
GRANT SELECT ON TABLE app.allies TO optibienestar360_readonly;

--
-- Name: SEQUENCE allies_allies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.allies_allies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.allies_allies_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE allies_ally_number_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.allies_ally_number_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.allies_ally_number_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_agreements; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_agreements TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_agreements TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_agreements_ally_agreements_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_agreements_ally_agreements_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_agreements_ally_agreements_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_ally_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_ally_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_ally_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_ally_types_ally_ally_types_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_ally_types_ally_ally_types_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_ally_types_ally_ally_types_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_opening_hours; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_opening_hours TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_opening_hours TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_opening_hours_ally_opening_hours_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_opening_hours_ally_opening_hours_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_opening_hours_ally_opening_hours_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_patient_links; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_patient_links TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_patient_links TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_patient_links_ally_patient_links_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_patient_links_ally_patient_links_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_patient_links_ally_patient_links_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_product_review_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_product_review_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_product_review_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_product_review_log_ally_product_review_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_product_review_log_ally_product_review_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_product_review_log_ally_product_review_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_products; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_products TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_products TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_products_ally_products_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_products_ally_products_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_products_ally_products_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_professions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_professions TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_professions TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_specialties_ally_specialties_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_specialties_ally_specialties_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_specialties_ally_specialties_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_types_ally_types_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_types_ally_types_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_types_ally_types_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE ally_users; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.ally_users TO optibienestar360_app;
GRANT SELECT ON TABLE app.ally_users TO optibienestar360_readonly;

--
-- Name: SEQUENCE ally_users_ally_users_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.ally_users_ally_users_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.ally_users_ally_users_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE attached_files; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.attached_files TO optibienestar360_app;
GRANT SELECT ON TABLE app.attached_files TO optibienestar360_readonly;

--
-- Name: SEQUENCE attached_files_attached_files_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.attached_files_attached_files_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.attached_files_attached_files_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE entity_config; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.entity_config TO optibienestar360_app;
GRANT SELECT ON TABLE app.entity_config TO optibienestar360_readonly;

--
-- Name: SEQUENCE audit_entity_config_audit_entity_config_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.audit_entity_config_audit_entity_config_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.audit_entity_config_audit_entity_config_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE banks; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.banks TO optibienestar360_app;
GRANT SELECT ON TABLE app.banks TO optibienestar360_readonly;

--
-- Name: SEQUENCE banks_banks_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.banks_banks_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.banks_banks_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE beneficiaries; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.beneficiaries TO optibienestar360_app;
GRANT SELECT ON TABLE app.beneficiaries TO optibienestar360_readonly;

--
-- Name: SEQUENCE beneficiaries_beneficiaries_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.beneficiaries_beneficiaries_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.beneficiaries_beneficiaries_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE benefit_usages; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.benefit_usages TO optibienestar360_app;
GRANT SELECT ON TABLE app.benefit_usages TO optibienestar360_readonly;

--
-- Name: SEQUENCE benefit_usages_benefit_usages_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.benefit_usages_benefit_usages_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.benefit_usages_benefit_usages_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE campaign_promoters; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.campaign_promoters TO optibienestar360_app;
GRANT SELECT ON TABLE app.campaign_promoters TO optibienestar360_readonly;

--
-- Name: SEQUENCE campaign_promoters_campaign_promoters_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.campaign_promoters_campaign_promoters_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.campaign_promoters_campaign_promoters_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE campaign_transaction_exceptions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.campaign_transaction_exceptions TO optibienestar360_app;
GRANT SELECT ON TABLE app.campaign_transaction_exceptions TO optibienestar360_readonly;

--
-- Name: SEQUENCE campaign_transaction_exceptio_campaign_transaction_exceptio_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.campaign_transaction_exceptio_campaign_transaction_exceptio_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.campaign_transaction_exceptio_campaign_transaction_exceptio_seq TO optibienestar360_readonly;

--
-- Name: TABLE campaign_transaction_links; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.campaign_transaction_links TO optibienestar360_app;
GRANT SELECT ON TABLE app.campaign_transaction_links TO optibienestar360_readonly;

--
-- Name: SEQUENCE campaign_transaction_links_campaign_transaction_links_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.campaign_transaction_links_campaign_transaction_links_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.campaign_transaction_links_campaign_transaction_links_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE campaigns; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.campaigns TO optibienestar360_app;
GRANT SELECT ON TABLE app.campaigns TO optibienestar360_readonly;

--
-- Name: SEQUENCE campaigns_campaigns_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.campaigns_campaigns_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.campaigns_campaigns_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE cities; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.cities TO optibienestar360_app;
GRANT SELECT ON TABLE app.cities TO optibienestar360_readonly;

--
-- Name: SEQUENCE cities_cities_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.cities_cities_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.cities_cities_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE collection_commission_tier_promoter_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.collection_commission_tier_promoter_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.collection_commission_tier_promoter_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE collection_commission_tier_pr_collection_commission_tier_pr_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.collection_commission_tier_pr_collection_commission_tier_pr_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.collection_commission_tier_pr_collection_commission_tier_pr_seq TO optibienestar360_readonly;

--
-- Name: TABLE collection_commission_tiers; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.collection_commission_tiers TO optibienestar360_app;
GRANT SELECT ON TABLE app.collection_commission_tiers TO optibienestar360_readonly;

--
-- Name: SEQUENCE collection_commission_tiers_collection_commission_tiers_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.collection_commission_tiers_collection_commission_tiers_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.collection_commission_tiers_collection_commission_tiers_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE commission_bonus_rule_promoter_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_bonus_rule_promoter_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_bonus_rule_promoter_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_bonus_rule_promote_commission_bonus_rule_promote_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_bonus_rule_promote_commission_bonus_rule_promote_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_bonus_rule_promote_commission_bonus_rule_promote_seq TO optibienestar360_readonly;

--
-- Name: TABLE commission_bonus_rules; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_bonus_rules TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_bonus_rules TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_bonus_rules_commission_bonus_rules_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_bonus_rules_commission_bonus_rules_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_bonus_rules_commission_bonus_rules_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE commissions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commissions TO optibienestar360_app;
GRANT SELECT ON TABLE app.commissions TO optibienestar360_readonly;

--
-- Name: TABLE commission_period_summary; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_period_summary TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_period_summary TO optibienestar360_readonly;

--
-- Name: TABLE commission_retroactive_topup_cuts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_retroactive_topup_cuts TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_retroactive_topup_cuts TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_retroactive_topup__commission_retroactive_topup__seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_retroactive_topup__commission_retroactive_topup__seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_retroactive_topup__commission_retroactive_topup__seq TO optibienestar360_readonly;

--
-- Name: TABLE commission_retroactive_topups; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_retroactive_topups TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_retroactive_topups TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_retroactive_topups_commission_retroactive_topups_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_retroactive_topups_commission_retroactive_topups_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_retroactive_topups_commission_retroactive_topups_seq TO optibienestar360_readonly;

--
-- Name: TABLE commission_tier_promoter_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_tier_promoter_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_tier_promoter_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_tier_promoter_type_commission_tier_promoter_type_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_tier_promoter_type_commission_tier_promoter_type_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_tier_promoter_type_commission_tier_promoter_type_seq TO optibienestar360_readonly;

--
-- Name: TABLE commission_tiers; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.commission_tiers TO optibienestar360_app;
GRANT SELECT ON TABLE app.commission_tiers TO optibienestar360_readonly;

--
-- Name: SEQUENCE commission_tiers_commission_tiers_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commission_tiers_commission_tiers_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commission_tiers_commission_tiers_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE commissions_commissions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.commissions_commissions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.commissions_commissions_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE company_bank_account_payment_methods; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.company_bank_account_payment_methods TO optibienestar360_app;
GRANT SELECT ON TABLE app.company_bank_account_payment_methods TO optibienestar360_readonly;

--
-- Name: TABLE company_bank_accounts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.company_bank_accounts TO optibienestar360_app;
GRANT SELECT ON TABLE app.company_bank_accounts TO optibienestar360_readonly;

--
-- Name: SEQUENCE company_bank_accounts_company_bank_accounts_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.company_bank_accounts_company_bank_accounts_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.company_bank_accounts_company_bank_accounts_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_award_settlements; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_award_settlements TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_award_settlements TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_award__competitive_commission_award__seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_award__competitive_commission_award__seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_award__competitive_commission_award__seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_awards; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_awards TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_awards TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_awards_competitive_commission_awards_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_awards_competitive_commission_awards_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_awards_competitive_commission_awards_seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_manual_decisions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_manual_decisions TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_manual_decisions TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_manual_competitive_commission_manual_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_manual_competitive_commission_manual_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_manual_competitive_commission_manual_seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_rule_positions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_rule_positions TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_rule_positions TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_rule_p_competitive_commission_rule_p_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_rule_p_competitive_commission_rule_p_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_rule_p_competitive_commission_rule_p_seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_rule_promoter_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_rule_promoter_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_rule_promoter_types TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_rule_ranks; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_rule_ranks TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_rule_ranks TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_rules; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_rules TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_rules TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_rules_competitive_commission_rules_i_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_rules_competitive_commission_rules_i_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_rules_competitive_commission_rules_i_seq TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_tie_candidates; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_tie_candidates TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_tie_candidates TO optibienestar360_readonly;

--
-- Name: TABLE competitive_commission_ties; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.competitive_commission_ties TO optibienestar360_app;
GRANT SELECT ON TABLE app.competitive_commission_ties TO optibienestar360_readonly;

--
-- Name: SEQUENCE competitive_commission_ties_competitive_commission_ties_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_ties_competitive_commission_ties_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.competitive_commission_ties_competitive_commission_ties_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE contact_messages; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.contact_messages TO optibienestar360_app;
GRANT SELECT ON TABLE app.contact_messages TO optibienestar360_readonly;

--
-- Name: SEQUENCE contact_messages_contact_messages_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.contact_messages_contact_messages_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.contact_messages_contact_messages_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE corporate_contracts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.corporate_contracts TO optibienestar360_app;
GRANT SELECT ON TABLE app.corporate_contracts TO optibienestar360_readonly;

--
-- Name: SEQUENCE corporate_contracts_corporate_contracts_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.corporate_contracts_corporate_contracts_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.corporate_contracts_corporate_contracts_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE countries; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.countries TO optibienestar360_app;
GRANT SELECT ON TABLE app.countries TO optibienestar360_readonly;

--
-- Name: SEQUENCE countries_countries_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.countries_countries_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.countries_countries_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE currencies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.currencies TO optibienestar360_app;
GRANT SELECT ON TABLE app.currencies TO optibienestar360_readonly;

--
-- Name: SEQUENCE currencies_currencies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.currencies_currencies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.currencies_currencies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE data_change_audit_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.data_change_audit_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.data_change_audit_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE data_change_audit_log_data_change_audit_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.data_change_audit_log_data_change_audit_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.data_change_audit_log_data_change_audit_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE members; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.members TO optibienestar360_app;
GRANT SELECT ON TABLE app.members TO optibienestar360_readonly;

--
-- Name: TABLE memberships; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.memberships TO optibienestar360_app;
GRANT SELECT ON TABLE app.memberships TO optibienestar360_readonly;

--
-- Name: TABLE persons; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.persons TO optibienestar360_app;
GRANT SELECT ON TABLE app.persons TO optibienestar360_readonly;

--
-- Name: TABLE plans; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.plans TO optibienestar360_app;
GRANT SELECT ON TABLE app.plans TO optibienestar360_readonly;

--
-- Name: TABLE users; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.users TO optibienestar360_app;
GRANT SELECT ON TABLE app.users TO optibienestar360_readonly;

--
-- Name: TABLE digital_cards_view; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.digital_cards_view TO optibienestar360_app;
GRANT SELECT ON TABLE app.digital_cards_view TO optibienestar360_readonly;

--
-- Name: TABLE document_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.document_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.document_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE document_types_document_types_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.document_types_document_types_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.document_types_document_types_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE exchange_rates; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.exchange_rates TO optibienestar360_app;
GRANT SELECT ON TABLE app.exchange_rates TO optibienestar360_readonly;

--
-- Name: SEQUENCE exchange_rates_exchange_rates_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.exchange_rates_exchange_rates_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.exchange_rates_exchange_rates_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE file_download_links; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.file_download_links TO optibienestar360_app;
GRANT SELECT ON TABLE app.file_download_links TO optibienestar360_readonly;

--
-- Name: SEQUENCE file_download_links_file_download_links_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.file_download_links_file_download_links_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.file_download_links_file_download_links_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE file_type_policies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.file_type_policies TO optibienestar360_app;
GRANT SELECT ON TABLE app.file_type_policies TO optibienestar360_readonly;

--
-- Name: SEQUENCE file_type_policies_file_type_policies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.file_type_policies_file_type_policies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.file_type_policies_file_type_policies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE flyway_schema_history; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.flyway_schema_history TO optibienestar360_migration;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.flyway_schema_history TO optibienestar360_app;
GRANT SELECT ON TABLE app.flyway_schema_history TO optibienestar360_readonly;

--
-- Name: TABLE genders; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.genders TO optibienestar360_app;
GRANT SELECT ON TABLE app.genders TO optibienestar360_readonly;

--
-- Name: SEQUENCE genders_genders_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.genders_genders_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.genders_genders_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE hierarchy_override_tiers; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.hierarchy_override_tiers TO optibienestar360_app;
GRANT SELECT ON TABLE app.hierarchy_override_tiers TO optibienestar360_readonly;

--
-- Name: SEQUENCE hierarchy_override_tiers_hierarchy_override_tiers_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.hierarchy_override_tiers_hierarchy_override_tiers_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.hierarchy_override_tiers_hierarchy_override_tiers_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE holidays; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.holidays TO optibienestar360_app;
GRANT SELECT ON TABLE app.holidays TO optibienestar360_readonly;

--
-- Name: SEQUENCE holidays_holidays_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.holidays_holidays_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.holidays_holidays_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE licensing_bodies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.licensing_bodies TO optibienestar360_app;
GRANT SELECT ON TABLE app.licensing_bodies TO optibienestar360_readonly;

--
-- Name: SEQUENCE licensing_bodies_licensing_bodies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.licensing_bodies_licensing_bodies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.licensing_bodies_licensing_bodies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE login_audit_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.login_audit_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.login_audit_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE login_audit_log_login_audit_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.login_audit_log_login_audit_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.login_audit_log_login_audit_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE marital_statuses; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.marital_statuses TO optibienestar360_app;
GRANT SELECT ON TABLE app.marital_statuses TO optibienestar360_readonly;

--
-- Name: SEQUENCE marital_statuses_marital_statuses_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.marital_statuses_marital_statuses_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.marital_statuses_marital_statuses_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE media_items; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.media_items TO optibienestar360_app;
GRANT SELECT ON TABLE app.media_items TO optibienestar360_readonly;

--
-- Name: SEQUENCE media_items_media_items_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.media_items_media_items_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.media_items_media_items_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE medical_record_access_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.medical_record_access_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.medical_record_access_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE medical_record_access_log_medical_record_access_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.medical_record_access_log_medical_record_access_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.medical_record_access_log_medical_record_access_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE medical_records; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.medical_records TO optibienestar360_app;
GRANT SELECT ON TABLE app.medical_records TO optibienestar360_readonly;

--
-- Name: SEQUENCE medical_records_medical_records_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.medical_records_medical_records_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.medical_records_medical_records_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE professions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.professions TO optibienestar360_app;
GRANT SELECT ON TABLE app.professions TO optibienestar360_readonly;

--
-- Name: SEQUENCE medical_specialties_medical_specialties_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.medical_specialties_medical_specialties_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.medical_specialties_medical_specialties_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE member_documents; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.member_documents TO optibienestar360_app;
GRANT SELECT ON TABLE app.member_documents TO optibienestar360_readonly;

--
-- Name: SEQUENCE member_documents_member_documents_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.member_documents_member_documents_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.member_documents_member_documents_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE member_promoter_assignments; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.member_promoter_assignments TO optibienestar360_app;
GRANT SELECT ON TABLE app.member_promoter_assignments TO optibienestar360_readonly;

--
-- Name: SEQUENCE member_promoter_assignments_member_promoter_assignments_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.member_promoter_assignments_member_promoter_assignments_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.member_promoter_assignments_member_promoter_assignments_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE members_members_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.members_members_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.members_members_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE membership_charges; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.membership_charges TO optibienestar360_app;
GRANT SELECT ON TABLE app.membership_charges TO optibienestar360_readonly;

--
-- Name: SEQUENCE membership_charges_membership_charges_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.membership_charges_membership_charges_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.membership_charges_membership_charges_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE membership_contract_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.membership_contract_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.membership_contract_seq TO optibienestar360_readonly;

--
-- Name: TABLE membership_promotions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.membership_promotions TO optibienestar360_app;
GRANT SELECT ON TABLE app.membership_promotions TO optibienestar360_readonly;

--
-- Name: SEQUENCE membership_promotions_membership_promotions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.membership_promotions_membership_promotions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.membership_promotions_membership_promotions_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE memberships_memberships_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.memberships_memberships_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.memberships_memberships_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE notifications; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.notifications TO optibienestar360_app;
GRANT SELECT ON TABLE app.notifications TO optibienestar360_readonly;

--
-- Name: SEQUENCE notifications_notifications_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.notifications_notifications_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.notifications_notifications_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE occupations; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.occupations TO optibienestar360_app;
GRANT SELECT ON TABLE app.occupations TO optibienestar360_readonly;

--
-- Name: SEQUENCE occupations_occupations_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.occupations_occupations_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.occupations_occupations_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE onboarding_policies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.onboarding_policies TO optibienestar360_app;
GRANT SELECT ON TABLE app.onboarding_policies TO optibienestar360_readonly;

--
-- Name: SEQUENCE onboarding_policies_onboarding_policies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.onboarding_policies_onboarding_policies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.onboarding_policies_onboarding_policies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE organizations; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.organizations TO optibienestar360_app;
GRANT SELECT ON TABLE app.organizations TO optibienestar360_readonly;

--
-- Name: SEQUENCE organizations_organizations_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.organizations_organizations_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.organizations_organizations_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payer_bank_account_payment_methods; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payer_bank_account_payment_methods TO optibienestar360_app;
GRANT SELECT ON TABLE app.payer_bank_account_payment_methods TO optibienestar360_readonly;

--
-- Name: TABLE payer_bank_accounts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payer_bank_accounts TO optibienestar360_app;
GRANT SELECT ON TABLE app.payer_bank_accounts TO optibienestar360_readonly;

--
-- Name: SEQUENCE payer_bank_accounts_payer_bank_accounts_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payer_bank_accounts_payer_bank_accounts_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payer_bank_accounts_payer_bank_accounts_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payment_categories; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payment_categories TO optibienestar360_app;
GRANT SELECT ON TABLE app.payment_categories TO optibienestar360_readonly;

--
-- Name: SEQUENCE payment_categories_payment_categories_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payment_categories_payment_categories_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payment_categories_payment_categories_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payment_credit_claims; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payment_credit_claims TO optibienestar360_app;
GRANT SELECT ON TABLE app.payment_credit_claims TO optibienestar360_readonly;

--
-- Name: SEQUENCE payment_credit_claims_payment_credit_claims_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payment_credit_claims_payment_credit_claims_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payment_credit_claims_payment_credit_claims_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payment_lines; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payment_lines TO optibienestar360_app;
GRANT SELECT ON TABLE app.payment_lines TO optibienestar360_readonly;

--
-- Name: SEQUENCE payment_lines_payment_line_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payment_lines_payment_line_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payment_lines_payment_line_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payment_methods; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payment_methods TO optibienestar360_app;
GRANT SELECT ON TABLE app.payment_methods TO optibienestar360_readonly;

--
-- Name: SEQUENCE payment_methods_payment_methods_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payment_methods_payment_methods_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payment_methods_payment_methods_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE payments; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.payments TO optibienestar360_app;
GRANT SELECT ON TABLE app.payments TO optibienestar360_readonly;

--
-- Name: SEQUENCE payments_payments_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.payments_payments_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.payments_payments_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE permission_domains; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.permission_domains TO optibienestar360_app;
GRANT SELECT ON TABLE app.permission_domains TO optibienestar360_readonly;

--
-- Name: SEQUENCE permission_domains_permission_domains_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.permission_domains_permission_domains_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.permission_domains_permission_domains_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE permissions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.permissions TO optibienestar360_app;
GRANT SELECT ON TABLE app.permissions TO optibienestar360_readonly;

--
-- Name: SEQUENCE permissions_permissions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.permissions_permissions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.permissions_permissions_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE person_credit_entries; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.person_credit_entries TO optibienestar360_app;
GRANT SELECT ON TABLE app.person_credit_entries TO optibienestar360_readonly;

--
-- Name: SEQUENCE person_credit_entries_person_credit_entries_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.person_credit_entries_person_credit_entries_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.person_credit_entries_person_credit_entries_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE person_professional_licenses; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.person_professional_licenses TO optibienestar360_app;
GRANT SELECT ON TABLE app.person_professional_licenses TO optibienestar360_readonly;

--
-- Name: SEQUENCE person_professional_licenses_person_professional_licenses_i_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.person_professional_licenses_person_professional_licenses_i_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.person_professional_licenses_person_professional_licenses_i_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE persons_persons_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.persons_persons_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.persons_persons_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE plans_plans_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.plans_plans_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.plans_plans_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE product_categories; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.product_categories TO optibienestar360_app;
GRANT SELECT ON TABLE app.product_categories TO optibienestar360_readonly;

--
-- Name: SEQUENCE product_categories_product_categories_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.product_categories_product_categories_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.product_categories_product_categories_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE products; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.products TO optibienestar360_app;
GRANT SELECT ON TABLE app.products TO optibienestar360_readonly;

--
-- Name: SEQUENCE products_products_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.products_products_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.products_products_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_bonus_awards; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_bonus_awards TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_bonus_awards TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_bonus_awards_promoter_bonus_awards_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_bonus_awards_promoter_bonus_awards_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_bonus_awards_promoter_bonus_awards_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_hierarchy_overrides; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_hierarchy_overrides TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_hierarchy_overrides TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_hierarchy_overrides_promoter_hierarchy_overrides_i_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_hierarchy_overrides_promoter_hierarchy_overrides_i_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_hierarchy_overrides_promoter_hierarchy_overrides_i_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_member_contacts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_member_contacts TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_member_contacts TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_member_contacts_promoter_member_contacts_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_member_contacts_promoter_member_contacts_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_member_contacts_promoter_member_contacts_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_payout_account_payment_methods; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_payout_account_payment_methods TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_payout_account_payment_methods TO optibienestar360_readonly;

--
-- Name: TABLE promoter_payout_accounts; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_payout_accounts TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_payout_accounts TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_payout_accounts_promoter_payout_accounts_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_payout_accounts_promoter_payout_accounts_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_payout_accounts_promoter_payout_accounts_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_ranks; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_ranks TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_ranks TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_ranks_promoter_ranks_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_ranks_promoter_ranks_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_ranks_promoter_ranks_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_references; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_references TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_references TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_references_promoter_references_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_references_promoter_references_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_references_promoter_references_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_supervisor_assignments; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_supervisor_assignments TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_supervisor_assignments TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_supervisor_assignmen_promoter_supervisor_assignmen_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_supervisor_assignmen_promoter_supervisor_assignmen_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_supervisor_assignmen_promoter_supervisor_assignmen_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoter_types; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoter_types TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoter_types TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoter_types_promoter_types_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoter_types_promoter_types_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoter_types_promoter_types_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promoters; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promoters TO optibienestar360_app;
GRANT SELECT ON TABLE app.promoters TO optibienestar360_readonly;

--
-- Name: SEQUENCE promoters_promoters_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promoters_promoters_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promoters_promoters_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE promotion_plans; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promotion_plans TO optibienestar360_app;
GRANT SELECT ON TABLE app.promotion_plans TO optibienestar360_readonly;

--
-- Name: TABLE promotions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.promotions TO optibienestar360_app;
GRANT SELECT ON TABLE app.promotions TO optibienestar360_readonly;

--
-- Name: SEQUENCE promotions_promotions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.promotions_promotions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.promotions_promotions_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE referrals; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.referrals TO optibienestar360_app;
GRANT SELECT ON TABLE app.referrals TO optibienestar360_readonly;

--
-- Name: SEQUENCE referrals_referrals_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.referrals_referrals_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.referrals_referrals_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE report_audit_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.report_audit_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.report_audit_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE report_audit_log_report_audit_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.report_audit_log_report_audit_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.report_audit_log_report_audit_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE report_share; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.report_share TO optibienestar360_app;
GRANT SELECT ON TABLE app.report_share TO optibienestar360_readonly;

--
-- Name: SEQUENCE report_share_report_share_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.report_share_report_share_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.report_share_report_share_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE role_permissions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.role_permissions TO optibienestar360_app;
GRANT SELECT ON TABLE app.role_permissions TO optibienestar360_readonly;

--
-- Name: SEQUENCE role_permissions_role_permissions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.role_permissions_role_permissions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.role_permissions_role_permissions_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE roles; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.roles TO optibienestar360_app;
GRANT SELECT ON TABLE app.roles TO optibienestar360_readonly;

--
-- Name: SEQUENCE roles_roles_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.roles_roles_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.roles_roles_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE scheduled_job_runs; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.scheduled_job_runs TO optibienestar360_app;
GRANT SELECT ON TABLE app.scheduled_job_runs TO optibienestar360_readonly;

--
-- Name: SEQUENCE scheduled_job_runs_scheduled_job_runs_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.scheduled_job_runs_scheduled_job_runs_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.scheduled_job_runs_scheduled_job_runs_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE scheduled_jobs; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.scheduled_jobs TO optibienestar360_app;
GRANT SELECT ON TABLE app.scheduled_jobs TO optibienestar360_readonly;

--
-- Name: SEQUENCE scheduled_jobs_scheduled_jobs_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.scheduled_jobs_scheduled_jobs_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.scheduled_jobs_scheduled_jobs_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE security_policies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.security_policies TO optibienestar360_app;
GRANT SELECT ON TABLE app.security_policies TO optibienestar360_readonly;

--
-- Name: SEQUENCE security_policies_security_policies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.security_policies_security_policies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.security_policies_security_policies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE states; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.states TO optibienestar360_app;
GRANT SELECT ON TABLE app.states TO optibienestar360_readonly;

--
-- Name: SEQUENCE states_states_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.states_states_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.states_states_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE subsidies; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.subsidies TO optibienestar360_app;
GRANT SELECT ON TABLE app.subsidies TO optibienestar360_readonly;

--
-- Name: SEQUENCE subsidies_subsidies_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.subsidies_subsidies_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.subsidies_subsidies_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE subsidy_audit_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.subsidy_audit_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.subsidy_audit_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE subsidy_audit_log_subsidy_audit_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.subsidy_audit_log_subsidy_audit_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.subsidy_audit_log_subsidy_audit_log_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE subsidy_beneficiaries; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.subsidy_beneficiaries TO optibienestar360_app;
GRANT SELECT ON TABLE app.subsidy_beneficiaries TO optibienestar360_readonly;

--
-- Name: SEQUENCE subsidy_beneficiaries_subsidy_beneficiaries_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.subsidy_beneficiaries_subsidy_beneficiaries_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.subsidy_beneficiaries_subsidy_beneficiaries_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE system_configs; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.system_configs TO optibienestar360_app;
GRANT SELECT ON TABLE app.system_configs TO optibienestar360_readonly;

--
-- Name: SEQUENCE system_configs_system_configs_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.system_configs_system_configs_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.system_configs_system_configs_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE terms_acceptances; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.terms_acceptances TO optibienestar360_app;
GRANT SELECT ON TABLE app.terms_acceptances TO optibienestar360_readonly;

--
-- Name: SEQUENCE terms_acceptances_terms_acceptances_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.terms_acceptances_terms_acceptances_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.terms_acceptances_terms_acceptances_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE terms_versions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.terms_versions TO optibienestar360_app;
GRANT SELECT ON TABLE app.terms_versions TO optibienestar360_readonly;

--
-- Name: SEQUENCE terms_versions_terms_versions_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.terms_versions_terms_versions_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.terms_versions_terms_versions_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE user_password_history; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.user_password_history TO optibienestar360_app;
GRANT SELECT ON TABLE app.user_password_history TO optibienestar360_readonly;

--
-- Name: SEQUENCE user_password_history_user_password_history_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.user_password_history_user_password_history_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.user_password_history_user_password_history_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE user_roles; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.user_roles TO optibienestar360_app;
GRANT SELECT ON TABLE app.user_roles TO optibienestar360_readonly;

--
-- Name: SEQUENCE user_roles_user_roles_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.user_roles_user_roles_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.user_roles_user_roles_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE user_sessions_log; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.user_sessions_log TO optibienestar360_app;
GRANT SELECT ON TABLE app.user_sessions_log TO optibienestar360_readonly;

--
-- Name: SEQUENCE user_sessions_log_user_sessions_log_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.user_sessions_log_user_sessions_log_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.user_sessions_log_user_sessions_log_id_seq TO optibienestar360_readonly;

--
-- Name: SEQUENCE users_users_id_seq; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE app.users_users_id_seq TO optibienestar360_app;
GRANT SELECT,USAGE ON SEQUENCE app.users_users_id_seq TO optibienestar360_readonly;

--
-- Name: TABLE v_report_commissions; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.v_report_commissions TO optibienestar360_app;
GRANT SELECT ON TABLE app.v_report_commissions TO optibienestar360_readonly;

--
-- Name: TABLE v_report_payments; Type: ACL; Schema: app; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE app.v_report_payments TO optibienestar360_app;
GRANT SELECT ON TABLE app.v_report_payments TO optibienestar360_readonly;

--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: app; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT,USAGE ON SEQUENCES  TO optibienestar360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT,USAGE ON SEQUENCES  TO optibienestar360_readonly;

--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: app; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT,USAGE ON SEQUENCES  TO optibienestar360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT,USAGE ON SEQUENCES  TO optibienestar360_readonly;

--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: app; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES  TO optibienestar360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT ON TABLES  TO optibienestar360_readonly;

--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: app; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES  TO optibienestar360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT ON TABLES  TO optibienestar360_readonly;

--
-- PostgreSQL database dump complete
--

