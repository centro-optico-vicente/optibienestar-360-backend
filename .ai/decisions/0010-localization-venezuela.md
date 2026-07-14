# ADR 0010 — Localización: Venezuela como mercado primario

> **Espejo local** del [ADR 0010 del hub](../../../centro-optico-vicente/.ai/decisions/0010-localization-venezuela.md).
> Se mantiene esta copia dentro del repo backend para que cualquier sesión IA que solo tenga el backend cargado (sin el hub disponible) tenga acceso directo a la decisión.
> Si hay divergencia, el hub es la fuente canónica — actualizar este espejo desde allí.

**Estado:** Aceptado
**Fecha:** 2026-05-24
**Decisores:** equipo OptiBienestar 360

## Contexto

El ecosistema OptiBienestar 360 / Centro Óptico Vicente está pensado **exclusivamente** para operar en Venezuela: el negocio físico está en Venezuela, los afiliados, aliados, titulares y promotores son residentes venezolanos, los pagos manuales se hacen en bolívares y USD (Zelle, principalmente, por contexto cambiario), y la regulación de fondo (salud, consumidor, datos personales) es venezolana.

Sin congelar esto como decisión, cada vertical (backend, frontend, landing, docs) podría adoptar defaults distintos para idioma, zona horaria, moneda, formato de fecha o teléfono, generando inconsistencias visibles al usuario y bugs sutiles (ej. dates en UTC mostradas como si fueran locales, o `Intl.NumberFormat('en-US')` separando miles con coma cuando el usuario espera punto).

[ADR 0009](0009-code-conventions.md) ya estableció que **docs en español, código en inglés**, pero sin especificar el dialecto del español ni los valores de localización aplicables. Esta ADR lo cierra.

## Decisión

### Defaults de localización (toda la stack)

| Eje | Valor canónico | Código / estándar | Notas |
|---|---|---|---|
| **País** | Venezuela | `VE` (ISO 3166-1 alpha-2) | Mercado único; no hay roadmap multi-país |
| **Idioma — docs y UI** | Español de Venezuela | `es-VE` (BCP 47) | Fallback aceptable: `es-419` (Latin American Spanish), luego `es` |
| **Zona horaria** | Caracas | `America/Caracas` (IANA TZDB) | UTC-4 fijo. Venezuela **no observa DST** → sin ambigüedades de hora civil |
| **Moneda primaria** | Bolívar Soberano | `VES` (ISO 4217), símbolo `Bs.` | Cantidades en `NUMERIC(18,2)`; nunca `float`/`double` |
| **Moneda secundaria** | Dólar estadounidense | `USD` | Para pagos Zelle/transferencia; ver ADR 0008 (en el hub) |
| **Formato de número** | `1.234,56` | Punto miles, coma decimal | Convención latinoamericana |
| **Formato de fecha** | `dd/MM/yyyy` | | Hora cuando aplica: `HH:mm` (24h) |
| **Formato datetime** | `dd/MM/yyyy HH:mm` | | Sin segundos en UI; con segundos en logs |
| **Prefijo telefónico** | `+58` | E.164 | Móviles VE: `+58 4XX XXX XXXX`; fijos: `+58 2XX XXX XXXX` |
| **Documento de identidad** | Cédula `V-XXXXXXXX` / RIF `J-XXXXXXXX-X` | | Esquema validable; ver glosario del dominio |

### Dónde se materializa cada default

| Capa | Configuración |
|---|---|
| **Docker Compose** | `TIME_ZONE=America/Caracas` en `env_template.env` (ya aplicado), inyectado como `TZ` en todos los servicios |
| **Backend (Spring Boot)** | `spring.web.locale=es-VE`, `spring.jackson.time-zone=America/Caracas`, `Locale.setDefault(new Locale("es","VE"))` al arrancar |
| **Backend (PostgreSQL)** | DB con `LC_COLLATE='es_VE.UTF-8'` (o `es_ES.UTF-8` si la imagen no soporta `es_VE`); columnas datetime como `TIMESTAMP WITH TIME ZONE` almacenando UTC |
| **Backend (Money)** | `NUMERIC(18,2)` para columnas en VES; never `float`/`double`. Cantidades manipuladas con `BigDecimal` y `RoundingMode.HALF_UP` |
| **Frontend admin / portales (Nuxt)** | Nuxt i18n con `defaultLocale: 'es-VE'`, single locale (no multi-language toggle por ahora) |
| **Frontend (formateo)** | `Intl.NumberFormat('es-VE', { style: 'currency', currency: 'VES' })` para Bs.; `Intl.DateTimeFormat('es-VE', { timeZone: 'America/Caracas' })` |
| **Landing pública** | Contenido en `es-VE`. Meta tags: `<html lang="es-VE">`. Cuando se migre a Nuxt SSG aplica i18n single-locale igual que portales |
| **Templates email** | Cuerpo en `es-VE`; subject sin emojis ni caracteres exóticos para evitar problemas con clientes legacy. Hora en cuerpos en `America/Caracas` con indicador `(hora Venezuela)` |
| **Logs y errores técnicos** | **Inglés** (per [ADR 0009](0009-code-conventions.md)). Las fechas en logs van en UTC ISO-8601 — la traducción a Caracas es responsabilidad del visor |
| **API responses** | Fechas en formato ISO-8601 con offset (`2026-05-24T18:30:00-04:00`), nunca naive. Cantidades como números puros + campo `currency: "VES"`; el frontend formatea |
| **Validación de inputs** | Aceptar fechas y cantidades en formato local (`dd/MM/yyyy`, `1.234,56`); convertir internamente a tipos ISO antes de persistir |

### Lo que NO cambia con esta ADR

- El stack técnico (Spring/Nuxt/Postgres/etc.) sigue 100% en inglés, según [ADR 0009](0009-code-conventions.md).
- Commit messages, código fuente, comentarios, logs: **inglés**.
- El servidor puede vivir físicamente fuera de Venezuela (hoy: Contabo, Alemania) — el deploy regional no altera la localización de la app.

## Alternativas consideradas

### Opción A — `es-LA` / `es-419` como default (sin pinear VE)
- **Pro:** más portable a otros países de LATAM si surge expansión.
- **Contra:** YAGNI — no hay roadmap multi-país. Además, las particularidades VE (RIF, +58, BCV, sin DST) requerirían overrides constantes igual.

### Opción B — Multi-locale con toggle de idioma (`es-VE` + `en-US`)
- **Pro:** accesible a expats / inglesoparlantes.
- **Contra:** audiencia es 100% hispanohablante; el costo de mantener traducciones de UI/email/legales en dos idiomas no se justifica.

### Opción C (elegida) — `es-VE` como locale único, defaults explícitos en cada capa
- **Pro:** consistencia visible, sin sorpresas; setup más simple; código aligned con realidad del negocio.
- **Contra:** si surge expansión regional habrá que refactorizar — costo aceptable para una decisión futura hipotética.

## Consecuencias

### Positivas
- Defaults claros que cada vertical hereda sin re-decidir.
- Evita bugs de TZ (datetime en UTC mostrada como local), de formato (`1,234.56` vs `1.234,56`), de moneda (mostrar USD cuando es Bs. y viceversa).
- Templates de email y errores de validación pueden referirse a montos/fechas en formato familiar al usuario sin gymnastics.
- Logs siguen siendo universalmente legibles (en inglés + UTC) para debugging por IA o devs externos.

### Negativas / a mitigar
- Si en el futuro surge necesidad de expandir fuera de VE (improbable), hay que refactorizar i18n, validadores y formatos. Mitigación: aislar los formateadores en helpers/composables (`formatCurrency()`, `formatDate()`) para que el cambio sea localizado.
- Algunos servicios de terceros (Cloudflare R2, Brevo SMTP) reportan timestamps en UTC en sus dashboards — el equipo debe mentalmente sumar/restar 4h.
- Imagen de Postgres alpine no incluye locale `es_VE.UTF-8` por default; hay que generarlo (`localedef`) o caer a `es_ES.UTF-8` aceptando la pequeña divergencia de collation.

## Cómo aplicar

- **Nuevos componentes:** consumir helpers `formatCurrency()` / `formatDate()` / `formatPhoneNumber()` desde el composable compartido del frontend. En backend: `LocaleResolver` configurado con `es-VE`.
- **PR review:** rechazar hardcoded `'en-US'`, `'$'`, `MM/DD/YYYY`, `UTC` en strings visibles al usuario. Bloquear comparaciones de fechas sin TZ explícito.
- **Migrations Flyway:** cuando crees columnas datetime, usar `TIMESTAMP WITH TIME ZONE`. Para columnas de dinero, `NUMERIC(18,2)`.
- **Validación de inputs:** el backend acepta el formato local (`dd/MM/yyyy`) en endpoints públicos; los endpoints internos/admin pueden requerir ISO-8601.
- **Tests:** los tests de formateo deben verificar el output esperado (`'Bs. 1.234,56'`), no asumir defaults del JVM/Node del runner — fijar `Locale` y `TimeZone` en setup.

## Referencias

- ADR 0008 — Pagos manuales (hub) — implica handling de VES + USD
- [ADR 0009 — Convenciones de código](0009-code-conventions.md) — base sobre idioma código/docs
- `context/business-rules.md` (hub) — reglas de negocio donde aparecen montos y fechas
- `context/domain-glossary.md` (hub) — términos VE-específicos (cédula, RIF, BCV)
- IANA Time Zone Database: `America/Caracas` (UTC-4, sin DST desde 2016)
- ISO 4217 (currency): `VES` Bolívar Soberano
