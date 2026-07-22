# ADR 0009 — Convenciones de código (idioma, naming, comentarios)

> **Espejo local** del [ADR 0009 del hub](../../../centro-optico-vicente/.ai/decisions/0009-code-conventions.md).
> Se mantiene esta copia dentro del repo backend para que cualquier sesión IA que solo tenga el backend cargado (sin el hub disponible) tenga acceso directo a la convención.
> Si hay divergencia, el hub es la fuente canónica — actualizar este espejo desde allí.

**Estado:** Aceptado
**Fecha:** 2026-05-18
**Decisores:** equipo OptiBienestar 360

## Contexto

El equipo trabaja en español pero la mayoría de la documentación técnica del mundo del software, librerías, herramientas y stackoverflow están en inglés. Necesitamos una convención clara sobre cuándo usar cada idioma y cómo nombrar identificadores para que el código sea legible para devs hispanohablantes pero también para futuros colaboradores anglos o herramientas IA entrenadas predominantemente en inglés.

## Decisión

### Idioma por contexto

| Contexto | Idioma | Razón |
|---|---|---|
| **Código fuente** (variables, funciones, clases, packages) | **Inglés** | Mejor compatibilidad con frameworks/libs/skills, mejor entendimiento por IA. |
| **Comentarios en código** | **Inglés** | Idem. Si hay un caso muy local (regla específica de Venezuela), permitido en español. |
| **Logs y errores técnicos** | **Inglés** | Idem. Plus: stack traces en inglés son universalmente buscables. |
| **Mensajes de UI (textos visibles al usuario)** | **Español** | Audiencia es 100% hispanohablante. Usar i18n con `es` default. |
| **Templates email** | **Español** | Idem. |
| **Datos de negocio en BD** (seed data: `plans.name`, `plans.description`, `ally_types.name`, `medical_specialties.name`, etc.) | **Español, marketing copy únicamente** | Lo lee el admin en el panel y eventualmente el afiliado en el portal. Sin refs internas — ver sección "Datos de negocio visibles al usuario" abajo. |
| **Documentación `.ai/`** (specs, ADRs, playbooks) | **Español** | Audiencia es el equipo. |
| **README.md de repos** | **Español** o **Inglés** | A discreción, optar por español para audiencia interna. |
| **Commit messages** | **Inglés** | Compatible con herramientas, convencional commits, futuros colaboradores. |
| **Nombres propios** (BCV, BCRA, Zelle, OptiBienestar, etc.) | **Sin traducir** | Son nombres propios. |
| **Términos del glosario del dominio** | Ver `centro-optico-vicente/.ai/context/domain-glossary.md` | Cada término tiene su contraparte EN para código. |

### Convención de naming

| Tipo | Convención | Ejemplo |
|---|---|---|
| **Java packages** | `lowercase.dotted` | `com.fenixcore.optibienestar360.modules.members` |
| **Java clases** | `PascalCase` | `MemberService`, `JwtAuthenticationFilter` |
| **Java methods** | `camelCase` | `findMemberByDocument()` |
| **Java fields/variables** | `camelCase` | `memberId`, `currentBalance` |
| **Java constants** | `UPPER_SNAKE_CASE` | `MAX_FAMILY_MEMBERS`, `DEFAULT_GRACE_DAYS` |
| **Java enum values** | `UPPER_SNAKE_CASE` | `ACTIVE`, `PENDING_REVIEW` |
| **TypeScript/Vue interfaces/types** | `PascalCase` | `Member`, `PaymentDTO`, `ApiResponse` |
| **TypeScript/Vue functions** | `camelCase` | `loadMembers()`, `formatCurrency()` |
| **TypeScript/Vue composables** | `useXxx` | `useApi()`, `useAuth()`, `usePermissions()` |
| **Vue components** | `PascalCase` (multi-word) | `DigitalCard.vue`, `PaymentForm.vue` |
| **Vue pages** | `kebab-case.vue` | `pages/admin/members.vue`, `pages/afiliado/usage-history.vue` |
| **CSS classes / Tailwind** | `kebab-case` | `card-container`, `text-primary` |
| **SQL tables** | `snake_case_plural` | `members`, `ally_agreements` |
| **SQL columns** | `snake_case` | `full_name`, `created_at` |
| **Archivos de migraciones Flyway** | `V{N}__{snake_case}.sql` | `V14__allies.sql` |
| **REST endpoints (URLs)** | `kebab-case` | `/v1/admin/allies/{id}/agreements` |
| **JSON keys (API)** | `snake_case` | `{"member_id": "uuid", "next_due_date": "..."}` |

### Naming descriptivo (siempre)

| ❌ Evitar | ✅ Preferir |
|---|---|
| `i`, `j`, `k` (excepto loops triviales muy cortos) | `index`, `iteration`, `currencyIndex` |
| `arr`, `obj`, `tmp`, `foo` | `currencies`, `paymentData`, `tempReceipt`, `parsedAmount` |
| `e`, `err`, `ex` (excepto catch blocks muy cortos) | `error`, `exception`, `validationError` |
| `data`, `result`, `value` solos | `memberData`, `validationResult`, `currentValue` |
| `m`, `p`, `a` | `member`, `payment`, `ally` |
| `getData`, `doStuff`, `handle` | `fetchActiveMembers`, `recalculateMembershipBalance`, `handlePaymentApproval` |

### Comentarios

**Default: no escribir comentarios.** El código bien nombrado se explica solo.

**Excepciones donde SÍ comentar:**

1. **Por qué no es obvio** (no qué hace, ya lo dice el nombre):
   ```java
   // Compensation rules require excluding refunds posted same day to avoid double-counting
   // (see business-rules.md section "Comisiones")
   ```

2. **Workaround documentado** con link al issue:
   ```typescript
   // Workaround for Nuxt UI Bug #4567: tooltips break in mobile Safari
   ```

3. **Invariante crítico** que no es obvio del tipo:
   ```java
   // member.beneficiaries.size() <= plan.maxFamilyMembers — validated in addBeneficiary()
   ```

**Evitar:**
- ❌ Comentarios obvios: `// increment counter` antes de `counter++`.
- ❌ Comentarios de seguimiento: `// added by JL 2024-03-15` (eso está en git blame).
- ❌ Comentarios desactualizados (mejor borrarlos).
- ❌ Bloques de comentarios marcando secciones (`// === USER METHODS ===`) — si necesitás separar, hacé clases o packages.

### Datos de negocio visibles al usuario (seed data, descripciones)

Reglas adicionales para strings que se INSERTAN en la BD como contenido visible al admin / afiliado / aliado — separadas del comentario técnico porque la audiencia y el ciclo de vida son distintos.

**Reglas:**

1. **100% español** sin code-switching. Términos como "pricing", "price", "discount", "review", "approved" → "tarifa/precio", "precio", "descuento", "revisión", "aprobado". Inclusive términos comerciales que en el mundo SaaS se dicen en inglés se traducen al español de Venezuela.

2. **Marketing copy únicamente.** Describe el producto al consumidor. NO describe el estado del desarrollo, ni el estado del backlog, ni decisiones pendientes con el cliente.

3. **Sin referencias internas del repositorio.** Prohibido en data persistida:
   - Nombres de checklists: `vertical-5`, `fase-2`, `checklist.md`, etc.
   - Nombres de branches: `feature/...`, `chore/...`.
   - Nombres de PRs / issues / tickets: `#42`, `#PR-123`.
   - Nombres de migraciones futuras: `V20__memberships.sql`, `corporate_contracts planeado v2`.
   - Códigos de versionado interno del producto: `v1`, `v2`, `v3` (estos viven en `scope-additions-v2.md`, no en data).
   - Marcadores de dev status: `TBD`, `TODO`, `FIXME`, `WIP`, `PRICING TBD`.

4. **El estado del producto se transmite por flags estructurados.** Si un plan está pendiente de pricing, eso se señala con `is_published = FALSE` (la column es la señal); NO con `"PRICING TBD"` embebido en la prosa. El admin tiene UI que muestra el flag; el afiliado nunca ve plans con `is_published = FALSE`.

5. **El contexto dev del seed vive donde corresponde:**
   - Header comment del archivo SQL (en inglés, puede referenciar vertical/v2/etc.).
   - Commit message.
   - Bullet del checklist en `.ai/checklists/`.
   - Doc en `.ai/scope-additions-v?.md`.

**Ejemplo (V14 seed_plans, Corporativo):**

❌ Antes:
```
Plan Corporativo — pricing por persona. PRICING TBD con cliente
(ver vertical-5 v2). Modelo: cobertura masiva para empresas, escuelas
y sindicatos. La membresía se administra vía un contrato corporativo
(corporate_contracts, planeado v2).
```
Problemas: "pricing" en inglés, "PRICING TBD" filtra dev status, "(ver vertical-5 v2)" filtra ref a checklist, "(corporate_contracts, planeado v2)" filtra ref a migración futura.

✅ Después:
```
Cobertura corporativa para empresas, escuelas y sindicatos. Tarifa
por persona; las membresías se administran mediante un contrato único
que agrupa a todos los miembros bajo la institución pagadora.
```
Marketing copy puro. El TBD del pricing se señala vía `is_published = FALSE` (estructurado) + se documenta en el header del SQL + en `vertical-5`.

**Cómo verificar en code review:**

`grep -nE 'vertical-|fase-|PRICING|TBD|TODO|FIXME|WIP|\bv[0-9]\b|planeado|pending|review_status'` sobre strings en archivos SQL de seed (`V*__seed_*.sql`, `V*__*_seeds.sql`) NO debe encontrar nada — el header comment está OK porque está después de `--`.

### Estructura de paquetes Java

```
com.fenixcore.optibienestar360/
├── OptiBienestar360Application.java
├── core/                    # Cross-cutting (config, exceptions, auditing)
│   ├── audit/
│   ├── config/
│   └── exception/
├── security/                # Auth, JWT, filters
│   ├── jwt/
│   └── SecurityConfig.java
├── common/                  # Servicios compartidos (storage, email)
│   ├── service/
│   └── model/
└── modules/                 # Módulos de negocio
    ├── users/
    │   ├── entity/
    │   ├── repository/
    │   ├── service/
    │   ├── controller/
    │   ├── dto/
    │   └── mapper/
    ├── allies/...
    ├── members/...
    ├── memberships/...
    ├── payments/...
    ├── promoters/...
    ├── validator/...
    └── notifications/...
```

### Estructura de carpetas Nuxt (frontend admin)

```
optibienestar-360-frontend/
├── pages/
│   ├── admin/
│   ├── aliado/
│   ├── afiliado/
│   └── promotor/
├── components/
│   ├── shared/         # Reutilizables
│   ├── members/
│   ├── payments/
│   └── ...
├── composables/
│   ├── useApi.ts
│   ├── useAuth.ts
│   └── usePermissions.ts
├── stores/             # Pinia
├── middleware/
├── layouts/
└── i18n/
    └── locales/
        └── es.json
```

## Alternativas consideradas

### Opción A: Todo en español (incluido código)
- **Pro:** alineado con el equipo.
- **Contra:** rompe consistencia con frameworks (Spring, Nuxt), problemas con caracteres especiales (`ñ`, tildes en nombres de variables), peor performance de IA en code review.

### Opción B: Todo en inglés (incluido docs `.ai/`)
- **Pro:** consistencia total.
- **Contra:** barrera de entrada al equipo, lectura más lenta de contexto de negocio.

### Opción C (elegida): código EN, docs ES
- **Pro:** mejor de los dos mundos.
- **Contra:** requiere disciplina y un glosario actualizado para mapear términos (resuelto con `centro-optico-vicente/.ai/context/domain-glossary.md`).

## Consecuencias

### Positivas
- Código compatible con IDE autocompletado, libs, IA, future hires anglos.
- Docs accesibles al equipo en su idioma natural.
- Glosario explícito evita confusión.

### Negativas / a mitigar
- Requiere disciplina en code review.
- Glosario debe mantenerse actualizado — incluir verificación en checklist.

## Cómo aplicar

- **En cada PR review:** verificar que variables/clases están en inglés, que mensajes UI están en español (i18n), que comentarios siguen las reglas.
- **En PR review de seed migrations** (`V*__seed_*.sql`, `V*__*_seeds.sql`): grep sobre los string literals en español buscando refs internas (`vertical-`, `fase-`, `TBD`, `TODO`, `\bv[0-9]\b`, `planeado`, etc.) — si aparecen dentro de comillas (no después de `--`), reescribir.
- **Cuando agregás concepto del dominio:** actualizar `domain-glossary.md` con su par ES/EN.
- **Linter:** considerar checkstyle / eslint rules custom para forzar (futuro).
