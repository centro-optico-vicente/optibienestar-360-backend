# 15 — Motor de Reportes y Documentos

> Implementa [ADR 0012 cross-stack](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0012-reporting-documents-engine.md).
> Relacionado: [`08-storage-r2.md`](08-storage-r2.md) (R2 + presigned), [`09-smtp.md`](09-smtp.md) (email), [`12-commissions.md`](12-commissions.md) (fuente de cuentas por pagar).

## Alcance

Un motor único que sirve dos familias:

- **Analítica** (`ANALYTICAL_REPORT`) — listados/KPIs → XLSX, CSV, PDF-tabla.
- **Transaccional** (`TRANSACTIONAL_DOCUMENT`) — recibo, cobro, planilla, carnet → PDF, ticket.

## Dependencias nuevas (`build.gradle`)

```groovy
// PDF: Thymeleaf HTML -> PDF (reusa el SpringTemplateEngine de los emails)
implementation 'io.github.openhtmltopdf:openhtmltopdf-core:1.1.22'
implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.22'
// XLSX: escritura streaming, huella mínima
implementation 'org.dhatim:fastexcel:0.18.4'
// CSV: quoting RFC-4180
implementation 'org.apache.commons:commons-csv:1.11.0'
```

> Fijar versiones al día en el momento de implementar. Licencias: openhtmltopdf LGPL; fastexcel y commons-csv Apache-2.0 — compatibles con [ADR 0004](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0004-only-free-tools.md).
> Empacar una TTF Unicode (DejaVu/Noto) en `resources/fonts/` y registrarla en el builder: sin ella los acentos y `Bs.` salen rotos.

## Layout del módulo

```
modules/document/
├── AdminDocumentController.java       # /v1/documents
├── AdminReportController.java         # /v1/admin/reports/*
├── dto/
│   ├── DocumentDto.java
│   ├── DocumentGenerateRequest.java
│   └── DocumentUrlDto.java           # misma forma que PaymentSupportUrlDto
├── entity/GeneratedDocument.java
├── repository/GeneratedDocumentRepository.java
└── service/
    ├── DocumentService.java          # orquestador
    ├── DocumentModel.java            # sealed: TabularModel | TemplateModel
    ├── ReportDataProvider.java       # interfaz
    ├── DocumentRenderer.java         # interfaz
    ├── providers/                    # PaymentReceiptProvider, AccountsPayableProvider, ...
    └── renderers/                    # PdfRenderer, XlsxRenderer, CsvRenderer, TicketPdfRenderer
```

## Interfaces

```java
public interface ReportDataProvider {
    String code();                          // matchea DocumentType.code, único
    DocumentModel fetch(ReportParams params);
}

public interface DocumentRenderer {
    DocumentFormat format();                // PDF | XLSX | CSV | TICKET_PDF
    RenderedDocument render(DocumentModel model, RenderContext ctx);
}

public record RenderedDocument(byte[] content, String contentType, String fileName) {}
```

`DocumentModel` sellado:

```java
public sealed interface DocumentModel permits TabularModel, TemplateModel {}

// Analítico: columnas + filas + agregados -> XLSX/CSV/PDF-tabla
public record TabularModel(String title, List<Column> columns,
                           List<List<Object>> rows, Map<String, Object> aggregates)
        implements DocumentModel {}

// Transaccional: variables + plantilla Thymeleaf -> PDF/ticket
public record TemplateModel(String templateBase, Map<String, Object> vars)
        implements DocumentModel {}
```

## Registros (copiar `JobExecutionService.buildRunnerIndex`)

Ambos registros se construyen en `@PostConstruct` indexando la `List<Bean>` inyectada, y **fallan al arranque ante códigos/formatos duplicados** — mismo contrato que `ScheduledJobRunner`:

```java
@PostConstruct
void buildIndexes() {
    for (ReportDataProvider p : providers) {
        if (providerIndex.putIfAbsent(p.code(), p) != null)
            throw new IllegalStateException("Duplicate ReportDataProvider code: " + p.code());
    }
    for (DocumentRenderer r : renderers) {
        if (rendererIndex.putIfAbsent(r.format(), r) != null)
            throw new IllegalStateException("Duplicate DocumentRenderer format: " + r.format());
    }
}
```

`DocumentService` valida `format ∈ documentType.supportedFormats()` **antes** de renderizar (422 limpio si no).

## Renderizadores

**PDF / TICKET_PDF** — Thymeleaf → HTML → jsoup (a DOM W3C) → `PdfRendererBuilder` (salida pdfbox). El ticket es el mismo renderer con un perfil CSS de ancho 58/80 mm; las plantillas deben ser XHTML limpio y ceñirse a CSS 2.1 + paged-media.

**XLSX** — fastexcel en streaming sobre `TabularModel`: encabezado en negrita + formatos de número/fecha/moneda. Escribir a `ByteArrayOutputStream` (los reportes caben en memoria; si algún día no, pasar a subida en streaming a R2).

**CSV** — commons-csv con `CSVFormat.DEFAULT` sobre `TabularModel`. Prefijar BOM UTF-8 si Excel-VE debe abrirlo con doble clic sin romper acentos.

**Formato de datos** — montos y fechas se formatean con la convención `es-VE` de [ADR 0010](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0010-localization-venezuela.md) (`1.234,56`, `dd/MM/yyyy`, `America/Caracas`). Plantillas con paridad `_es`/`_en`, resueltas como ya lo hace `EmailService.resolveLocalizedTemplate`.

## Storage

Key: `reports/{yyyy}/{MM}/{documentType}/{uuid}-{safeName}.{ext}` — reutilizar `PaymentsService.safeName()`.

Inyectar `ObjectProvider<StorageService>` y degradar igual que pagos: con `storage.r2.enabled=false` el bean no existe → **422 limpio**, sin NPE. Reutilizar `clampTtl` (1 min–1 h, default 5 min) para las presigned.

Configurar la lifecycle rule del prefijo `reports/` en R2: 90 días para exports analíticos; **sin borrado automático** para documentos legales (recibos, planilla), consistente con `payments/`.

## Esquema `generated_documents` (migración `V31`)

Sigue [ADR 0006](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0006-table-conventions.md) y el molde de `V28__notifications.sql` (JSONB + status + índices parciales).

| Columna | Tipo | Notas |
|---|---|---|
| `generated_documents_id` | BIGINT identity PK | interno |
| `uuid` | UUID unique | externo |
| `document_type` | VARCHAR(80) | abierto; resuelve al provider (como `scheduled_jobs.code`) |
| `category` | VARCHAR(20) | CHECK `ANALYTICAL_REPORT`,`TRANSACTIONAL_DOCUMENT` |
| `format` | VARCHAR(16) | CHECK `PDF`,`XLSX`,`CSV`,`TICKET_PDF` |
| `title` | VARCHAR(200) | |
| `file_name` | VARCHAR(200) | |
| `content_type` | VARCHAR(120) | |
| `size_bytes` | BIGINT | |
| `storage_key` | TEXT | NULL mientras `PENDING`/`GENERATING`/`FAILED` |
| `status` | VARCHAR(20) | CHECK `PENDING`,`GENERATING`,`READY`,`FAILED`,`EXPIRED`; default `PENDING` |
| `params` | JSONB | parámetros del reporte → re-generación + auditoría |
| `source_module` | VARCHAR(40) | procedencia (payment, membership, commission) |
| `source_entity_uuid` | UUID | idempotencia: "el recibo de este pago" |
| `trigger_source` | VARCHAR(20) | CHECK `MANUAL`,`EVENT`,`SCHEDULED` |
| `generated_by` | UUID | NULL para system/scheduled |
| `expires_at` | TIMESTAMPTZ | |
| `error_message` | TEXT | |
| + audit `BaseEntity` | | `is_active`, created/updated |

**CHECKs de coherencia:** `READY ⇒ storage_key NOT NULL` · `FAILED ⇒ error_message NOT NULL`.

**Índices parciales:**
- `(source_module, source_entity_uuid, document_type)` — "¿ya se generó?"
- `(document_type, created_at DESC)` — listado admin
- `(generated_by, created_at DESC)` — "mis documentos"
- `(status) WHERE status IN ('PENDING','GENERATING')` — worker async
- `(expires_at) WHERE status='READY'` — barrido de expirados

## Endpoints

### Analíticos — `AdminReportController`

| Método | Ruta | Authority |
|---|---|---|
| GET | `/v1/admin/reports/{memberships,allies,commissions}` | `REPORT_VIEW_DASHBOARD` |
| GET | `/v1/admin/dashboard` | `REPORT_VIEW_DASHBOARD` |
| GET | `/v1/admin/reports/export?type=…&format=csv\|xlsx` | `REPORT_EXPORT` |

> El `format=csv` reservado en los checklists **se absorbe** como el CSV renderer del motor: se extiende el enum a `csv|xlsx`, no se duplica el contrato.

### Documentos — `AdminDocumentController`

| Método | Ruta | Authority | Notas |
|---|---|---|---|
| POST | `/v1/documents` | `DOCUMENT_GENERATE` | `{documentType, format, params, delivery:{mode,to?}}` → 200 o **202** |
| GET | `/v1/documents` | `DOCUMENT_VIEW_ALL` | paginado + RSQL (`documentType`,`sourceModule`,`status`,`createdAt`) |
| GET | `/v1/documents/{uuid}` | `DOCUMENT_VIEW_ALL` \| `DOCUMENT_VIEW_OWN` | metadata + status (target de polling) |
| GET | `/v1/documents/{uuid}/download?ttlMinutes=` | `DOCUMENT_VIEW_ALL` \| `DOCUMENT_VIEW_OWN` | acuña presigned fresca (`clampTtl`) |
| POST | `/v1/documents/{uuid}/email` | `DOCUMENT_GENERATE` | (re)envío adjunto o enlace |

**Azúcar en controllers de entidad**, envolviendo `DocumentService`:
`POST /v1/admin/payments/{uuid}/receipt?format=pdf|ticket&delivery=…`

**Permisos.** Dominio `DOCUMENTS` nuevo (`DOCUMENT_VIEW_ALL`, `DOCUMENT_VIEW_OWN`, `DOCUMENT_GENERATE`) sembrado en `V31` con el patrón V22/V28. `DOCUMENT_VIEW_OWN` se distribuye como `NOTIFICATION_VIEW_OWN` (SYSTEM + ADMIN + AFILIADO + PROMOTOR + ALIADO).

> **Auth efectiva = permiso de documento ∧ permiso de la entidad origen.** Un `PAYMENT_RECEIPT` exige además `PAYMENT_VIEW_ALL`/`PAYMENT_VIEW_OWN`. Un AFILIADO nunca recibe `REPORT_EXPORT`.

## Entrega

`DocumentService.deliver(rendered, delivery)` con tres modos:

- **`DOWNLOAD`** (default) → subir a R2 → `DocumentUrlDto{url, expiresAt, expiresInSeconds, fileName, contentType, sizeBytes}` (misma forma que `PaymentSupportUrlDto`).
- **`EMAIL_ATTACHMENT`** → `EmailService.sendTemplatedWithAttachment(...)`. **Extensión necesaria** — el `MimeMessageHelper(msg, true, "UTF-8")` ya es multipart:

```java
helper.addAttachment(fileName, new ByteArrayResource(bytes), contentType);
```

- **`EMAIL_LINK`** → el correo enlaza al endpoint autenticado `GET /v1/documents/{uuid}/download` (acuña presigned fresca en cada llamada). **No** hornear URLs firmadas largas: el presign de R2 topa a 7 días y "acceder después" debe sobrevivirlo.

**Matriz por defecto:** transaccional pequeño → adjunto + persistido · analítico grande → enlace (nunca adjuntar) · export interactivo → presigned al SPA.

**Nunca streamear bytes al SPA**: `useApi` es JSON-only y `window.open` no manda `Authorization`.

## Async y programados

**Híbrido sync-then-202** copiando `max_sync_seconds` de `JobExecutionService`: si excede el presupuesto → **202** + `documentUuid` (`status=GENERATING`); el SPA hace polling a `GET /v1/documents/{uuid}` hasta `READY`. Ejecutar en un `reportExecutor` dedicado.

**Programados** — un `ScheduledJobRunner` por reporte recurrente + fila semilla en `scheduled_jobs` (cron `America/Caracas`):

```java
@Component
public class AccountsPayableReportJobRunner implements ScheduledJobRunner {
    public String code() { return "ACCOUNTS_PAYABLE_REPORT"; }
    public JobRunResult run() { /* generar -> persistir -> email con enlace a finanzas */ }
}
```

> **Cuentas por pagar = comisiones a promotores.** El provider reusa el agrupamiento de `CommissionPayoutService` (comisiones `PENDING` por promotor y ciclo). No hay módulo de payables de proveedores.

## Correo

Usar el `EmailService` `@Async` actual. **Migrar a la cola persistente `notifications`** (tabla V28) cuando aterrice `NotificationService` (vertical-9): el motor de documentos **no** debe re-implementar reintentos ni backoff.

## Testing

- Unit por renderer: `TabularModel` fijo → aserciones sobre bytes (PDF: cabecera `%PDF`; XLSX: leer con POI en test-scope; CSV: string exacto con quoting).
- Registro: duplicar `code()`/`format()` debe fallar el `contextLoads`.
- `DocumentService`: formato no soportado → 422; R2 apagado (`ObjectProvider` vacío) → 422 limpio, sin NPE.
- Email con adjunto: GreenMail (ya previsto en [`09-smtp.md`](09-smtp.md)) verificando el `Content-Disposition: attachment`.
- Async: generación que excede el presupuesto → 202 + transición `GENERATING → READY`.
