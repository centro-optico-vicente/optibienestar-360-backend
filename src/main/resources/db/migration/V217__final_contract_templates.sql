-- ====================================================
-- V217__final_contract_templates.sql
--
-- Final contract texts delivered by the client and reviewed by legal on
-- 2026-10-31 (hub ADR 0031 §12, plan 2026-10-31 "professional ally" §7).
--
-- 1. organizations gains the institutional contact the final .docx print in
--    their footer ({{org.address}}, {{org.email}}, {{org.phones}}). The
--    existing organization is filled only where the value is still NULL.
--
-- 2. New terms_versions, valid from now, with the hub seeds
--    (.ai/notes/2026-10-17-contract-seeds/) as content:
--      - AFILIADO and ASESOR: a new version of their single stream;
--      - ALIADO segment = B2B (Convenio Corporativo de Integración Comercial)
--        and ALIADO segment = B2P (Convenio Corporativo de Integración
--        Profesional): the first version of two new scoped streams (V183).
--    Content = the seed text without its HTML comments and without the "#"
--    heading (the title lives in terms_versions.title), wrapped like V186:
--    an identification block ("anverso", built from the comment notes of the
--    seed) before the body and a signature block plus the institutional
--    footer after it. Every {{...}} is a variable of TermsVariables for that
--    type (TermsVariablesTest checks this file). The seed comments name a few
--    values the system does not hold yet (the affiliate's advisor code, extra
--    beneficiary slots, the approving manager); they are left out of the
--    blocks, not invented.
--    is_public / is_mandatory are copied from the current unscoped version of
--    the type (for ALIADO, the general template), defaulting to the V181/V192
--    values when the type has none.
--    Idempotent: a stream that already holds this exact text is skipped.
--
-- 3. The general (unscoped) ALIADO template stops being offered to new
--    allies by code, not by data: TermsVersionScopes.currentApplicableTo
--    drops the general template once a current template of the ally's
--    segment exists — and every ally has a segment (V211, NOT NULL), so after
--    this migration no ally is offered the general one for a new agreement.
--    Its rows are left untouched: agreements already signed under it keep
--    their link and their accepted text (TermsVersionService: a vigente version is
--    immutable).
--
-- Accepters of the previous AFILIADO / ASESOR versions get the new version
-- through the existing "new version pending" flow.
-- ====================================================

SET search_path TO app, public;

-- ─── 1. organizations: institutional contact ────────────────────────────────
ALTER TABLE organizations
    ADD COLUMN address TEXT,
    ADD COLUMN email   CITEXT,
    ADD COLUMN phones  VARCHAR(100);

COMMENT ON COLUMN organizations.address IS 'Dirección de la organización ({{org.address}}, pie institucional de los contratos)';
COMMENT ON COLUMN organizations.email IS 'Correo de contacto de la organización ({{org.email}})';
COMMENT ON COLUMN organizations.phones IS 'Teléfonos de contacto tal como se imprimen en los contratos ({{org.phones}})';

UPDATE organizations
SET address = COALESCE(address, 'Av. 30 con Calle 21, C.C. Ciudad Cristal, Planta Baja, Local N° 23, Acarigua, Edo. Portuguesa'),
    email   = COALESCE(email, 'planoptibienestar360@gmail.com'),
    phones  = COALESCE(phones, '(+58)414-5738530, (+58)424-5984590')
WHERE address IS NULL
   OR email IS NULL
   OR phones IS NULL;

-- ─── 2. terms_versions: final texts ─────────────────────────────────────────
INSERT INTO terms_versions (term_type, segment, title, content_markdown, is_public, is_mandatory, valid_from)
SELECT t.term_type,
       t.segment,
       t.title,
       t.content,
       COALESCE(cur.is_public, t.default_public),
       COALESCE(cur.is_mandatory, t.default_mandatory),
       NOW()
FROM (VALUES
    ('AFILIADO', NULL::VARCHAR, 'Acuerdo de Membresía y Blindaje Familiar', $afiliado$**I. REGISTRO OPERATIVO**

- **Fecha de activación:** {{membership.activatedAt}}
- **Plan seleccionado:** {{membership.planName}} · Base: titular + {{plan.includedBeneficiaries}}
- **Modalidad de pago:** {{membership.inscriptionPaymentMethod}}
- **Beneficiarios registrados:** {{beneficiaries.count}}

**II. DATOS DEL AFILIADO TITULAR**

- **Nombre completo:** {{member.fullName}}
- **Cédula de identidad:** {{member.document}}
- **Fecha de nacimiento:** {{member.birthDate}}

---

Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, con RIF **{{org.taxId}}**; y por la otra parte, la persona natural plenamente identificada en el anverso de este documento (en lo sucesivo "**EL AFILIADO TITULAR**"), convienen en celebrar el presente acuerdo de afiliación al ecosistema de salud y consumo, regido por las siguientes cláusulas operativas:

**PRIMERA: Objeto del Ecosistema (El Blindaje Económico).** {{brand.name}} otorga a EL AFILIADO TITULAR (y a sus dependientes registrados, si aplica) el derecho de acceso a una red exclusiva de comercios, clínicas, farmacias y especialistas médicos (Aliados B2B/B2P) en el {{org.serviceArea}}. Al presentar su Credencial Digital, el afiliado gozará del beneficio de una Tarifa Preferencial (descuento directo) sobre los bienes y servicios estipulados previamente entre la corporación y los aliados.

**SEGUNDA: Naturaleza Legal del Servicio (Importante).** {{brand.name}} opera estrictamente como una plataforma tecnológica y comercial de intermediación y gestión de descuentos. Este servicio NO es, ni sustituye, a una póliza de seguros de salud, plan de medicina prepagada o fondo de financiamiento regulado por la Superintendencia de la Actividad Aseguradora (SUDEASEG). El ecosistema no asume el pago de siniestros, deducibles ni hospitalizaciones; su función es garantizar el acceso a tarifas reducidas para proteger el presupuesto familiar.

**TERCERA: Uso de normativas.**

- **Validación Inmediata:** EL AFILIADO deberá presentar su Credencial Digital activa desde su dispositivo móvil, acompañada de su Cédula de Identidad, antes de la facturación en el comercio aliado.
- **Intransferibilidad (Prevención de Fraude):** La Credencial Digital es de uso estrictamente personal. El préstamo, captura de pantalla o intento de uso por parte de terceros no registrados en el núcleo familiar del titular, causará la anulación inmediata y definitiva del portafolio sin derecho a reembolso.
- **Pago Directo:** EL AFILIADO pagará el monto total del producto o servicio (ya con la Tarifa Preferencial aplicada) directamente en la caja del Aliado Comercial.

**CUARTA: Responsabilidad sobre Productos y Servicios Médicos.** Dado que {{brand.name}} es el conector tecnológico, la responsabilidad absoluta sobre la calidad de los productos vendidos, las garantías, la mala praxis médica o la atención al cliente dentro de las instalaciones, recae de manera exclusiva sobre el Aliado Comercial o Profesional (Comercio/Clínica) que presta el servicio final. {{brand.name}} audita constantemente a sus aliados para mantener estándares de excelencia, pero no es solidariamente responsable por contingencias operativas o médicas de terceros.

**QUINTA: Inversión, Vigencia y Renovación.**

- **Activación:** El acceso al ecosistema se activa en el momento en que EL AFILIADO TITULAR cancela el monto correspondiente a la {{membership.activationFeeLabel}}.
- **Vigencia:** Este acuerdo tiene una validez de un (1) año calendario a partir de su emisión.
- **Modificaciones de la Red:** La plataforma es un ecosistema vivo. {{brand.name}} se reserva el derecho de agregar nuevos aliados, retirar aquellos que no cumplan con la calidad exigida, o actualizar los porcentajes de las tarifas preferenciales en beneficio de la red, lo cual será notificado en el catálogo digital de la plataforma.

Al procesar su pago y generar su Credencial Digital, EL AFILIADO TITULAR declara haber leído, comprendido y aceptado la naturaleza y normas de uso del ecosistema {{brand.name}}, sellando su integración a la red comercial más exclusiva de la región.

---

**Por {{brand.name}}:** Aprobación del sistema automático

**Por EL AFILIADO TITULAR:** {{member.fullName}} · C.I. {{member.document}} · Fecha de activación: {{membership.activatedAt}}

{{org.address}} · Email: {{org.email}} · Teléfonos: {{org.phones}}$afiliado$, TRUE, TRUE),
    ('ASESOR', NULL::VARCHAR, 'Contrato de Mandato y Representación Comercial Independiente', $asesor$**Nº de expediente:** {{advisor.code}}

**I. REGISTRO**

- **Fecha de ingreso:** {{advisor.joinedAt}}
- **Código del asesor comercial:** {{advisor.code}}
- **Región / zona asignada:** {{advisor.zone}}
- **Rango:** {{advisor.rank}}

**II. IDENTIDAD PERSONAL**

- **Nombre completo:** {{advisor.fullName}}
- **Cédula de identidad:** {{advisor.document}}

---

Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, RIF **{{org.taxId}}**; y por la otra parte, el ciudadano(a) plenamente identificado(a) en el anverso de este documento (en lo sucesivo "**EL ASESOR COMERCIAL**"), convienen en celebrar el presente contrato mercantil bajo las siguientes cláusulas:

**PRIMERA: Objeto del Contrato (Expansión del Ecosistema).** {{brand.name}} contrata los servicios profesionales e independientes de EL ASESOR COMERCIAL para ejecutar la promoción, captación, negociación y cierre de afiliaciones al sistema {{brand.name}}. Su gestión abarcará tanto el portafolio corporativo (Captación de Clínicas y Comercios Aliados - B2B/B2P) como el portafolio de consumo (Afiliación de Familias - B2C) dentro del territorio asignado, operando siempre bajo los principios de Fricción Cero y excelencia ejecutiva.

**SEGUNDA: Naturaleza Jurídica y Autonomía.** El presente es un contrato de naturaleza estrictamente mercantil y civil. EL ASESOR COMERCIAL actuará con total autonomía de tiempo, ruta y métodos (respetando la ética corporativa), utilizando sus propios recursos, sin estar sujeto a jornada laboral obligatoria ni subordinación jurídica continua. En consecuencia, este acuerdo no genera relación de dependencia laboral, excluyendo el pago de salario fijo, prestaciones sociales u otros pasivos derivados de la Ley Orgánica del Trabajo, Trabajadoras y Trabajadores. La retribución será 100% variable y basada en el rendimiento.

**TERCERA: Obligaciones del Asesor Comercial.** Para mantener el estándar de la marca, EL ASESOR COMERCIAL se obliga a:

- **Ejecución Táctica:** Utilizar los métodos de abordaje y descubrimiento aprobados por {{brand.name}}, transmitiendo con exactitud las políticas de Integración a Riesgo Cero y Blindaje Familiar.
- **Integridad de la Información:** Recaudar de manera veraz, legible y completa los datos de los comercios y usuarios finales a través de las Fichas Técnicas Oficiales, ingresándolos de inmediato a la plataforma para garantizar la operatividad de los afiliados.
- **Protección de Marca:** No alterar el logotipo, nombre, isotipo ni modificar las tarifas preferenciales establecidas por la Dirección de Expansión.

**CUARTA: Sistema de Rendimientos y Liquidación.** Como contraprestación única por sus servicios, EL ASESOR COMERCIAL percibirá honorarios mercantiles calculados exclusivamente sobre los cierres efectivos y cobrados, según el sistema de jerarquía financiera de {{brand.name}}. Los dividendos se liquidarán a la cuenta bancaria especificada en el anverso, bajo los siguientes conceptos (detallados en la Tabla de Comisiones Anexa):

- **Dividendo de Activación Inicial (DAI):** cada nueva familia afiliada o Empresa integrado de forma efectiva.
- **Tasa de Capitalización Acelerada (TCA):** Bonificaciones por alcance de volumen semanal/mensual.
- **Margen de Tracción Superior (MTS):** Rendimientos por captación de cuentas corporativas de alto impacto.
- **Rendimiento de Dominio 360:** Participación residual según el nivel de jerarquía alcanzado dentro de la Fuerza de Expansión.

**QUINTA: Confidencialidad y Propiedad Intelectual.** Toda la información operativa, manuales de objeciones, guiones, listado de Comercios Aliados, Clínicas, Profesionales, entre otros (B2B/B2P), estrategias comerciales, software y bases de datos a los que tenga acceso EL ASESOR COMERCIAL son propiedad exclusiva e intransferible de {{brand.name}}. Queda estrictamente prohibida su divulgación, copia o uso para fines ajenos a este contrato, incluso después de la terminación del mismo, sujeto a acciones legales por daños y perjuicios.

**SEXTA: Vigencia y Resolución.** El presente contrato entra en vigencia a partir de su firma y tendrá una duración de un (1) año. Cualquiera de las partes podrá darlo por terminado sin expresión de causa, mediante simple notificación a la otra parte, procediéndose a la liquidación de los rendimientos generados hasta el día efectivo del cese. El incumplimiento de las cláusulas éticas o el fraude en las afiliaciones causará la resolución inmediata del contrato y el bloqueo permanente del ecosistema.

En señal de plena conformidad, habiendo leído y aceptado los términos que rigen a la Fuerza de Expansión, firman en la ciudad de {{org.city}}.

---

**Por {{brand.name}}:** Gerente autorizado (nombre, C.I. y firma)

**Por EL ASESOR COMERCIAL:** {{advisor.fullName}} · C.I. {{advisor.document}}

{{org.address}} · Email: {{org.email}} · Teléfonos: {{org.phones}}$asesor$, FALSE, FALSE),
    ('ALIADO', 'B2B'::VARCHAR, 'Convenio Corporativo de Integración Comercial', $aliado_b2b$**Nº de expediente:** {{ally.fileNumber}}

**I. REGISTRO OPERATIVO**

- **Región / zona comercial:** {{ally.commercialZone}}
- **Categoría:** {{ally.typeNames}}

**II. IDENTIDAD JURÍDICA**

- **Razón social:** {{ally.name}}
- **Nombre comercial:** {{ally.tradeName}}
- **RIF:** {{ally.taxId}}
- **Año de fundación:** {{ally.foundedYear}}
- **Dirección fiscal:** {{ally.address}}
- **Ciudad y estado:** {{ally.cityState}}
- **Teléfono local / sede:** {{ally.phone}}
- **Redes sociales:** {{ally.socialLinks}}

**III. REPRESENTACIÓN LEGAL**

- **Representante:** {{ally.representativeName}} · C.I./RIF {{ally.representativeDocument}}
- **Cargo:** {{ally.representativePosition}}

**IV. ESTRUCTURA OPERATIVA Y CONVENIO**

- **Nº de cajas registradoras:** {{ally.cashRegistersCount}}
- **Nº de sucursales:** {{ally.branchesCount}}
- **¿Maneja sistema POS?:** {{ally.hasPos}}
- **Horario de atención:** {{ally.openingHours}}
- **Inicio del convenio:** {{agreement.startDate}}

---

Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, con RIF **{{org.taxId}}**; y por la otra parte, el **ALIADO COMERCIAL** (en lo sucesivo "**EL ALIADO**"), cuyos datos jurídicos y operativos se detallan plenamente en el anverso de este documento, convienen en celebrar el presente acuerdo bajo las siguientes cláusulas:

**PRIMERA: Objeto del Convenio (Simbiosis Comercial)** El presente documento tiene como objetivo integrar a EL ALIADO a la red física y digital de {{brand.name}} en el estado {{org.registryState}}. {{brand.name}} se compromete a direccionar tráfico cautivo de familias afiliadas hacia las instalaciones de EL ALIADO, y este último se compromete a otorgar beneficios económicos (Tarifa Preferencial) de manera directa y exclusiva a dichos usuarios, bajo un modelo de operación de riesgo financiero cero.

**SEGUNDA: Obligaciones de {{brand.name}}:** Nuestra Empresa garantiza a EL ALIADO el cumplimiento de los siguientes parámetros operativos:

1. **Redireccionamiento de Tráfico:** Promover activamente los productos y servicios de EL ALIADO a través del catálogo digital, plataforma web y fuerza de ventas.
2. **Integración a Riesgo Cero:** {{brand.name}} **no cobrará** a EL ALIADO cuotas de inscripción, mensualidades, ni comisiones por ventas. El modelo es de impacto directo al consumidor.
3. **Soporte Tecnológico:** Garantizar que las Credenciales Digitales de los usuarios cuenten con los estándares de seguridad necesarios para una validación inmediata y segura en la caja de EL ALIADO.

**TERCERA: Obligaciones del Aliado (Operatividad de Fricción Cero):** Para garantizar una experiencia fluida e instantánea al consumidor, EL ALIADO se compromete a:

1. **Validación Inmediata:** Reconocer la Credencial Digital (activa y vigente) presentada por el afiliado desde su dispositivo móvil, sin exigir papeleos, claves de aprobación telefónica ni trámites burocráticos adicionales.
2. **Aplicación de la Tarifa Preferencial:** Aplicar el porcentaje de descuento o tarifa especial pactada en este acuerdo directamente sobre el monto total de la factura al momento del pago.
3. **Exhibición de Marca:** Colocar el identificador visual de {{brand.name}} en un lugar visible de sus cajas o recepción, indicando a los usuarios que el establecimiento forma parte del ecosistema.

**CUARTA: Condiciones Económicas y Exclusiones** La Tarifa Preferencial otorgada por EL ALIADO a los usuarios de {{brand.name}} queda establecida según el siguiente esquema (El Aliado mantiene autonomía total sobre sus márgenes):

| Servicio / Departamento Aplicable | Tarifa Preferencial (Descuento %) | Exclusiones Operativas (No aplica en) |
|---|---|---|
{{#agreement.rates}}
| **B2B:** {{serviceName}} | {{discountLabel}} | {{exclusions}} |
{{/agreement.rates}}

**QUINTA: Autonomía, Confidencialidad y Duración:**

1. **Independencia Jurídica:** Ambas partes mantienen su total independencia laboral, fiscal y administrativa. Este acuerdo no constituye una sociedad de hecho ni relación laboral.
2. **Privacidad de Datos:** EL ALIADO no está obligado a compartir sus bases de datos internas con {{brand.name}}, ni {{brand.name}} revelará la información financiera de la plataforma a terceros.
3. **Vigencia:** El presente acuerdo tiene una duración de un (1) año calendario a partir de su firma, renovable automáticamente. Cualquiera de las partes podrá rescindirlo notificando por escrito con treinta (30) días de anticipación, sin penalización alguna.

En señal de absoluta conformidad operativa y comercial, se firman dos (2) ejemplares de un mismo tenor y a un solo efecto, en la ciudad de {{org.city}}.

---

**Por {{brand.name}} ({{org.legalName}}):** Asesor comercial {{ally.advisorFullName}} · Código {{ally.advisorCode}}

**Por EL ALIADO COMERCIAL:** {{ally.representativeName}} · C.I./RIF {{ally.representativeDocument}}

{{org.address}} · Email: {{org.email}} · Teléfonos: {{org.phones}}$aliado_b2b$, TRUE, FALSE),
    ('ALIADO', 'B2P'::VARCHAR, 'Convenio Corporativo de Integración Profesional', $aliado_b2p$**Nº de Expediente:** {{ally.fileNumber}}

**I. REGISTRO OPERATIVO**

- **Clasificación médica:** {{professional.specialty}}
- **Sub-especialidad:** {{professional.subSpecialty}}

**II. IDENTIDAD DEL PROFESIONAL**

- **Nombres:** {{professional.firstNames}}
- **Apellidos:** {{professional.lastNames}}
- **Cédula de identidad:** {{professional.document}}
- **Fecha de nacimiento:** {{professional.birthDate}}
- **Nº M.P.P.S.:** {{professional.licenses.MPPS}}
- **Nº Colegio de Médicos:** {{professional.licenses.COLEGIO_MEDICOS}}
- **Teléfono celular:** {{professional.phone}}
- **Correo:** {{professional.email}}

**III. COORDENADAS DE CONSULTA**

- **Centro clínico principal:** {{professional.hostName}}
- **Número de consultorio:** {{professional.officeNumber}}
- **Dirección de la clínica:** {{ally.address}}
- **Ciudad y estado:** {{ally.cityState}}
- **Días de consulta / horario de atención:** {{ally.openingHours}}
- **Teléfono para citas:** {{professional.appointmentPhone}}
- **Asistente:** {{professional.assistantName}}
- **Inicio del convenio:** {{agreement.startDate}}

**IV. ACUERDO ARANCELARIO**

| Servicio | Precio normal al público | Tarifa Preferencial 360º |
|---|---|---|
{{#agreement.rates}}
| {{serviceName}} | {{priceLabel}} | {{finalPriceLabel}} |
{{/agreement.rates}}

---

Entre **{{org.legalName}}** (en lo sucesivo "**{{brand.name}}**"), inscrita por ante el Registro Mercantil del estado {{org.registryState}}, con RIF **{{org.taxId}}**; y por la otra parte, el **ALIADO PROFESIONAL** (en lo sucesivo "**EL ALIADO**"), el ciudadano(a) plenamente identificado(a) en el anverso de este documento, convienen en celebrar el presente acuerdo bajo las siguientes cláusulas:

**PRIMERA: Objeto del Convenio (Simbiosis Comercial)** El presente documento tiene como objetivo integrar a EL ALIADO a la red física y digital de {{brand.name}} en el estado {{org.registryState}}. {{brand.name}} se compromete a direccionar tráfico cautivo de familias afiliadas hacia las instalaciones de EL ALIADO, y este último se compromete a otorgar beneficios económicos (Tarifa Preferencial) de manera directa y exclusiva a dichos usuarios, bajo un modelo de operación de riesgo financiero cero.

**SEGUNDA: Obligaciones de {{brand.name}}:** Nuestra Empresa garantiza a EL ALIADO el cumplimiento de los siguientes parámetros operativos:

1. **Redireccionamiento de Tráfico:** Promover activamente los productos y servicios de EL ALIADO a través del catálogo digital, plataforma web y fuerza de ventas.
2. **Integración a Riesgo Cero:** {{brand.name}} **no cobrará** a EL ALIADO cuotas de inscripción, mensualidades, ni comisiones por ventas. El modelo es de impacto directo al consumidor.
3. **Soporte Tecnológico:** Garantizar que las Credenciales Digitales de los usuarios cuenten con los estándares de seguridad necesarios para una validación inmediata y segura en la caja de EL ALIADO.

**TERCERA: Obligaciones del Aliado (Operatividad de Fricción Cero):** Para garantizar una experiencia fluida e instantánea al consumidor, EL ALIADO se compromete a:

1. **Validación Inmediata:** Reconocer la Credencial Digital (activa y vigente) presentada por el afiliado desde su dispositivo móvil, sin exigir papeleos, claves de aprobación telefónica ni trámites burocráticos adicionales.
2. **Aplicación de la Tarifa Preferencial:** Aplicar el porcentaje de descuento o tarifa especial pactada en este acuerdo directamente sobre el monto total de la factura al momento del pago.
3. **Exhibición de Marca:** Colocar el identificador visual de {{brand.name}} en un lugar visible de sus cajas o recepción, indicando a los usuarios que el establecimiento forma parte del ecosistema.

**CUARTA: Condiciones Económicas y Exclusiones** La Tarifa Preferencial otorgada por EL ALIADO a los usuarios de {{brand.name}} queda establecida según el siguiente esquema (El Aliado mantiene autonomía total sobre sus márgenes):

| Servicio / Departamento Aplicable | Tarifa Preferencial (Descuento %) | Exclusiones Operativas (No aplica en) |
|---|---|---|
{{#agreement.rates}}
| **B2P:** {{serviceName}} | {{discountLabel}} | {{exclusions}} |
{{/agreement.rates}}

**QUINTA: Autonomía, Confidencialidad y Duración:**

1. **Independencia Jurídica:** Ambas partes mantienen su total independencia laboral, fiscal y administrativa. Este acuerdo no constituye una sociedad de hecho ni relación laboral.
2. **Privacidad de Datos:** EL ALIADO no está obligado a compartir sus bases de datos internas con {{brand.name}}, ni {{brand.name}} revelará la información financiera de la plataforma a terceros.
3. **Vigencia:** El presente acuerdo tiene una duración de un (1) año calendario a partir de su firma, renovable automáticamente. Cualquiera de las partes podrá rescindirlo notificando por escrito con treinta (30) días de anticipación, sin penalización alguna.

En señal de absoluta conformidad operativa y comercial, se firman dos (2) ejemplares de un mismo tenor y a un solo efecto, en la ciudad de {{org.city}}.

---

**Por {{brand.name}} ({{org.legalName}}):** Asesor comercial {{ally.advisorFullName}} · Código {{ally.advisorCode}}

**Por EL ALIADO PROFESIONAL:** {{professional.fullName}} · C.I. {{professional.document}}

{{org.address}} · Email: {{org.email}} · Teléfonos: {{org.phones}}$aliado_b2p$, TRUE, FALSE)
) AS t(term_type, segment, title, content, default_public, default_mandatory)
LEFT JOIN LATERAL (
    SELECT tv.is_public, tv.is_mandatory
    FROM terms_versions tv
    WHERE tv.term_type = t.term_type
      AND tv.ally_type_id IS NULL
      AND tv.segment IS NULL
      AND tv.ally_id IS NULL
      AND tv.valid_from <= NOW()
    ORDER BY tv.valid_from DESC, tv.terms_versions_id DESC
    LIMIT 1
) cur ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM terms_versions x
    WHERE x.term_type = t.term_type
      AND x.ally_type_id IS NULL
      AND x.ally_id IS NULL
      AND x.segment IS NOT DISTINCT FROM t.segment
      AND x.content_markdown = t.content
);
