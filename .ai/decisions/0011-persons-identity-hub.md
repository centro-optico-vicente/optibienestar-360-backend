# ADR 0011 — Persons como hub central de identidad

**Estado:** Aceptado
**Fecha:** 2026-06-05
**Decisores:** equipo OptiBienestar 360

## Contexto

Hasta esta ADR, los datos personales (nombre completo, cédula, RIF, teléfono, email, locale, dirección, fecha de nacimiento, género, estado civil) vivían **embebidos en cada tabla de rol**:

- `users` (V5) tenía `full_name`, `document_type`, `document_number`, `phone`, `locale`.
- `members` (planeada V15) iba a tener los mismos campos otra vez.
- `beneficiaries` (planeada V16) idem.
- Futuros `promoters` y `ally_users` también.

Esto generaba:

1. **Duplicación masiva** cuando un mismo humano aparece en múltiples roles (el admin que también se afilia → su nombre/cédula vive en `users` Y en `members`; un promotor que es titular → en `users` Y `promoters` Y `members`; un beneficiario que tiene cuenta de login → en `users` Y `beneficiaries`).
2. **Imposibilidad de responder "¿quiénes son TODAS las personas en el sistema?"** sin `UNION` entre N tablas y deduplicar por cédula a posteriori.
3. **Inconsistencias asegurables**: si Juan cambia su teléfono, ¿se actualiza en `users`, `members`, ambos? La verdad podría divergir entre roles.
4. **GDPR/LOPDP awkward**: "olvidar a Juan" requiere `UPDATE` en N tablas; cada copia es un vector de fuga.
5. **Cédula única no enforzable globalmente** — cada tabla tendría su propio `UNIQUE(document_type, document_number)`, pero nada impide que `members.documento` y `users.documento` coincidan con datos distintos.

El sistema previo del cliente (`proyecto-iv-mh`) ya tenía precedente del patrón: `tglo_PERSONA_m` central referenciada por `tseg_USUARIO_m` y `tinst_TRABAJADOR`. No manejaba N-roles, pero la idea de "tabla persona como hub" no es ajena al ecosistema.

## Decisión

Introducir la tabla `persons` como **single source of truth** de identidad civil + contacto de toda persona en el sistema. Cada tabla de rol referencia `persons` por FK en vez de duplicar campos.

### Estructura de `persons` (ver `V15__persons.sql`)

- **Nombres partidos LATAM**: `first_name` + `middle_name` + `last_name` + `second_last_name`. Columna `full_name` derivada como `GENERATED ALWAYS AS ... STORED` para queries simples y full-text search.
- **Documento personal**: cédula VE (`document_type` V/E + `document_number`), UNIQUE compuesto.
- **Documento tributario**: RIF VE separado (`tax_document_type` J/V/E/G/P + `tax_document_number`), UNIQUE parcial cuando ambos presentes.
- **Demografía**: `birth_date`, `gender_id` FK, `marital_status_id` FK.
- **Contacto**: `phone`, `email` (CITEXT), `locale` (BCP47).
- **Dirección**: `address` (text) + `city_id` FK opcional al catálogo V8.
- **Audit + soft-delete** vía BaseEntity (`active`, `created_at/by`, `updated_at/by`).

### Relación con `users` (refactor en `V16__refactor_users_persons.sql`)

- `users` mantiene **solo** auth/security: `email` (login), `password_hash`, `status`, `active`, `failed_login_attempts`, `locked_until`, `password_reset_token`, `last_login_at`, `password_never_expires`.
- `users.person_id` BIGINT NOT NULL UNIQUE FK → `persons` (relación 1:1).
- Backfill por cédula: cada `users` row existente genera su correspondiente `persons` row; los nombres single-word de los seed (`System`, `Administrador`) se splittean con un fallback explícito hardcoded.
- Columnas dropped de `users`: `full_name`, `document_type`, `document_number`, `phone`, `locale`.

**Distinción email login vs email contacto:** se mantienen DOS columnas — `users.email` es la credencial de login, `persons.email` es el email de contacto. Normalmente coinciden; la separación permite casos como "uso mi email personal para login, recibo recibos en el email corporativo" sin acoplamiento.

### Otras tablas afectadas (planeadas, no implementadas aún)

- `members.person_id` BIGINT NOT NULL UNIQUE FK → `persons` (1:1).
- `beneficiaries.person_id` BIGINT NOT NULL FK → `persons` (N:1 — una persona puede ser beneficiary de múltiples members).
- `promoters` y `ally_users` ya estaban planeadas como wrappers de `users` (referencian users vía FK) — heredan persons automáticamente vía `user.person`.

### Renumeración de migraciones planeadas

Insertar V15 + V16 bumpea todas las planeadas anteriores en +2:

| Antes | Después |
|---|---|
| V15 members | V17 members |
| V16 beneficiaries | V18 beneficiaries |
| V17 medical_records | V19 medical_records |
| V18 memberships | V20 memberships |
| V19 payments | V21 payments |
| V20 benefit_usages | V22 benefit_usages |
| V21 promoters | V23 promoters |
| V22 commissions | V24 commissions |
| V23 referrals | V25 referrals |
| V24 notifications | V26 notifications |
| V25 digital_cards_view | V27 digital_cards_view |

Actualizado en todos los checklists `vertical-N-*.md`, `fase-2-*.md` y `02-database.md`.

## Servicio `PersonService.findOrCreate`

Patrón obligatorio para alta de cualquier rol nuevo (User, Member, Beneficiary, Promoter, AllyUser): construir un `Person` seed con los datos del request, llamar `personService.findOrCreate(seed)`, recibir la row existente si la cédula ya está en el sistema o una nueva si no. Esto garantiza que **un mismo humano nunca se duplica entre roles**.

```java
Person seed = new Person();
seed.setFirstName(...);
seed.setLastName(...);
seed.setDocumentType(...);
seed.setDocumentNumber(...);
Person person = personService.findOrCreate(seed);

User user = new User();
user.setPerson(person);
// ...
```

## Breaking changes en la API REST

DTOs de admin actualizados (breaking change en v2):

- `AdminCreateUserRequest`: `fullName` (string única) → reemplazado por `firstName` + `middleName` + `lastName` + `secondLastName` (4 campos atómicos). `documentType` + `documentNumber` ahora **obligatorios**. Nuevos: `taxDocumentType` + `taxDocumentNumber` (opcionales).
- `AdminUpdateUserRequest`: mismo cambio. PATCH-style — campos null se ignoran.
- `UserDto` (response): expone los 4 name parts + el `fullName` derivado para que el frontend use el formato que necesite.

El frontend admin deberá adaptar sus formularios.

## Trade-offs aceptados

| Costo | Mitigación |
|---|---|
| Queries de Member/Beneficiary requieren JOIN a persons | `@EntityGraph` selectivo + índice GIN unaccent sobre `persons.full_name` para búsqueda |
| Migración invasiva (drop columns en users) | Hecho en fase dev sin datos prod; backfill autocontenido en V16 |
| 2 columnas email (users + persons) | Conceptualmente distintas; documentado |
| Lazy-loading de Person desde User puede generar N+1 | `@OneToOne FetchType.LAZY` + fetch join explícito en queries de listado |

## Alternativas descartadas

- **No hacer nada (mantener duplicación):** descartada — el costo crece linealmente con cada nuevo rol; la migración futura será más cara.
- **Scope reducido (solo members + beneficiaries, dejar users con datos embebidos):** descartada — preserva la duplicación cuando un admin se afilia o un promotor es titular.
- **Tablas separadas `person_contacts` / `person_addresses`:** descartada por ahora — un solo phone/email/address inline en persons es suficiente para fase 1; refactor a tablas separadas solo si surge necesidad de múltiples teléfonos/direcciones por persona.

## Consecuencias

- Las migraciones V11-V14 mantienen su numeración y semántica.
- El **JWT** sigue usando `user.uuid` como subject; Person no entra al token. La invalidación por epoch (ADR token-staleness) sigue funcionando igual.
- `MembershipStatusService` (vertical-5) y demás services que computen estado por persona pueden hacer queries directas a `persons` sin pasar por roles.
- Cualquier futura "ficha 360 del afiliado" sale de un JOIN sobre `persons + members + beneficiaries + commissions + payments` sin deduplicación.
