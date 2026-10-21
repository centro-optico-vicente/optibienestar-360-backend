# 18 — Contratos (T&C): variables y aceptación por convenio

> **Relacionadas:** [`14-i18n.md`](14-i18n.md) (descripciones de variables en `messages_*.properties`), [`16-audit.md`](16-audit.md) (`DisplayFormatter`), [`17-brand-documents.md`](17-brand-documents.md) (placeholders `[… pendiente]`).
> **Frontend:** modal de aceptación y «Mis contratos» — [frontend spec 05 — Auth flow](https://github.com/centro-optico-vicente/optibienestar-360-frontend/blob/main/.ai/specs/05-auth-flow.md).
> **Estado:** ✅ implementado (V139, V181, V183, V186, V189).

## 1. Modelo

- `terms_versions`: plantilla Markdown versionada por tipo (`AFILIADO`, `ASESOR`, `ALIADO`) y, para ALIADO, por alcance (aliado, tipo de aliado, segmento — V183). La versión vigente de cada "stream" es la de mayor `valid_from <= now()`.
- `terms_acceptances`: quién aceptó qué y cuándo, con evidencia (IP, User-Agent, canal — V181).
  - **V189:** `ally_agreement_id` (convenio para el que se aceptó un ALIADO), `rendered_content` (el texto exacto que se mostró, con las variables resueltas) y `content_sha256` (hash hex del texto en UTF-8).
  - Unicidad con dos índices parciales: `(user_id, terms_versions_id) WHERE ally_agreement_id IS NULL` y `(user_id, terms_versions_id, ally_agreement_id) WHERE ally_agreement_id IS NOT NULL`.

## 2. Quién debe aceptar qué (`TermsAcceptanceService`)

| Tipo | Audiencia | Clave de "ya aceptado" |
|---|---|---|
| AFILIADO | Todo usuario | (usuario, versión) |
| ASESOR | Usuario con ficha de asesor activa y no de sistema | (usuario, versión) |
| ALIADO | Por cada convenio activo (`is_active`, `status = ACTIVE`) de cada aliado del usuario que tenga plantilla vigente | (usuario, versión, **convenio**) |

- **Cada convenio se acepta por separado**, con los datos de su aliado. Si dos aliados del usuario usan la misma plantilla, son dos pendientes (antes de V189 se aceptaba una sola vez, con los datos de un convenio cualquiera).
- Orden: AFILIADO, ASESOR y luego los convenios — primero el aliado principal del usuario (`ally_users.is_primary`), luego por nombre del aliado, luego por fecha de inicio del convenio.
- Toda aceptación (portal o autoafiliación) guarda `rendered_content` y `content_sha256`.

### Migración V189 (backfill)

Una aceptación ALIADO previa se vincula al convenio **solo si** el usuario tiene exactamente un convenio (cualquier estado, por cualquiera de sus filas en `ally_users`) con esa versión. Si no, queda en `NULL` y el usuario acepta cada convenio al entrar. Nunca se adivina para qué convenio se dio una aceptación. `rendered_content` queda `NULL` en filas previas: cuando hace falta el texto, se arma la versión con los datos actuales y se marca `contentReconstructed = true`.

## 3. Endpoints (`/v1/me/terms`, cualquier usuario autenticado)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/v1/me/terms/pending` | Pendientes. Cada ítem: `termsVersionUuid`, `termType`, `title`, `contentMarkdown` (ya renderizado), `versionNumber`, `allyUuid`, `allyName`, `allyAgreementUuid` y `previousAcceptance {termsVersionUuid, versionNumber, acceptedAt, renderedContent}` |
| POST | `/v1/me/terms/accept` | `{ "items": [{ "termsVersionUuid", "allyAgreementUuid"? }] }`. `termsVersionUuids` (lista de UUID) sigue aceptándose, **deprecado**: vale para AFILIADO/ASESOR o un ALIADO pendiente para un solo convenio |
| GET | `/v1/me/terms/acceptances` | Historial («Mis contratos»), lo más reciente primero: `termType`, `allyUuid`/`allyName`, `versionNumber`, `title`, `acceptedAt`, `channel`, `renderedContent`, `contentReconstructed` |

- `previousAcceptance`: la última aceptación del mismo convenio; si no hay, la del mismo aliado (convenio renovado); si no, una aceptación ALIADO previa a V189 sin vincular de la misma versión. Para AFILIADO/ASESOR, la última del mismo tipo. `null` en la primera aceptación.
- `versionNumber`: posición (desde 1) de la versión en su stream, por `valid_from`.
- Errores 422: `terms_acceptance.not_pending` (el ítem no está pendiente), `terms_acceptance.agreement_required` (UUID sin convenio pendiente para varios convenios). 400 si no viene ningún ítem (`terms_acceptance.nothing_selected`).

## 4. Variables de plantilla (`TermsVariables`)

Catálogo único: lo usan el renderer, la validación al guardar (422 `terms_version.unknown_variables` si la plantilla usa una variable que no es de su tipo) y `GET /v1/admin/terms/variables?type=` (`key`, `description`, `kind`). Un valor faltante se muestra como `[… pendiente]`.

| Variable | Tipo | `kind` | Valor |
|---|---|---|---|
| `brand.name` | todos | TEXT | OPTIBIENESTAR 360 |
| `org.legalName`, `org.taxId`, `org.registryState`, `org.city`, `org.serviceArea` | todos | TEXT | Organización |
| `member.fullName`, `member.document` | AFILIADO | TEXT | Titular |
| `member.birthDate`, `membership.activatedAt` | AFILIADO | DATE | `dd-MM-yyyy` |
| `membership.planName` | AFILIADO | TEXT | Plan de la membresía activa |
| `beneficiaries.count` | AFILIADO | NUMBER | Beneficiarios activos |
| `plan.monthlyFee`, `plan.inscriptionFee`, `plan.extraBeneficiaryInscriptionFee` | AFILIADO | MONEY | Montos del plan de la membresía activa |
| `plan.includedBeneficiaries` | AFILIADO | NUMBER | Beneficiarios incluidos |
| `plan.maxBeneficiaries` | AFILIADO | NUMBER | Máximo; «sin límite» si el plan no tiene tope |
| `advisor.code`, `advisor.fullName`, `advisor.document`, `advisor.zone`, `advisor.rank` | ASESOR | TEXT | Ficha del asesor |
| `advisor.joinedAt` | ASESOR | DATE | Fecha de ingreso |
| `ally.name`, `ally.taxId`, `ally.representativeName`, `ally.representativeDocument`, `ally.fileNumber` | ALIADO | TEXT | Aliado del convenio |
| `agreement.startDate` | ALIADO | DATE | Inicio del convenio |
| `agreement.noticeDays` | ALIADO | NUMBER | Días de preaviso |
| `agreement.autoRenew` | ALIADO | BOOLEAN | Sí / No |
| `agreement.addendum` | ALIADO | TEXT | Condiciones particulares |

### Montos en contratos (MONEY)

- Se escriben **en su moneda**, con `DisplayFormatter.money(valor, moneda, es-VE)` — p. ej. `US$ 25,00`.
- Si la moneda no es la oficial de la organización, se agrega «o su equivalente en bolívares a la tasa oficial del BCV vigente el día del pago» (texto fijo cuando la oficial es VES; con otra moneda oficial se usa su nombre del maestro `currencies`).
- **Nunca se escribe una cifra convertida**: el contrato tiene que seguir valiendo con el tiempo y el pago se hace a la tasa del día.
- Las seeds no se tocaron: para usar los montos, el admin publica una versión nueva.
- NUMBER usa `DisplayFormatter.number` (separadores es-VE).
- Pendiente (backlog): los porcentajes por servicio del convenio.
