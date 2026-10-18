SET search_path TO app, public;

-- ============================================================
-- V181: contract templates seed + acceptance evidence.
--
-- 1. organizations gains the three contract-header values the client
--    documents need (registry state, signing city, service area). They
--    feed the {{org.*}} placeholders TermsTemplateRenderer resolves
--    when serving a T&C version.
-- 2. Version 1 of each term_type (AFILIADO / PROMOTOR / ALIADO), only if
--    that type has no row yet — an environment where an admin already
--    published a version is left untouched. Texts come from the approved
--    client drafts (hub .ai/notes/2026-10-17-contract-seeds/) with the
--    HTML comment blocks removed, the ALIADO rates table replaced by a
--    reference to the agreement's rates annex, and per-person variables
--    replaced by neutral wording. Only {{org.*}} and {{brand.name}}
--    remain, resolved at serve time (the stored template stays generic).
-- 3. terms_acceptances gains evidence columns: client IP, user agent,
--    acceptance channel and, for self-service affiliation, the
--    membership the acceptance was given for.
-- ============================================================

-- ─── 1. organizations: contract header fields ───────────────────────────────
ALTER TABLE organizations
    ADD COLUMN registry_state VARCHAR(80),
    ADD COLUMN signing_city   VARCHAR(80),
    ADD COLUMN service_area   VARCHAR(150);

COMMENT ON COLUMN organizations.registry_state IS 'Estado del Registro Mercantil donde está inscrita la organización ({{org.registryState}} en los contratos)';
COMMENT ON COLUMN organizations.signing_city IS 'Ciudad de firma de los contratos ({{org.city}})';
COMMENT ON COLUMN organizations.service_area IS 'Zona de cobertura de la red de aliados ({{org.serviceArea}})';

UPDATE organizations
SET registry_state = COALESCE(registry_state, 'Portuguesa'),
    signing_city   = COALESCE(signing_city, 'Acarigua'),
    service_area   = COALESCE(service_area, 'eje Acarigua-Araure')
WHERE registry_state IS NULL
   OR signing_city IS NULL
   OR service_area IS NULL;

-- ─── 2. terms_versions: version 1 per type ──────────────────────────────────
INSERT INTO terms_versions (term_type, title, content_markdown, is_public, valid_from)
SELECT 'AFILIADO',
       'Acuerdo de Membresía y Blindaje Familiar',
       $$Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, con RIF **{{org.taxId}}**; y por la otra parte, la persona natural plenamente identificada en el anverso de este documento (en lo sucesivo "**EL AFILIADO TITULAR**"), convienen en celebrar el presente acuerdo de afiliación al ecosistema de salud y consumo, regido por las siguientes cláusulas operativas:

**PRIMERA: Objeto del Ecosistema (El Blindaje Económico).** {{brand.name}} otorga a EL AFILIADO TITULAR (y a sus dependientes registrados, si aplica) el derecho de acceso a una red exclusiva de comercios, clínicas, farmacias y especialistas médicos (Aliados B2B/B2P) en el {{org.serviceArea}}. Al presentar su Credencial Digital, el afiliado gozará del beneficio de una Tarifa Preferencial (descuento directo) sobre los bienes y servicios estipulados previamente entre la corporación y los aliados.

**SEGUNDA: Naturaleza Legal del Servicio (Importante).** {{brand.name}} opera estrictamente como una plataforma tecnológica y comercial de intermediación y gestión de descuentos. Este servicio NO es, ni sustituye, a una póliza de seguros de salud, plan de medicina prepagada o fondo de financiamiento regulado por la Superintendencia de la Actividad Aseguradora (SUDEASEG). El ecosistema no asume el pago de siniestros, deducibles ni hospitalizaciones; su función es garantizar el acceso a tarifas reducidas para proteger el presupuesto familiar.

**TERCERA: Uso de normativas.**

- **Validación Inmediata:** EL AFILIADO deberá presentar su Credencial Digital activa desde su dispositivo móvil, acompañada de su Cédula de Identidad, antes de la facturación en el comercio aliado.
- **Intransferibilidad (Prevención de Fraude):** La Credencial Digital es de uso estrictamente personal. El préstamo, captura de pantalla o intento de uso por parte de terceros no registrados en el núcleo familiar del titular, causará la anulación inmediata y definitiva del portafolio sin derecho a reembolso.
- **Pago Directo:** EL AFILIADO pagará el monto total del producto o servicio (ya con la Tarifa Preferencial aplicada) directamente en la caja del Aliado Comercial.

**CUARTA: Responsabilidad sobre Productos y Servicios Médicos.** Dado que {{brand.name}} es el conector tecnológico, la responsabilidad absoluta sobre la calidad de los productos vendidos, las garantías, la mala praxis médica o la atención al cliente dentro de las instalaciones, recae de manera exclusiva sobre el Aliado Comercial o Profesional (Comercio/Clínica) que presta el servicio final. {{brand.name}} audita constantemente a sus aliados para mantener estándares de excelencia, pero no es solidariamente responsable por contingencias operativas o médicas de terceros.

**QUINTA: Inversión, Vigencia y Renovación.**

- **Activación:** El acceso al ecosistema se activa en el momento en que EL AFILIADO TITULAR cancela el monto correspondiente a su membresía o cuota de mantenimiento.
- **Vigencia:** Este acuerdo tiene una validez de un (1) año calendario a partir de su emisión.
- **Modificaciones de la Red:** La plataforma es un ecosistema vivo. {{brand.name}} se reserva el derecho de agregar nuevos aliados, retirar aquellos que no cumplan con la calidad exigida, o actualizar los porcentajes de las tarifas preferenciales en beneficio de la red, lo cual será notificado en el catálogo digital de la plataforma.

Al procesar su pago y generar su Credencial Digital, EL AFILIADO TITULAR declara haber leído, comprendido y aceptado la naturaleza y normas de uso del ecosistema {{brand.name}}, sellando su integración a la red comercial más exclusiva de la región.$$,
       TRUE,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM terms_versions WHERE term_type = 'AFILIADO');

INSERT INTO terms_versions (term_type, title, content_markdown, is_public, valid_from)
SELECT 'PROMOTOR',
       'Contrato de Mandato y Representación Comercial Independiente',
       $$Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, RIF **{{org.taxId}}**; y por la otra parte, el ciudadano(a) plenamente identificado(a) en el anverso de este documento (en lo sucesivo "**EL ASESOR COMERCIAL**"), convienen en celebrar el presente contrato mercantil bajo las siguientes cláusulas:

**PRIMERA: Objeto del Contrato (Expansión del Ecosistema).** {{brand.name}} contrata los servicios profesionales e independientes de EL ASESOR COMERCIAL para ejecutar la promoción, captación, negociación y cierre de afiliaciones al sistema {{brand.name}}. Su gestión abarcará tanto el portafolio corporativo (Captación de Clínicas y Comercios Aliados - B2B/B2P) como el portafolio de consumo (Afiliación de Familias - B2C) dentro del territorio asignado, operando siempre bajo los principios de Fricción Cero y excelencia ejecutiva.

**SEGUNDA: Naturaleza Jurídica y Autonomía.** El presente es un contrato de naturaleza estrictamente mercantil y civil. EL ASESOR COMERCIAL actuará con total autonomía de tiempo, ruta y métodos (respetando la ética corporativa), utilizando sus propios recursos, sin estar sujeto a jornada laboral obligatoria ni subordinación jurídica continua. En consecuencia, este acuerdo no genera relación de dependencia laboral, excluyendo el pago de salario fijo, prestaciones sociales u otros pasivos derivados de la Ley Orgánica del Trabajo, los Trabajadores y las Trabajadoras. La retribución será 100% variable y basada en el rendimiento.

**TERCERA: Obligaciones del Asesor Comercial.** Para mantener el estándar de la marca, EL ASESOR COMERCIAL se obliga a:

- **Ejecución Táctica:** Utilizar los métodos de abordaje y descubrimiento aprobados por {{brand.name}}, transmitiendo con exactitud las políticas de Integración a Riesgo Cero y Blindaje Familiar.
- **Integridad de la Información:** Recaudar de manera veraz, legible y completa los datos de los comercios y usuarios finales a través de las Fichas Técnicas Oficiales, ingresándolos de inmediato a la plataforma para garantizar la operatividad de los afiliados.
- **Protección de Marca:** No alterar el logotipo, nombre, isotipo ni modificar las tarifas preferenciales establecidas por la Dirección de Expansión.

**CUARTA: Sistema de Rendimientos y Liquidación.** Como contraprestación única por sus servicios, EL ASESOR COMERCIAL percibirá honorarios mercantiles calculados exclusivamente sobre los cierres efectivos y cobrados, según el sistema de jerarquía financiera de {{brand.name}}. Los dividendos se liquidarán a la cuenta bancaria especificada en el anverso, bajo los siguientes conceptos (detallados en la Tabla de Comisiones Anexa):

- **Dividendo de Activación Inicial (DAI):** por cada nueva familia afiliada o empresa integrada de forma efectiva.
- **Tasa de Capitalización Acelerada (TCA):** bonificaciones por alcance de volumen semanal/mensual.
- **Margen de Tracción Superior (MTS):** rendimientos por captación de cuentas corporativas de alto impacto.
- **Rendimiento de Dominio 360:** participación residual según el nivel de jerarquía alcanzado dentro de la Fuerza de Expansión.

**QUINTA: Confidencialidad y Propiedad Intelectual.** Toda la información operativa, manuales de objeciones, guiones, listado de Comercios Aliados, Clínicas, Profesionales, entre otros (B2B/B2P), estrategias comerciales, software y bases de datos a los que tenga acceso EL ASESOR COMERCIAL son propiedad exclusiva e intransferible de {{brand.name}}. Queda estrictamente prohibida su divulgación, copia o uso para fines ajenos a este contrato, incluso después de la terminación del mismo, sujeto a acciones legales por daños y perjuicios.

**SEXTA: Vigencia y Resolución.** El presente contrato entra en vigencia a partir de su firma y tendrá una duración de un (1) año. Cualquiera de las partes podrá darlo por terminado sin expresión de causa, mediante simple notificación a la otra parte, procediéndose a la liquidación de los rendimientos generados hasta el día efectivo del cese. El incumplimiento de las cláusulas éticas o el fraude en las afiliaciones causará la resolución inmediata del contrato y el bloqueo permanente del ecosistema.

En señal de plena conformidad, habiendo leído y aceptado los términos que rigen a la Fuerza de Expansión, firman en la ciudad de {{org.city}}.$$,
       FALSE,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM terms_versions WHERE term_type = 'PROMOTOR');

INSERT INTO terms_versions (term_type, title, content_markdown, is_public, valid_from)
SELECT 'ALIADO',
       'Convenio Corporativo de Integración Comercial',
       $$Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, con RIF **{{org.taxId}}**, representada en este acto por su Asesor Comercial autorizado; y por la otra parte, el **ALIADO COMERCIAL / PROFESIONAL** (en lo sucesivo "**EL ALIADO**"), cuyos datos jurídicos y operativos se detallan en el Anexo A (Ficha Técnica de Integración), convienen en celebrar el presente acuerdo bajo las siguientes cláusulas:

**PRIMERA: Objeto del Convenio (Simbiosis Comercial).** El presente documento tiene como objetivo integrar a EL ALIADO a la red física y digital de {{brand.name}} en el {{org.serviceArea}}. {{brand.name}} se compromete a direccionar tráfico cautivo de familias afiliadas hacia las instalaciones de EL ALIADO, y este último se compromete a otorgar beneficios económicos (Tarifa Preferencial) de manera directa y exclusiva a dichos usuarios, bajo un modelo de operación de riesgo financiero cero.

**SEGUNDA: Obligaciones de {{brand.name}}.** Nuestra empresa garantiza a EL ALIADO el cumplimiento de los siguientes parámetros operativos:

- **Redireccionamiento de Tráfico:** Promover activamente los productos y servicios de EL ALIADO a través del catálogo digital, plataforma web y fuerza de ventas.
- **Integración a Riesgo Cero:** {{brand.name}} no cobrará a EL ALIADO cuotas de inscripción, mensualidades, ni comisiones por ventas. El modelo es de impacto directo al consumidor.
- **Soporte Tecnológico:** Garantizar que las Credenciales Digitales de los usuarios cuenten con los estándares de seguridad necesarios para una validación inmediata y segura en la caja de EL ALIADO.

**TERCERA: Obligaciones del Aliado (Operatividad de Fricción Cero).** Para garantizar una experiencia fluida e instantánea al consumidor, EL ALIADO se compromete a:

- **Validación Inmediata:** Reconocer la Credencial Digital (activa y vigente) presentada por el afiliado desde su dispositivo móvil, sin exigir papeleos, claves de aprobación telefónica ni trámites burocráticos adicionales.
- **Aplicación de la Tarifa Preferencial:** Aplicar el porcentaje de descuento o tarifa especial pactada en este acuerdo directamente sobre el monto total de la factura al momento del pago.
- **Exhibición de Marca:** Colocar el identificador visual de {{brand.name}} (calcomanía corporativa o display) en un lugar visible de sus cajas o recepción, indicando a los usuarios que el establecimiento forma parte del ecosistema.

**CUARTA: Condiciones Económicas y Exclusiones.** La Tarifa Preferencial otorgada por EL ALIADO a los usuarios de {{brand.name}} queda establecida según el detalle de servicios, tarifas preferenciales y exclusiones del Anexo de Tarifas del convenio (El Aliado mantiene autonomía total sobre sus márgenes).

**QUINTA: Autonomía, Confidencialidad y Duración.**

- **Independencia Jurídica:** Ambas partes mantienen su total independencia laboral, fiscal y administrativa. Este acuerdo no constituye una sociedad de hecho ni relación laboral.
- **Privacidad de Datos:** EL ALIADO no está obligado a compartir sus bases de datos internas con {{brand.name}}, ni {{brand.name}} revelará la información financiera de la plataforma a terceros.
- **Vigencia:** El presente acuerdo tiene una duración de un (1) año calendario a partir de su firma, renovable automáticamente. Cualquiera de las partes podrá rescindirlo notificando por escrito con treinta (30) días de anticipación, sin penalización alguna.

En señal de absoluta conformidad operativa y comercial, se firman dos (2) ejemplares de un mismo tenor y a un solo efecto, en la ciudad de {{org.city}}.$$,
       TRUE,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM terms_versions WHERE term_type = 'ALIADO');

-- ─── 3. terms_acceptances: acceptance evidence ──────────────────────────────
ALTER TABLE terms_acceptances
    ADD COLUMN ip_address    VARCHAR(45),
    ADD COLUMN user_agent    VARCHAR(400),
    ADD COLUMN channel       VARCHAR(20) NOT NULL DEFAULT 'PORTAL'
        CONSTRAINT chk_terms_acceptances_channel CHECK (channel IN ('PORTAL', 'SELF_SERVICE', 'ASSISTED', 'ADMIN')),
    ADD COLUMN membership_id BIGINT REFERENCES memberships (memberships_id);

CREATE INDEX idx_terms_acceptances_membership
    ON terms_acceptances (membership_id) WHERE membership_id IS NOT NULL;

COMMENT ON COLUMN terms_acceptances.ip_address IS 'IP del cliente al aceptar (IPv4 o IPv6)';
COMMENT ON COLUMN terms_acceptances.user_agent IS 'User-Agent del navegador al aceptar (truncado a 400)';
COMMENT ON COLUMN terms_acceptances.channel IS 'Canal de aceptación: PORTAL (modal en la app), SELF_SERVICE (autoafiliación pública), ASSISTED (cargado por un asesor), ADMIN';
COMMENT ON COLUMN terms_acceptances.membership_id IS 'Membresía para la que se aceptó (autoafiliación); NULL en aceptaciones desde el portal';
