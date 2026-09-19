# Unificar cobros y pagos de comisión en `payments` (header + líneas) + catálogos `payment_categories`/`payment_methods`

> **Estado: schema backend implementado (2026-09-18), resto pendiente.** `V117__payments_direction_and_lines.sql` (direction/payment_type_id/person_id/promoter_id en `payments` + tabla `payment_lines` + backfill + recreación de `v_report_commissions`/`v_report_payments`), `V118__commission_payout_payment_link.sql` (`payout_payment_id` en `commissions`/`promoter_hierarchy_overrides`/`commission_retroactive_topups`) y `V119__payment_catalogs_permissions_and_audit.sql` (registro en `entity_config` de `bank`/`payment_category`/`payment_method`/`payment_line`, dominio de permiso nuevo `PAYMENT_CATALOG` con el set estándar de 6 permisos por catálogo — VIEW_ALL/CREATE/UPDATE/DELETE/RECORD_AUDIT_VIEW/REPORT_AUDIT_VIEW, mismo patrón que V73 usó para country/gender/etc. — otorgados a SYSTEM/ADMINISTRADOR) ya están escritas en local, **no aplicadas en ningún ambiente todavía**. Ya implementado también el stack Java completo (entidad/repositorio/service/DTOs/`AdminXController`) para `Bank`, `PaymentCategory` y `PaymentMethod` — mismo patrón que `Currency`/`AdminCurrencyController`, CRUD admin ya funcional vía `/v1/admin/banks`, `/v1/admin/payment-categories`, `/v1/admin/payment-methods`. El CI del PR #262 falló por el drop de `payment_method`/`reference_number` en `payments` (V117) contra el flujo de cobro existente sin migrar — refactor completo aplicado (`Payment`/`PaymentLine`/`PaymentsService`/`BeneficiaryInscriptionBiller`/`DisplayRefs`/3 reportes JasperReports), ver memoria de sesión para detalle.

**Frontend:** las 3 pantallas de catálogo nuevas (Bancos/Categorías de pago/Métodos de pago) están implementadas vía el patrón de página genérica (`catalog-registry.ts` + `pages/dashboard/catalogs/[resource].vue`, sin código de pantalla nuevo) + grupo de menú nuevo **Finanzas** (`nav.ts`) con esos 3 ítems. Monedas/Tasas de cambio/Pagos **NO se movieron todavía** a Finanzas (se quedan donde están hasta que el flujo OUT/CommissionPayoutService y las 6 pantallas de movimientos existan). Pendiente: `CommissionPayoutService` (crear `payments`/`payment_lines` reales, migrar seteo de `exchange_rate_at_paid` y setear `payout_payment_id`, backfill de histórico `PAID` vía `payout_reference`), las 6 pantallas de cobros/pagos/movimientos, y la mudanza completa del menú (Monedas/Tasas de cambio/Pagos → Finanzas) descrita en plan §"Reorganización de menú". Ver nota de naming abajo (decisión #3) sobre `payout_payment_id` vs. `payment_id`.
>
> **Nota de implementación no anticipada por el diseño original:** `commissions.payment_id` ya existía desde V26 con otro significado (el pago IN que *originó* la comisión, no el pago OUT que la *paga*). La columna nueva de la decisión #3 se llamó `payout_payment_id` para no colisionar — mismo criterio aplicado a `promoter_hierarchy_overrides`/`commission_retroactive_topups` por consistencia aunque ahí no había conflicto. `payout_reference` (string libre) se dejó intacta por ahora — su reemplazo real queda para la sesión que toque `CommissionPayoutService`.
>
> **Estado original de diseño (histórico, ver arriba para lo implementado): Este documento **reemplaza el diseño** del plan previo `.ai/plans/2026-09-16-commission-payouts-collections-plan.md` (que proponía una tabla `commission_payouts` separada) tras refinarlo con el dueño del producto en esta sesión. Ese documento queda como referencia histórica de la investigación de código, pero la sección "Lo que pidió el dueño del producto" de acá es la vigente.

## Contexto

El plan `2026-09-16-commission-payouts-collections-plan.md` detectó que:
- `commissions`/`promoter_hierarchy_overrides` mezclan cálculo y pago en la misma fila (columnas `payout_reference`/`paid_at` junto a `amount`/`tier_id`).
- `CommissionPayoutService` no es un ledger real: solo hace status-flip masivo con un `payout_reference` string libre, sin fila que represente la transferencia real.
- `payments.payment_method` es un `CHECK` hardcodeado (`V23__payments.sql:60`), sin catálogo.
- Nada soporta pago parcial o multi-método (parte en efectivo, parte en transferencia).

En esta sesión se refinó el diseño: en vez de crear una tabla `commission_payouts` espejo, **se unifica todo en la tabla `payments` existente**, generalizándola para cubrir tanto cobros (dinero que entra) como pagos de comisión (dinero que sale), y se le agrega un patrón header + líneas para soportar multi-método/parcial desde el schema, aunque hoy casi todo pago sea de una sola línea.

## Decisiones confirmadas (supersede la sección "Alcance a definir" del plan del 2026-09-16)

1. **No hay tabla `commission_payouts` nueva.** `payments` pasa a representar ambas direcciones: `direction` (`IN`/`OUT`). Cobro de membresía = `IN`. Pago de comisión = `OUT`. Un mismo dato, filtrado por rol en las pantallas (admin ve todo; afiliado ve "mis pagos" — que para la empresa son cobros; promotor filtra cobros solo de sus afiliados asociados).
2. **`payments` pasa a ser encabezado.** `payment_method`, `reference_number` y el monto por método dejan de ser obligatorios a nivel de header. Se crea `payment_lines` (nueva tabla) para cargarlos por línea — soporta que un solo pago se divida en efectivo + transferencia + cualquier combinación. Se construye el schema header+líneas **desde ya**, no como fase futura: todo pago hoy simplemente crea 1 línea.
3. **Vínculo pago↔comisión: FK directa**, no tabla puente N:M. `commissions`/`promoter_hierarchy_overrides`/`commission_retroactive_topups` ganan columna `payment_id` (reemplaza el `payout_reference` string) — un pago cubre N filas de comisión. El "pago parcial" que preocupaba (dividir el desembolso por método) ya está resuelto por `payment_lines`; no se modela que una misma fila de comisión se cubra con dos pagos distintos en momentos distintos, porque el negocio no paga una comisión en partes a lo largo del tiempo.
4. **Dos catálogos separados — `payment_categories` y `payment_methods`** (no `transaction_types` — muy genérico si mañana se agregan facturas/cotizaciones). Reemplazan el `CHECK` de `payment_method`. **Decisión revisada** (ver nota abajo): el diseño original de este plan proponía un catálogo único `payment_types` con una columna `direction` que hacía doble trabajo (IN/OUT real para motivos, `BOTH` como marcador disfrazado de "esto es un método"); se separó en dos tablas antes de que algo llegara a depender de él, porque un motivo y un método no son la misma clase de dato:
   - **`payment_categories`** (encabezado): `payments.payment_type_id` → motivo/categoría: cuota de membresía, inscripción, cargo — comisión bancaria/mora/intereses (`direction='IN'`); comisión directa regular, bono, comisión jerárquica, ajuste retroactivo (`direction='OUT'`). Acá `direction` es un dato real de esa categoría, no un truco. Nombres y descripciones en español (columna `description` nueva, ver "Diseño de tablas"); los `code` siguen en inglés como identificadores técnicos internos (`MEMBERSHIP_FEE`, `HIERARCHY_OVERRIDE`, `CHARGE`, etc.), igual que el resto de catálogos del sistema.
   - **`payment_methods`** (línea): `payment_lines.payment_type_id` → forma de pago: efectivo, transferencia bancaria, zelle, pago móvil, cripto, transferencia internacional, Zinly, PayPal, tarjeta de débito, tarjeta de crédito, cheque, depósito bancario, otro. Sin columna `direction` (un método nunca pertenece a una sola dirección). En cambio, tiene flags `is_mandatory_bank_account`/`is_mandatory_phone`/`is_mandatory_email`/`is_mandatory_reference_number` para que la UI/backend sepan qué campos adicionales exigir por método — inspirado en el catálogo legado `tglo_METODO_PAGO` de `proyecto-iv-mh/data/bd/database.sql` y sus backups (`es_banco_obligatorio`, `es_telefono_obligatorio`, `es_correo_obligatorio`, etc.), adaptado a este schema en vez de calcado campo por campo. La lista de métodos se amplió tras revisar ese catálogo legado a fondo (incluyendo `proyecto-iv-mh/data/backup/backup-2026-01-09_01-31.sql`) para no perder formas de pago que el negocio ya usa. De ese legado, `Cargo` (comisión bancaria, mora, intereses) resultó ser un **motivo de cobro**, no un método — se agregó como categoría `CHARGE` (`direction='IN'`) en `payment_categories`, no acá. `Nota de Crédito` sigue fuera de ambos catálogos por ahora, pendiente de confirmar con el dueño del producto — no es una forma de pago ni un motivo de cobro sino un instrumento de compensación (ver "Fuera de alcance / futuro" → vuelto/sobregiro).
   Cada FK apunta a su propia tabla, así la base de datos rechaza por integridad referencial un id de categoría donde debía ir un método (y viceversa), en vez de depender de que el código respete la convención a mano. Implementado en `V115__payment_categories_and_methods.sql` — el archivo se llamó originalmente `V115__payment_types_catalog.sql` (nombre heredado del diseño de catálogo único descartado) y se renombró el 2026-09-18 para reflejar su contenido real, seguro porque Flyway no lo había corrido en ningún ambiente todavía.
5. **Moneda completa — YA IMPLEMENTADO**, no pendiente. Los commits `2c6794a` (backend, PR #261, 2026-09-17) y `f26c092` (frontend, PR #155, 2026-09-17) ya agregaron `currency_Uuid`/`currency_Display` a `PaymentDto`/`CommissionDto`/`PlanDto` (vía `DisplayRef`, `PaymentMapper`/`CommissionMapper`/`PlanMapper`) y el link rápido a `/dashboard/catalogs/currencies` en las pantallas de pagos/comisiones/planes. Ese mismo commit backend agregó a `Commission` un **snapshot de tasa de cambio propio** (`exchange_rate_at_earned`/`earned_rate_date` al devengar, `exchange_rate_at_paid`/`paid_rate_date` al pasar a `PAID`, campo calculado `fxVarianceAmountConverted`) vía migración **V114** (`V114__commission_fx_snapshot.sql`) — esto consume el número de migración que este plan había reservado como "próximo libre"; ver nota de renumeración en "Archivos relevantes". El snapshot de tasa de pago (`exchange_rate_at_paid`) ya lo persiste `CommissionPayoutService` al momento del status-flip a `PAID` — al rediseñar ese servicio para que cree la fila `payments`/`payment_lines` real (decisión #1), ese seteo de tasa debe migrar al mismo punto donde se crea el `payment_id` real, no perderse. Reusar `ConversionEnricher.officialRateAt(currency, instant)` (agregado en ese mismo commit) para cualquier snapshot de tasa nuevo que necesite `payments`/`payment_lines`, en vez de reimplementar la resolución de tasa vigente a una fecha.
6. **Histórico ya `PAID`** (solo tiene `payout_reference` string, sin fila real): queda pendiente de decidir backfill (agrupar por `payout_reference` y generar `payments`/`payment_lines` retroactivos) vs. dejar el histórico tal cual — se decide en la sesión de implementación, no bloquea este plan.
7. **Alcance de hoy: solo este documento de diseño**, sin tocar código ni migraciones.

## Diagrama de tablas y relaciones

```
                    ┌───────────────────┐         ┌───────────────────┐         ┌───────────────────┐
                    │ payment_categories │         │  payment_methods   │         │       banks         │
                    │  code, name,        │         │  code, name,        │         │  code (SUDEBAN),      │
                    │  direction IN/OUT   │         │  is_mandatory_*      │         │  name                  │
                    │  (motivo real)       │         │  (sin direction)      │         └─────────┬──────────┘
                    └─────────┬──────────┘         └─────────┬──────────┘                   │
                              │                               │                     bank_id (nullable,
                    payment_type_id                           │                     solo si el método exige
                    (motivo: cuota                             │                     cuenta bancaria)
                     membresía,                                 │                              │
                     comisión, ...)                              │                              │
                              ▼                                   ▼                              ▼
                    ┌──────────────────────┐         ┌────────────────────────────┐
                    │       payments        │ 1     N │      payment_lines          │
                    │  (header)              │────────▶│  payment_type_id (método,   │
                    │  direction IN/OUT      │         │  FK a payment_methods)      │
                    │  payment_type_id       │         │  bank_id (FK a banks,        │
                    │  (FK a payment_categories)│      │  nullable)                   │
                    │  membership_id (IN)    │         │  amount, currency_id        │
                    │  person_id             │         │  reference_number           │
                    │  promoter_id           │         │  status (propio,             │
                    │  amount, currency_id   │         │  default = el del header)    │
                    │  status                │         └────────────────────────────┘
                    └──────────┬─────────────┘
                               │ 1
                               │
                               │ N (payment_id, nullable hasta pagar)
                 ┌─────────────┼──────────────────────────┐
                 ▼             ▼                          ▼
      ┌──────────────────┐ ┌────────────────────────────┐ ┌───────────────────────────┐
      │   commissions     │ │ promoter_hierarchy_overrides│ │ commission_retroactive_    │
      │   status,          │ │  status, basis_amount,       │ │ topups                      │
      │   amount, tier_id  │ │  tier_id, amount             │ │  status, amount              │
      └──────────────────┘ └────────────────────────────┘ └───────────────────────────┘

direction = IN  → payments.membership_id poblado (cobro de cuota de membresía/inscripción del afiliado)
direction = OUT → payments cubre N filas de commissions/overrides/topups vía su FK payment_id (pago de comisión al promotor)
banks es catálogo independiente (no tiene direction ni is_mandatory_*) — solo se referencia desde payment_lines.bank_id cuando el payment_methods elegido lo exige (transferencia bancaria, transferencia internacional, cheque, depósito bancario).
```

**Nota sobre `payment_type_id` en el diagrama:** el nombre de columna se mantiene igual en ambas tablas por continuidad histórica con el diseño original, pero ya **no** apuntan al mismo catálogo — `payments.payment_type_id` es FK a `payment_categories` (**motivo/categoría**: cuota de membresía, inscripción, comisión regular, bono, override, top-up) y `payment_lines.payment_type_id` es FK a `payment_methods` (**método**: efectivo, transferencia, zelle, etc.). Son dos columnas FK distintas apuntando a dos tablas catálogo distintas, cada una con su propia integridad referencial — no una tabla compartida distinguida por convención.

**`membership_id`** (columna que ya existe en `payments` desde V23): FK a la membresía del afiliado que se está cobrando — no a la persona directamente. Solo aplica cuando `direction = 'IN'`, y sigue haciendo falta porque una misma persona puede tener más de una membresía a lo largo del tiempo (renovaciones, cambios de plan) y el cobro corresponde a una membresía específica, no a la persona en general.

Para resolver el enlace directo que pedía el dueño del producto ("¿no debería apuntar a persona? ¿o tener también `promoter_id`?"), el header suma dos FKs más, pensadas para no depender de multi-hop joins al filtrar por pantalla:
- **`person_id`** → FK directa a la persona/usuario contraparte de la transacción (el afiliado si `direction='IN'`, el usuario del promotor si `direction='OUT'`). Reemplaza la semántica ambigua de `payer_user_id`.
- **`promoter_id`** → FK directa al promotor relacionado con la transacción: para `OUT` es el promotor que recibe el pago; para `IN` es el promotor dueño de la red a la que pertenece el afiliado que paga (denormalizado a propósito, para que "cobros de mi red" y "mis pagos de comisiones" filtren directo por `promoter_id = :promotorActual` sin atravesar `membership → afiliado → referido-de`).

## Diseño de tablas (borrador para la sesión de implementación)

**`payments`** (evoluciona desde `V23__payments.sql`, header):
- `payments_id`, `uuid` (ya existen)
- `direction` — `CHECK (direction IN ('IN','OUT'))` (nueva)
- `payment_type_id` → FK `payment_categories` (nueva; motivo/categoría: cuota membresía, inscripción, cargo, comisión regular, bono, comisión jerárquica, ajuste retroactivo)
- `membership_id` — sigue existiendo, solo aplica cuando `direction = 'IN'` (ver nota arriba)
- `person_id` → FK directa a la persona/usuario contraparte (afiliado si `IN`, usuario del promotor si `OUT`); reemplaza `payer_user_id`
- `promoter_id` → FK directa al promotor relacionado (receptor del pago si `OUT`; dueño de la red del afiliado si `IN`) — denormalizada para filtrar pantallas sin multi-hop joins
- `amount`, `currency_id`, `exchange_rate_used`, `exchange_rate_date` — total del header (validado contra suma de líneas)
- `status`, `reviewed_by`, `reviewed_at`, `review_reason` — estado por defecto del pago completo
- ~~`payment_method`~~, ~~`reference_number`~~ → se mueven a `payment_lines`

**`payment_lines`** (nueva):
- `payment_line_id`, `uuid`
- `payment_id` → FK `payments`
- `payment_type_id` → FK `payment_methods` (método: efectivo, transferencia bancaria, zelle, pago móvil, cripto, transferencia internacional, Zinly, PayPal, tarjeta de débito, tarjeta de crédito, cheque, depósito bancario, otro)
- `bank_id` → FK `banks` (nueva; nullable — solo aplica cuando el método tiene `is_mandatory_bank_account=true`: transferencia bancaria, transferencia internacional, cheque, depósito bancario)
- `amount`, `currency_id` (por línea, permite método distinto con su propio comprobante)
- `reference_number`
- `status` (propia, misma state machine que el header — PENDING/APPROVED/REJECTED) — por defecto hereda/coincide con el `status` del header al crearse, pero puede procesarse y aprobarse de forma independiente línea por línea (ej.: se aprueba la parte en transferencia antes que la parte en efectivo). El `status` del header pasa a ser un resumen derivado (ej.: `APPROVED` solo si todas las líneas están `APPROVED`) más que la única fuente de verdad.

**`payment_categories`** (catálogo nuevo, motivo/categoría, reemplaza parte del `CHECK` de V23 — implementado en `V115__payment_categories_and_methods.sql`):
- `payment_categories_id`, `uuid`, `code` (identificador técnico en inglés, p. ej. `HIERARCHY_OVERRIDE`), `name` (nombre visible, en español — p. ej. "Comisión jerárquica"), `description` (texto libre en español para ampliar el detalle de la categoría, p. ej. "Pago de la comisión que un promotor de rango superior recibe sobre las ventas generadas por los promotores de los niveles inferiores de su red"), `direction` (`IN`/`OUT`, real — no `BOTH`), audit columns estándar (ADR 0006)

**`payment_methods`** (catálogo nuevo, forma de pago, reemplaza el resto del `CHECK` de V23 — implementado en el mismo `V115__payment_categories_and_methods.sql`):
- `payment_methods_id`, `uuid`, `code`, `name`, `is_mandatory_bank_account`, `is_mandatory_phone`, `is_mandatory_email`, `is_mandatory_reference_number` (flags para que la UI/backend sepan qué campos adicionales exigir por método — inspirado en el catálogo legado `tglo_METODO_PAGO` de `proyecto-iv-mh/data/bd/database.sql`), audit columns estándar (ADR 0006). Sin columna `direction`: un método aplica igual a cobros y pagos.

**`commissions` / `promoter_hierarchy_overrides` / `commission_retroactive_topups`**:
- Se agrega `payment_id` → FK `payments` (nullable hasta que se pague)
- `status` se mantiene como state machine explícita (PENDING/APPROVED/PAID/VOIDED/DISPUTED), seteado junto con `payment_id` en la misma transacción — no se vuelve puramente derivado para no romper `HierarchyOverrideReRatingService` (que hoy solo mira `status`).

**`banks`** (catálogo nuevo — no existía ninguna entidad de banco en el sistema; implementado en `V116__banks.sql`, mismo patrón exacto que `currencies` V84):
- `banks_id`, `uuid`, `code` (código SUDEBAN, p. ej. `0102`), `name`, `is_active`, `status`, audit columns estándar (ADR 0006)
- Prerrequisito de `payment_lines.bank_id` (arriba): sin este catálogo no hay dónde apuntar la "cuenta bancaria" que exigen `is_mandatory_bank_account=true` (transferencia, transferencia internacional, cheque, depósito bancario) en `payment_methods`.
- Seed: los ~28 bancos venezolanos activos, tomados del catálogo legado `tglo_BANCO` de `proyecto-iv-mh/data/bd/database.sql` (misma fuente que ya se usó para `payment_methods`).
- **✅ Seed corregido (2026-09-18):** el catálogo legado `tglo_BANCO` tenía dos códigos incorrectos — `0169` decía "Mi Banco" (debía ser R4) y "Banco Digital de los Trabajadores" estaba en `0185` (código inexistente; el correcto es `0175`). Se reescribió el seed de `V116__banks.sql` contra el listado oficial vigente de participantes de la Cámara de Compensación Electrónica (SUDEBAN) aportado por el dueño del producto, y se agregó columna `short_name` (nombre comercial corto, ej. "Banesco") además de `name` (razón social completa) para selectores de UI/recibos.
- **Fuera de esta migración, pendiente para la sesión de implementación:** si en algún momento se necesita capturar el número de cuenta específico (no solo el banco), eso va en una tabla de cuentas bancarias aparte (p. ej. `payment_line_bank_accounts` o similar) — `banks` es solo el catálogo del banco emisor, no una cuenta.

## Fase 2 (futuro, solo diseño): cuentas de cobro de la organización

**Estado: NO implementado — solo diseño, no forma parte de la migración V115/V116.** Pedido del dueño del producto (2026-09-17): más allá de los catálogos (`payment_categories`, `payment_methods`, `banks`), la organización necesita configurar *sus propias* cuentas/formas de cobro concretas — ej. "Cuenta corriente Banesco N° 0134-XXXX-XX a nombre de [Empresa]", "Pago móvil 0414-XXXXXXX", "Zelle empresa@dominio.com" — de manera que (a) un administrador de finanzas pueda darlas de alta/baja y (b) opcionalmente exponerlas al público (afiliados, promotores, o un tercero externo vía API) para que sepan rápidamente a qué cuenta transferir.

**Tabla nueva: `company_payment_accounts`** (nombre tentativo; el detalle concreto de cada forma de cobro que la organización acepta):
- `company_payment_accounts_id`, `uuid`, audit columns estándar (ADR 0006)
- `payment_method_id` → FK `payment_methods` (qué forma de pago es esta cuenta: transferencia, pago móvil, Zelle, etc.)
- `bank_id` → FK `banks` (nullable — solo cuando el método lo exige, igual criterio que `payment_lines.bank_id`)
- `label`/`alias` (texto corto visible, p. ej. "Cuenta principal Banesco")
- Datos de la cuenta en sí: campos concretos según lo que el método exija — número de cuenta, titular, cédula/RIF del titular, teléfono (pago móvil), correo (Zelle/PayPal/Zinly/cripto), dirección de wallet (cripto). A decidir en implementación si son columnas explícitas o un `details` JSONB único; los `is_mandatory_*` de `payment_methods` ya indican qué le corresponde pedir a cada método, así que puede reusarse esa misma validación en vez de duplicarla.
- `is_public` (BOOLEAN NOT NULL DEFAULT FALSE) — controla si esta cuenta aparece en el endpoint público de datos de pago. Si en el futuro se necesita distinguir "visible en la app para afiliados/promotores" de "visible sin autenticación para terceros", este flag booleano se abre en dos (`is_visible_to_users` / `is_public_api`) — no se sobre-diseña ahora sin ese requerimiento confirmado.
- `is_active` — igual que el resto de catálogos, para poder desactivar sin borrar (una cuenta cerrada no debería seguir apareciendo aunque tenga histórico de pagos asociados).
- `display_order` (SMALLINT, opcional) — para que el administrador controle el orden en que las cuentas aparecen en la pantalla/endpoint público, en vez de depender del orden de inserción.

**Relación con lo ya diseñado:** esta tabla es informativa/de catálogo interno de la organización — no reemplaza ni se vincula por FK obligatoria a `payment_lines` (una línea de pago real sigue llevando su propio `bank_id`/`reference_number` de lo que efectivamente ocurrió, que puede o no coincidir con una de estas cuentas publicadas). Si más adelante se quiere trazabilidad de "a cuál cuenta publicada llegó este pago", se agregaría un `company_payment_account_id` nullable en `payment_lines` en ese momento — no se agrega preventivamente aquí.

**Endpoint público (diseño, no implementación):** `GET /api/public/payment-accounts` (o el prefijo público que ya use el proyecto) devuelve solo las filas con `is_public=true AND is_active=true`, sin autenticación, con los campos mínimos necesarios para que un pagador externo sepa dónde pagar (banco, alias, número/identificador, método) — nunca campos internos de auditoría. El administrador de finanzas gestiona altas/bajas y el flag `is_public` desde una pantalla de administración (ver más abajo), no hay edición directa vía el endpoint público (solo lectura).

**Pantalla de administración nueva:** "Cuentas de cobro" (nombre tentativo), en el grupo `finanzas` del menú (ver "Reorganización de menú" más abajo — pendiente de agregar el ítem cuando se implemente esta fase). Permite al administrador de finanzas crear/editar/desactivar cuentas, elegir método y banco (reusando los selectores ya construidos para `payment_methods`/`banks`), y marcar/desmarcar `is_public`.

**Permisos (a definir en implementación, mismo patrón `<ENTIDAD>_ACCIÓN` que el resto):** `COMPANY_PAYMENT_ACCOUNT_VIEW_ALL` / `_CREATE` / `_UPDATE` / `_DELETE` para el CRUD de administración; el endpoint público no requiere permiso (es público por diseño, filtrado por `is_public` a nivel de query, nunca por rol).

**Por qué es fase 2 y no parte de esta migración:** depende de que `payment_methods` y `banks` (V115/V116) ya existan — no tiene sentido diseñar el detalle de columnas de `company_payment_accounts` hasta confirmar la forma final de esos dos catálogos. Se deja documentado acá para no perder el requerimiento, pero la migración concreta (`V11X__company_payment_accounts.sql`) se numera y escribe en una sesión de implementación posterior, junto con el endpoint y la pantalla.

## Pantallas requeridas (frontend)

Todas son vistas sobre el mismo `payments`/`payment_lines` unificado — difieren solo por filtro de `direction`, relación (`membership_id`/`person_id`/`promoter_id`) y alcance de permisos.

**Autoservicio (promotor/afiliado):**
1. **Cobros de mi red** (promotor) — cobros (`direction=IN`) filtrados por `promoter_id` = el promotor autenticado (afiliados de su downline).
2. **Mis pagos** (afiliado) — el afiliado ve sus propios cobros (`direction=IN`, `person_id`/`membership_id` = los suyos). Para la empresa es un cobro; para el afiliado es "lo que pagó".
3. **Mis pagos de comisiones** (promotor) — pagos de comisión (`direction=OUT`) filtrados por `promoter_id` = el promotor autenticado (lo que le han pagado a él).

**Administración — vistas especializadas por responsable:**
4. **Cobros generales** (admin) — todos los cobros (`direction=IN`) de afiliados/membresías. Scope acotado solo a afiliados y membresías (no mezcla pagos de comisión). Pensada para quien confirma/aprueba cobros.
5. **Pagos generales** (admin) — todos los pagos de comisión (`direction=OUT`) hechos a promotores. Análoga a "Cobros generales" pero para egresos. Pensada para quien ejecuta los pagos — puede ser un responsable distinto de quien confirma cobros.

**Administración — vista maestra combinada:**
6. **Movimientos** (pagos/cobros, admin) — ambas direcciones juntas (`IN`+`OUT`) en una sola tabla, con filtros: tipo de pago (motivo), método, monto, moneda, rango de fechas, estado, dirección. Pensada para quien necesita visibilidad completa (ej. gerencia/finanzas) aunque la confirmación de cobros y la ejecución de pagos las hagan personas distintas (#4 y #5).

## Reorganización de menú (frontend)

Hoy no existe un grupo transversal de "Finanzas": lo monetario está disperso — **Pagos** cuelga de `afiliaciones`, **Comisiones**/reglas de `comercial`, **Tasas de cambio** de `sistema`, **Currency** (catálogo) de `datos-maestros` (`app/utils/nav.ts`). El menú es data-driven (`MAIN_NAV` en `nav.ts` + `useNav.ts` filtra por permiso/rol) — agregar un grupo nuevo es declarativo, sin tocar el motor de menú.

**Propuesta: nuevo grupo `finanzas` ("Finanzas"), consolidando lo que hoy está repartido:**
- **Cobros generales** — es la pantalla `/dashboard/payments` actual (ya solo maneja cobros hoy), se mueve de `afiliaciones` → `finanzas` y se relabelea (con `direction=IN` por defecto tras la unificación).
- **Pagos generales** — pantalla nueva (`direction=OUT`), no existe hoy ningún ledger real de pagos de comisión ejecutados. Va en `finanzas`.
- **Movimientos** — pantalla nueva (maestra combinada). Va en `finanzas`.
- **Tasas de cambio** — se mueve de `sistema` → `finanzas` (hoy tratada como config técnica; conceptualmente es dato financiero, ADR 0015).
- **Currency / Monedas** — se mueve de `datos-maestros` → `finanzas` (misma razón; hoy vive separada de Tasas de cambio pese a ser el mismo dominio multi-moneda).
- **Bancos** — catálogo nuevo (no existía ninguna entidad de banco en el sistema; `V116__banks.sql`, mismo patrón que Monedas/`currencies` V84). Va en `finanzas` junto a Monedas — mismo criterio: catálogo de soporte multi-moneda/pagos, no un dato genérico de `datos-maestros`.
- **Comisiones** y **Reglas de comisión** — **se quedan en `comercial`**, no se mueven: están acopladas a jerarquía/rank de promotores, y ya conviven ahí con Organigrama/Promotores. Solo ganan el link nuevo a `payment_Uuid`/`payment_Display` (la fila de pago real que las cubrió).

**Simulación completa del sidebar** (grupos tocados por este plan; el resto — Aliados, Seguridad, Datos maestros salvo Monedas, Reportes — queda igual):

```
👥 Afiliaciones                 (pierde "Pagos", que se muda a Finanzas)
├── Planes
├── Membresías
├── Afiliados
└── Reporte de pagos             (se mantiene como reporte, o se muda junto con Pagos — a confirmar en implementación)

📣 Comercial                    (sin cambios de estructura, solo dato nuevo)
├── Tipos de promotor
├── Cargos jerárquicos
├── Promotores
├── Organigrama
├── Reglas de comisión
├── Comisiones                   (gana link a payment_Uuid/payment_Display, misma pantalla)
├── Aprobación de comisiones
├── Reporte de comisiones
└── Reporte de pagos de comisiones

💰 Finanzas                     (grupo nuevo — orden pedido: catálogos primero, vista maestra, luego especializadas)
├── Monedas                      (currencies — movido desde Datos maestros)
├── Bancos                        (banks — catálogo nuevo, V116, mismo patrón que Monedas)
├── Tasas de cambio               (exchange-rates — movido desde Sistema)
├── Categorías de pago              (payment_categories — catálogo nuevo, motivo/categoría)
├── Métodos de pago                 (payment_methods — catálogo nuevo, forma de pago, reemplaza el CHECK de payment_method)
├── Movimientos                    (nuevo — vista maestra IN+OUT)
├── Cobros generales               (ex "Pagos" de Afiliaciones, relabeleado, direction=IN)
└── Pagos generales                (nuevo — direction=OUT, pagos de comisión ejecutados)

🪪 Mis portales                  (portal no-admin — afiliado / aliado / promotor)
├── Mi carnet                     (afiliado)
├── Mi empresa aliada              (aliado)
├── Validador                      (aliado)
├── Consumos                       (afiliado)
├── Mis pagos                      (nuevo — afiliado: cobros IN de su propia membresía)
├── Mis comisiones                 (nuevo — promotor: comisiones devengadas propias, COMMISSION_VIEW_OWN)
├── Mis pagos de comisiones        (nuevo — promotor: pagos OUT que le han hecho a él, PAYMENT_VIEW_OWN)
└── Cobros de mis afiliados        (nuevo — promotor: cobros IN de los afiliados de su red/downline)
```

**"Categorías de pago"**, **"Métodos de pago"** y **"Bancos"** no estaban en la lista original de pantallas — se agregan porque son catálogos nuevos (decisión #4, revisada a dos tablas separadas, más `banks` como prerrequisito de `payment_lines.bank_id`) y, siguiendo el patrón ya establecido para Monedas/Tasas de cambio, cada uno necesita su propia pantalla de mantenimiento (alta/baja), colgadas de `finanzas` en vez de `datos-maestros` genérico — igual que se hizo con Monedas. Pendiente de implementación (no incluido en este plan a nivel de código): entrada `banks` en `app/utils/catalog-registry.ts` (mismos campos `code`/`name` que la entrada de `currencies`) y su `NavLeaf` bajo el grupo `finanzas` en `app/utils/nav.ts`, más permisos `BANK_VIEW_ALL`/`BANK_CREATE`/`BANK_UPDATE`/`BANK_DELETE` siguiendo la convención `<ENTIDAD>_ACCIÓN` ya usada por `CURRENCY_*`.

**Promotor (autoservicio) — 3 ítems de menú nuevos en `mis-portales`, no reuso silencioso de Comercial/Finanzas:**
Se corrige el enfoque anterior: en vez de que el promotor vea los mismos ítems de admin (**Cobros generales**/**Pagos generales**/**Comisiones**) filtrados por detrás, gana sus propias entradas dedicadas en `mis-portales` — misma UX que ya tiene el afiliado/aliado ahí (Mi carnet, Consumos, etc.), en vez de mezclarse con las pantallas de administración:
- **Mis comisiones** — sus filas de `commissions` (`COMMISSION_VIEW_OWN`), la misma data que vería un admin en "Comisiones" pero ya prefiltrada y con su propia ruta/pantalla.
- **Mis pagos de comisiones** — sus pagos ejecutados (`direction=OUT`, `promoter_id` = él mismo; `PAYMENT_VIEW_OWN`).
- **Cobros de mis afiliados** — cobros (`direction=IN`) de los afiliados de su downline, filtrado por `promoter_id` denormalizado en `payments` (decisión de diseño de "Diagrama de tablas" arriba).

Backend-wise reusan los mismos endpoints/tablas que sus contrapartes de admin (no hay servicio ni tabla duplicada) — lo que cambia es que el frontend expone rutas/ítems de menú propios para el promotor en vez de compartir literalmente el mismo ítem visual que ve un admin.

## Impacto a revisar en implementación

- `CommissionPayoutService.java` (`modules/promoter/service/`) — hoy hace status-flip con `payout_reference` string; pasa a crear la fila `payments` (+ línea) real y setear `payment_id` en bloque sobre las filas cubiertas.
- `HierarchyOverrideReRatingService` — sigue mirando `status`, sin cambios de contrato si se mantiene la state machine explícita (decisión #3 arriba).
- `CommissionApprovalService` (plan `2026-09-07-hierarchical-commissions-plan.md` §4) — flujo de aprobación previo a pago, no debería verse afectado por esta separación.
- `PaymentDto`, `CommissionDto` — `currency_Uuid`/`currency_Display` **ya existen** (commit `2c6794a`); falta agregar `direction`, `payment_type_Display` (motivo), lista de líneas; nuevo `PaymentLineDto`. `CommissionDto` ya trae `exchangeRateAtEarned`/`exchangeRateAtPaid`/`fxVarianceAmountConverted` — sumar `payment_Uuid`/`payment_Display` (link al `payments` real que la cubrió) sin tocar esos campos de FX existentes.
- `CommissionPayoutService` — coordinar el nuevo seteo de `payment_id` (decisión #3) con el snapshot `exchange_rate_at_paid`/`paid_rate_date` que ya persiste hoy (commit `2c6794a`); ambos deben quedar consistentes en la misma transacción de payout.
- Pantallas frontend: admin (ve todo, ambas direcciones), afiliado ("mis pagos" = cobros de la empresa hacia/desde él), promotor (cobros filtrados a sus afiliados asociados) — mismo endpoint/tabla, filtro por `direction` + relación.

## Fuera de alcance / futuro: saldos, montos pendientes y recálculo de valorización FX

> No forma parte de este plan de unificación. Queda documentado acá como insumo para un plan/sesión posterior, a discutir con el dueño del producto antes de diseñar schema. Se originó al preguntar si además del monto pagado convendría trackear monto del concepto, monto restante, monto sobrante/vuelto/sobregiro, y saldos globales por promotor/afiliado con una acción de recálculo.

**Separar tres cosas que suelen mezclarse en una sola idea de "recalcular":**
1. *Snapshot histórico de tasa* — ya cubierto por la decisión #5 de este plan (`exchange_rate_at_earned`/`exchange_rate_at_paid`). Es inmutable una vez ocurrido el evento; no se recalcula nunca.
2. *Valorización en vivo de lo pendiente* — para algo aún no pagado, la conversión a moneda base usa la tasa **vigente**, que cambia con el tiempo. Esto sí tiene sentido recalcular, y solo debe tocar filas sin `payment_id` (nunca las ya `PAID`).
3. *Saldo/estado* (pagado vs. pendiente vs. excedente) — aritmética sobre lo ya guardado (concepto vs. pago), no depende de la tasa salvo para mostrarlo en otra moneda.

Un solo botón "recalcular todo" que no distinga estas tres cosas corre el riesgo de pisar el snapshot histórico de algo ya `PAID`. Si se implementa, debería ser como mínimo dos acciones separadas: **recalcular saldos** (re-agregación pura, siempre segura) y **actualizar valorización de lo pendiente** (usa `ConversionEnricher.officialRateAt` con la tasa vigente, solo sobre `PENDING`/`APPROVED` sin `payment_id`).

**Monto restante/sobrante por fila pagable (`commissions`/`overrides`/`topups`):** dado que este plan ya decidió (#3) que una fila de comisión se cubre con un solo pago (no en partes a través del tiempo), no se recomienda agregar columnas propias de "monto restante" ahí — es derivable (`payment_id IS NULL` → pendiente el `amount` completo; si no, `0`) y agregar una columna sería una fuente más de inconsistencia. Un mismatch entre el total de comisiones agrupadas y el monto del pago debería tratarse como error de validación al armar el `payment`, no como estado de negocio a persistir.

Donde el concepto de saldo parcial sí podría ser real es en **membresías** (`direction=IN`): si un afiliado puede pagar una cuota en varias partes a lo largo del tiempo, esa entidad sí necesitaría "monto del concepto" (precio del plan/cuota) vs. "suma de pagos aplicados" vs. "saldo pendiente". Falta confirmar con el dueño del producto si ese escenario existe hoy o es igual de atómico que las comisiones (un cobro siempre cierra completo).

**Vuelto/sobregiro:** si llega a pasar (pago en efectivo con cambio, o pago de más), no se recomienda modelarlo como un campo mutable de "saldo a favor" en la persona — mejor como un movimiento más en `payments` (p. ej. un `payment_type` de "aplicación de crédito"/"nota de crédito"), para tener trazabilidad de cuándo se generó el excedente y cuándo se consumió. Nota relacionada: el catálogo legado `tglo_METODO_PAGO` (`proyecto-iv-mh`) traía `Nota de Crédito` y `Cargo` como filas de "método de pago". `Cargo` resultó ser en realidad un motivo de cobro (comisión bancaria, mora, intereses) y ya se agregó como categoría `CHARGE` en `payment_categories` (V115). `Nota de Crédito` sigue pendiente — no describe cómo se movió el dinero ni por qué se cobra, sino un instrumento de compensación; falta decidir el modelo concreto con el dueño del producto.

**Saldos globales por actor (promotor/afiliado):** para las columnas de cobro/pago/cobro pendiente/pago pendiente por actor, se recomienda arrancar con una **vista SQL** (`v_promoter_balances`, `v_affiliate_balances`) que agregue directo sobre `payments`/`payment_lines`/`commissions`, en vez de una tabla materializada desde el día uno — siempre fresca, sin sincronización que mantener, y debería rendir bien al volumen esperado de este negocio. Solo migrar a tabla cacheada + job de recálculo si se mide que la vista es lenta en producción; en ese caso la acción de "recalcular saldos" ya mencionada arriba es el mecanismo correcto, y más adelante se puede programar como tarea periódica sin rediseñar nada (la acción ya existiría, solo cambia si escribe a una tabla o no).

**Resumen de qué evitar agregar al schema salvo que se confirme el caso de negocio:** columnas de "monto restante"/"monto sobrante" por fila en `commissions`/`overrides`/`topups` (redundante con el modelo de un-pago-cubre-N-comisiones ya decidido), y un campo mutable de "saldo a favor" sin ledger detrás. Lo que sí valdría la pena explorar en una sesión de diseño futura: `balance_due` cacheado en membresía (solo si hay pago parcial de cuotas), las vistas de saldo agregado por actor, y la separación de las dos acciones de recálculo descritas arriba.

## Archivos relevantes

- `optibienestar-360-backend/src/main/resources/db/migration/V23__payments.sql` (header actual, `payment_method` CHECK a reemplazar)
- `optibienestar-360-backend/src/main/resources/db/migration/V26__commissions.sql`
- `optibienestar-360-backend/src/main/resources/db/migration/V102__hierarchy_override_tiers_and_ledger.sql`, `V106__hierarchy_override_payout_tracking.sql`
- `optibienestar-360-backend/src/main/resources/db/migration/V114__commission_fx_snapshot.sql` (**ya usada** por el commit `2c6794a` — el schema de este plan arranca en **V115**, no V114; **coordinar con el plan `2026-09-17-ally-self-service-user-creation-plan.md`**, que también reserva V115/V116 para su propia tabla `role_assignable_by_creator_type` — renumerar el que se implemente segundo)
- `optibienestar-360-backend/src/main/resources/db/migration/V115__payment_categories_and_methods.sql` (**ya escrita, solo en local, no aplicada en ningún ambiente todavía**) — crea `payment_categories` + `payment_methods` (decisión #4 revisada arriba). Aún faltan por implementar: `direction`/`person_id`/`promoter_id` en `payments`, la tabla `payment_lines`, y `payment_id` en `commissions`/`overrides`/`topups`.
- `optibienestar-360-backend/src/main/resources/db/migration/V116__banks.sql` (**ya escrita, solo en local, no aplicada en ningún ambiente todavía**) — crea el catálogo `banks` (~28 bancos venezolanos con código SUDEBAN), mismo patrón que `V84__currencies.sql`. Prerrequisito de `payment_lines.bank_id` (aún no implementada). Mismo riesgo de renumeración que V115 (ver nota V114 arriba) — coordinar con el plan de self-service de aliados. **⚠️ Seed de bancos desactualizado** — falta al menos R4 y posiblemente otros bancos activos; hay que actualizar el `INSERT` contra una fuente vigente antes de aplicar esta migración en cualquier ambiente (ver nota en "Diseño de tablas").
- `proyecto-iv-mh/data/bd/database.sql` y `proyecto-iv-mh/data/backup/backup-2026-01-09_01-31.sql` (catálogo legado `tglo_METODO_PAGO` — 13 filas con flags `es_*_obligatorio`; fuente de los métodos y de los flags `is_mandatory_*` de `payment_methods`. `Cargo` se reclasificó como categoría `CHARGE`; `Nota de Crédito` quedó fuera de ambos catálogos por ahora, ver nota en "Fuera de alcance / futuro". El mismo `database.sql` trae también `tglo_BANCO`, catálogo legado de bancos venezolanos con código SUDEBAN — fuente del seed de `banks` en V116)
- `optibienestar-360-backend/src/main/resources/db/migration/V84__currencies.sql` (plantilla exacta de schema/patrón reusada para `banks` en V116)
- `optibienestar-360-backend/src/main/java/.../modules/promoter/service/CommissionPayoutService.java` (ya persiste `exchange_rate_at_paid` al pasar a `PAID` — coordinar con el nuevo `payment_id`)
- `optibienestar-360-backend/src/main/java/.../modules/promoter/service/CommissionService.java` (setea el snapshot de devengo `exchange_rate_at_earned`)
- `optibienestar-360-backend/src/main/java/.../modules/currency/service/ConversionEnricher.java` (nuevo helper `officialRateAt(currency, instant)` — reusar para tasas de `payments`/`payment_lines`)
- `optibienestar-360-backend/src/main/java/.../modules/payment/dto/PaymentDto.java` / `mapper/PaymentMapper.java` (ya expone `currency_Uuid`/`currency_Display`/`convertedCurrency_Uuid`/`_Display` vía `DisplayRef`)
- `optibienestar-360-backend/src/main/java/.../modules/promoter/dto/CommissionDto.java` / `mapper/CommissionMapper.java` (ya expone `currency` como `DisplayRef` + los 4 campos de FX snapshot)
- `optibienestar-360-frontend/app/pages/dashboard/payments/index.vue`, `[uuid].vue` — patrón de link rápido de moneda (`CommonEntityLinkCell` → `/dashboard/catalogs/currencies?edit={uuid}`) ya implementado, reusar para las pantallas nuevas de §"Pantallas requeridas"
- `optibienestar-360-frontend/app/pages/dashboard/commissions/index.vue` — sección "Diferencial cambiario" ya implementada en el modal de detalle, no tocar al agregar el link a `payment`
- `optibienestar-360-frontend/app/types/payments.ts`, `promoters.ts`, `plans.ts` — tipos ya alineados a los DTOs con `_Display`/FX
- `optibienestar-360-frontend/app/utils/nav.ts` + `app/composables/useNav.ts` — árbol de menú data-driven (`MAIN_NAV`), acá se agrega el grupo `finanzas` y los ítems nuevos de `mis-portales`; incluye el `NavLeaf` de Bancos (pendiente de implementación)
- `optibienestar-360-frontend/app/utils/catalog-registry.ts` — registro de catálogos genéricos (`CATALOGS`/`CATALOGS_IN_VERTICALS`); `payment_categories`, `payment_methods` y `banks` siguen el mismo patrón que ya usa `currencies` (`banks` pendiente de implementación)
- `centro-optico-vicente/.ai/decisions/0015-multi-currency-exchange-rates.md` (§1, `_Display` — ya completado por `2c6794a`/`f26c092`)
- `centro-optico-vicente/.ai/plans/2026-09-16-commission-payouts-collections-plan.md` (investigación original, superseded por este documento)
- `centro-optico-vicente/.ai/plans/2026-09-07-hierarchical-commissions-plan.md` (comisiones jerárquicas, complementario)
- `centro-optico-vicente/.ai/plans/2026-09-17-ally-self-service-user-creation-plan.md` (plan paralelo del mismo día — coordinar numeración de migraciones V115+)

## Verificación al implementar

- Migraciones Flyway V115+ (V114 ya está tomada por `commission_fx_snapshot`; coordinar con el plan de self-service de aliados que también apunta a V115) aplican limpio sobre BD local/staging (`./gradlew flywayMigrate` o equivalente del repo backend).
- `CommissionPayoutService` genera `payments`+`payment_lines` reales, sigue seteando `exchange_rate_at_paid`/`paid_rate_date` correctamente, y las pruebas existentes (`CommissionPayoutServiceTest`) siguen pasando con el nuevo flujo.
- Endpoints de listado de pagos filtran correctamente por `direction` + rol (admin/afiliado/promotor) — probar los 3 casos en el frontend.
- DTOs nuevos (`payment_type_Display`, `direction`, líneas) siguen el mismo patrón `DisplayRef`/`_Uuid`+`_Display` que ya usan `PaymentDto`/`CommissionDto`/`PlanDto` desde `2c6794a` (ADR 0014).
