# 08 — Storage en Cloudflare R2 (vía API S3)

> Implementa [ADR 0007 cross-stack](../../../centro-optico-vicente/.ai/decisions/0007-r2-as-s3.md).

## Configuración

`core/config/S3Config.java`:

```java
@Configuration
@RequiredArgsConstructor
public class S3Config {
    @Value("${storage.r2.endpoint}") private String endpoint;
    @Value("${storage.r2.access-key}") private String accessKey;
    @Value("${storage.r2.secret-key}") private String secretKey;
    @Value("${storage.r2.region:auto}") private String region;

    @Bean
    S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // requerido por R2
                .build())
            .build();
    }

    @Bean
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }
}
```

## StorageService

`common/service/StorageService.java`:

```java
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${storage.r2.bucket}") private String bucket;

    public String upload(String key, MultipartFile file) throws IOException {
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build(),
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
        return key;
    }

    public InputStream download(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public URL generatePresignedUrl(String key, Duration ttl) {
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .build();
        return presigner.presignGetObject(req).url();
    }

    public URL generatePresignedUploadUrl(String key, Duration ttl, String contentType) {
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build())
            .build();
        return presigner.presignPutObject(req).url();
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
```

## Estructura de keys

```
optibienestar-360-prod/
├── allies/{ally_id}/logo.{ext}
├── members/{member_id}/
│   ├── documents/{ts}_{type}.{ext}
│   └── medical-records/{ts}_{description}.pdf
├── payments/{payment_id}/support_{ts}.{ext}
├── catalog/services/{service_id}/{image_id}.{ext}
└── backups/  (manejado por servicio backup, no por backend)
```

## Validación de uploads

```java
@Documented
@Retention(RUNTIME)
@Target({PARAMETER, FIELD})
@Constraint(validatedBy = ValidatedFileValidator.class)
public @interface ValidatedFile {
    String message() default "Invalid file";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    int maxSizeMB() default 5;
    String[] allowedMimes() default {"image/jpeg", "image/png", "application/pdf"};
}

public class ValidatedFileValidator implements ConstraintValidator<ValidatedFile, MultipartFile> {
    // ... verifica MIME real (no extensión), tamaño max
}
```

Tamaños recomendados por tipo:

| Tipo | Max MB |
|---|---|
| Logos de aliados | 2 |
| Documentos identidad | 5 |
| Soportes de pago | 3 |
| Expedientes médicos | 10 |
| Avatares usuarios | 1 |

## Variables de entorno

```
STORAGE_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
STORAGE_R2_ACCESS_KEY=<from R2 dashboard>
STORAGE_R2_SECRET_KEY=<from R2 dashboard>
STORAGE_R2_BUCKET=optibienestar-360-prod
STORAGE_R2_REGION=auto
```

## Dev local

Usar **localstack** o **MinIO** simulando S3:

```yaml
# docker-compose.dev.yaml
services:
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
```

`application-dev.properties`:
```properties
storage.r2.endpoint=http://localhost:9000
storage.r2.access-key=minioadmin
storage.r2.secret-key=minioadmin
storage.r2.bucket=optibienestar-360-dev
```

## Plan: gestión general de archivos por visibilidad (pendiente de implementar)

> Estado actual real del código (verificado 2026-08-14): `S3Config`/`StorageService` ya existen y funcionan con **un solo bucket privado**, gated por `storage.r2.enabled`. El único flujo cableado de punta a punta es el comprobante de pago (`Payment.supportFileUrl` + `PaymentsService` + `AdminPaymentController`), con TTL clamped (`MIN=1min`, `MAX=1h`, `DEFAULT=5min`) definido ahí mismo. `MemberDocument` (entidad+repo) existe sin service/controller. `Ally.logoUrl` es solo columna, sin flujo de subida. `AllyService` (catálogo) no tiene ningún campo de imagen. No existe `AllyDocument`. No hay validación de MIME/tamaño en ningún flujo real todavía (la sección "Validación de uploads" de arriba es solo diseño, no implementado). El motor de reportes (ADR 0012 / spec 15) sigue en 0% de implementación y **no se toca en este plan**.

Este plan generaliza lo anterior antes de seguir agregando flujos puntuales, con 4 visibilidades: `PUBLIC`, `TEMPORARY`, `INTERNAL`, `CONFIDENTIAL`. Carpeta por tabla + subcarpeta por uuid del dueño, ej. `confidential/payments/{uuid}/...`, `public/services/{uuid}/...`.

### 1. Dos buckets

- **Privado** (el actual): todo acceso vía presigned URL con TTL clamped. Cubre `CONFIDENTIAL`, `INTERNAL`, `TEMPORARY`.
- **Público** (nuevo, `storage.r2.public-bucket` + `storage.r2.public-base-url`, dominio público de R2): objetos servidos directo por URL sin presign — necesario para `<img src>` de catálogo sin overhead de redirect por request. Cubre `PUBLIC` (R2 no permite exponer solo un prefijo de un bucket privado como público).

`S3Config`/`StorageService` se extienden para aceptar el bucket como parámetro en `upload`/`delete`/`exists`/`generatePresignedUrl`, en vez de tenerlo fijo. Siguen bajo el mismo `@ConditionalOnProperty(storage.r2.enabled=true)`.

### 2. Enum + key builder + TTL compartidos (`common/storage/`)

- `FileVisibility { PUBLIC, TEMPORARY, INTERNAL, CONFIDENTIAL }`.
- `StorageKeyBuilder.build(visibility, resourceTable, resourceUuid, fileName)` → `"{visibility}/{resourceTable}/{resourceUuid}/{fileName}"` (filename sanitizado, mismo criterio que ya usa `PaymentsService` para el support file).
- `PresignedUrlPolicy`: extrae las constantes/clamp que hoy viven duplicadas en `PaymentsService` para que todo nuevo consumidor (member docs, ally docs, y a futuro reportes) las reutilice. `PaymentsService` pasa a delegar aquí sin cambiar su comportamiento externo.

### 3. Validación configurable por scope (tabla `file_type_policy`)

Una fila por `FileVisibility`:
- `visibility` (PK), `mode` (`ALLOWLIST` | `DENYLIST`), `extensions` (`text[]`), `max_size_bytes`.

`FileValidationService.validate(visibility, fileName, contentType, size)`:
1. Carga la política de esa visibilidad (cacheable).
2. `DENYLIST` → rechaza si la extensión está en `extensions`; acepta el resto.
3. `ALLOWLIST` → acepta solo si la extensión está en `extensions`; rechaza el resto.
4. Rechaza si `size > max_size_bytes`.

Defaults sembrados por migración (seed data):
- `PUBLIC` → `ALLOWLIST` (`jpg,jpeg,png,webp`, 5MB).
- `CONFIDENTIAL` → `ALLOWLIST` (`pdf,jpg,jpeg,png`, 10MB).
- `TEMPORARY` → `ALLOWLIST` (`pdf,xlsx,csv`, 20MB) — para el futuro motor de reportes.
- `INTERNAL` → `DENYLIST` (`exe,sh,py,bat,dll,jar`, 20MB).

Se aplica también a `PaymentsService.attachSupportFile`, que hoy no valida nada.

### 4. Documentos confidenciales — mecanismo genérico reutilizable

El caso "adjuntar un archivo a un registro y poder listarlo después" se repite en varios dominios que no tienen nada que ver entre sí: cédula/foto de afiliado, contrato/RIF de aliado, comprobante de transferencia de una comisión pagada, etc. Construir una entidad+service+controller bespoke por cada dominio (al estilo copy-paste de `MemberDocument`) no escala — cada caso nuevo repetiría el mismo CRUD. Por eso, en vez de crear `AllyDocument` como entidad propia (como se planteaba en una versión anterior de este documento), se generaliza:

- Tabla/entidad única `attached_file` (`common/storage/AttachedFile.java`): `uuid` (PK), `owner_table` (varchar, ej. `"members"`, `"allies"`, `"commissions"`), `owner_uuid`, `visibility` (`FileVisibility`), `category` (varchar libre — el "tipo" dentro de ese dominio, ej. `ID_FRONT`, `CONTRACT`, `BANK_TRANSFER_RECEIPT`), `file_key`, `file_name`, `mime_type`, `size_bytes`, `uploaded_by`, `uploaded_at`, `shared` (boolean, default `false` — ver 4.2). Índice por `(owner_table, owner_uuid)`.
- `AttachedFileRepository` genérico: `findByOwnerTableAndOwnerUuid(...)`.
- `AttachedFileService` genérico — un solo lugar con la lógica de subir (`StorageKeyBuilder` + `FileValidationService`), listar con checklist (ver 4.1), generar URL presignada on-demand (`PresignedUrlPolicy`) y borrar. Recibe `ownerTable`/`ownerUuid`/`visibility`/`category` como parámetros — no conoce reglas de negocio de ningún dominio.
- **La autorización queda a cargo del controller de cada dominio**, no del service genérico: `AdminMemberController`, `AdminAllyController`, `AdminCommissionController`, etc. exponen su propio endpoint delgado (ej. `POST /v1/admin/commissions/{uuid}/attachments`) que primero valida el permiso de ese dominio (`COMMISSION_VIEW`/`COMMISSION_MANAGE`, ya existentes) y luego delega en `AttachedFileService` pasando su `ownerTable` fijo (nunca tomado del request, para que un cliente no pueda adjuntar un archivo a una tabla arbitraria). Esto es lo mismo que ya se documentaba como regla en ADR 0012: "permiso del documento ∧ permiso de la entidad dueña".
- Cada dominio nuevo que necesite adjuntar archivos (comisiones, futuros casos) solo agrega su controller delgado + el permiso de su propio dominio si no existe ya — no repite storage/validación/checklist.
- **`MemberDocument` no se migra** en este plan (ya existe como entidad+tabla propia, shipeada) — se le sigue dando su propio service/controller (`MemberDocumentService`/`AdminMemberDocumentController`) pero internamente reutiliza `StorageKeyBuilder`/`PresignedUrlPolicy`/`FileValidationService` igual que el mecanismo genérico, para no duplicar esa parte. Es la única excepción bespoke, por compatibilidad con lo ya construido.
- Comisiones (ejemplo del usuario: comprobante de transferencia bancaria) usa `attached_file` con `owner_table="commissions"`, `category="BANK_TRANSFER_RECEIPT"`, `visibility=CONFIDENTIAL` — sin crear ninguna tabla nueva.

#### 4.1. Listado por dueño + checklist de recaudos requeridos

`GET /v1/admin/{recurso}/{uuid}/documents` (o `/attachments`, según dominio) no es solo "listar lo que hay subido" — el caso de uso real es **validar que el expediente esté completo** (afiliado con cédula+foto antes de activarlo, aliado con contrato firmado, comisión con su comprobante) o simplemente **ver qué se adjuntó** cuando no hay noción de "requerido" (ej. comisiones: 0 o 1 comprobante, no una checklist fija). `AttachedFileService.list(ownerTable, ownerUuid)` devuelve siempre el array plano de metadata; `AttachedFileService.listWithChecklist(ownerTable, ownerUuid, Set<String> requiredCategories)` es el método adicional que arma la checklist para los dominios que sí tienen recaudos obligatorios:

```json
{
  "documents": [
    {"category": "ID_FRONT", "status": "UPLOADED", "fileUuid": "...", "fileName": "...", "uploadedAt": "..."},
    {"category": "ID_BACK", "status": "MISSING"},
    {"category": "MEMBER_PHOTO", "status": "UPLOADED", "fileUuid": "...", ...}
  ],
  "complete": false
}
```

- `requiredCategories` por dominio se define en código, en el controller/service de ese dominio (ej. `MemberDocumentService.REQUIRED_TYPES = {ID_FRONT, ID_BACK, MEMBER_PHOTO}`, futuro `AllyController` podría pasar `{LEGAL_ID, CONTRACT}`) — no en BD, ya que es una regla de negocio estable, no configuración editable por admin. Comisiones, al no tener recaudo obligatorio, simplemente llama `list(...)` sin checklist.
- Una `category` subida que no está en `requiredCategories` (ej. `OTHER`) aparece igual en el listado, sin afectar `complete`.
- `complete = true` solo si todas las `requiredCategories` tienen `status = UPLOADED`. Este flag es el que usarían las pantallas de aprobación de afiliación/aliado para bloquear el botón de aprobar si el expediente está incompleto.
- No se listan URLs presignadas en este endpoint (serían N presigned URLs por cada carga de pantalla, caro e innecesario) — cada fila trae solo metadata; la URL se pide bajo demanda con `GET .../{fileUuid}/url` cuando el admin realmente hace click en "ver".

#### 4.2. Permisos granulares por acción + archivos propios vs compartidos

`FileVisibility` (`PUBLIC/TEMPORARY/INTERNAL/CONFIDENTIAL`) controla el storage/bucket — es una capa. La autorización de negocio es otra capa, independiente, resuelta por cada dominio con 4 authorities separadas (mismo patrón `_VIEW_ALL`/`_VIEW_OWN` que ya usa el proyecto, ej. `NOTIFICATION_VIEW_OWN`):

| Authority | Qué habilita |
|---|---|
| `<DOMINIO>_DOCUMENT_VIEW_ALL` | Ver/listar todos los archivos del registro, propios y ajenos |
| `<DOMINIO>_DOCUMENT_VIEW_OWN` | Ver/listar solo los archivos que subió el propio usuario + los marcados como compartidos |
| `<DOMINIO>_DOCUMENT_UPLOAD` | Subir archivos al registro |
| `<DOMINIO>_DOCUMENT_DELETE` | Eliminar archivos (propios o ajenos, no se distingue delete-own/delete-all salvo que un dominio concreto lo necesite) |

Al ser authorities independientes, un rol puede tener el permiso de ver el registro (`<DOMINIO>_VIEW`) sin ninguno de los `_DOCUMENT_*` (ve el registro, no ve archivos); ver archivos sin `_UPLOAD` (solo lectura); subir sin `_DELETE` (sube pero no borra); etc. — se define por rol, no por código.

**Archivo propio → compartido:** columna `shared` (boolean, default `false`) en `attached_file`, junto a `uploaded_by`. Es una ACL de negocio, no de storage:

- Al subir, `shared=false`: solo lo ve el uploader (con `_DOCUMENT_VIEW_OWN`) o alguien con `_DOCUMENT_VIEW_ALL`.
- `PATCH /v1/admin/{recurso}/{uuid}/documents/{fileUuid}/share` (toggle `shared`): permitido al propio uploader o a quien tenga `_DOCUMENT_DELETE` (nivel de gestión del recurso) — no se crea una authority nueva solo para compartir.
- Una vez `shared=true`, cualquiera con `_DOCUMENT_VIEW_OWN` también lo ve (no solo `_VIEW_ALL`) — deja de ser "privado del que lo subió" y pasa a ser visible para cualquiera con acceso de lectura a los archivos del registro.
- `AttachedFileService.list(ownerTable, ownerUuid, currentUserUuid, hasViewAll)`: si `hasViewAll` → todas las filas; si no → `WHERE uploaded_by = currentUserUuid OR shared = true`. El filtro vive en el service genérico (no hay que repetirlo por dominio); el controller de cada dominio solo decide cuál de las dos authorities (`VIEW_ALL`/`VIEW_OWN`) exige y con qué `hasViewAll` boolean llama al service.
### 5. Imágenes públicas de catálogo (`AllyService`)

- Migración: columna `image_key` (bucket público) en `ally_service`. No se persiste la URL completa — se deriva en el mapper como `publicBaseUrl + imageKey`.
- Sube al bucket público con `StorageKeyBuilder.build(PUBLIC, "services", uuid, fileName)`, valida con `FileValidationService`.
- `POST/DELETE /v1/admin/ally-services/{uuid}/image`, protegidos con **permisos propios** `ALLY_SERVICE_IMAGE_UPLOAD` / `ALLY_SERVICE_IMAGE_DELETE` (dominio `ALLIES`) — no se reusa `ALLY_UPDATE`. Reusarlo mezclaría "quién puede editar precio/nombre/descripción del servicio" con "quién puede gestionar su imagen", rompiendo el mismo principio de authorities independientes que se aplicó a los documentos de afiliados/aliados (§4.2): un rol de moderación de contenido debería poder gestionar imágenes sin poder tocar precios, y viceversa.
- No se agrega `ALLY_SERVICE_IMAGE_VIEW_*`: la imagen es `PUBLIC` por diseño (sin presign, visible sin auth una vez el servicio está `published && APPROVED`), así que no hay nada que autorizar para "verla" — quien ya tiene `ALLY_VIEW_ALL` ve el campo `imageKey`/`imageUrl` en el detalle del servicio como cualquier otro campo.
- DTOs públicos de listado/detalle se extienden con `imageUrl` (nullable, sin presign — el servicio ya requiere `published && APPROVED` para aparecer).

### 6. Enlaces de descarga con vencimiento propio (días), no solo el TTL de R2

El TTL de `PresignedUrlPolicy` (1min–1h) sirve para el caso "usuario autenticado en el SPA pide ver un archivo ahora". No sirve para "reporte de cotización enviado por correo, válido 10 días" — porque R2 tiene un tope duro de **7 días** para presigned URLs, y un link de días de validez no puede ser una sola presigned URL horneada en el correo (si expira antes de que el destinatario lo abra, o si se pasa del máximo de R2, se rompe).

Se agrega un mecanismo separado, desacoplado de `PresignedUrlPolicy`:

- Tabla `file_download_link`: `token` (UUID/opaco, PK, va en la URL pública), `visibility`, `resource_table`, `resource_uuid`, `file_key`, `expires_at` (fecha/hora de negocio — puede ser días), `revoked_at` (nullable), `created_by`, `created_at`, `max_downloads` (nullable, opcional para limitar reintentos).
- `FileDownloadLinkService.createLink(fileRef, Duration businessValidity)`: crea la fila y devuelve la URL pública `{publicBaseUrl}/v1/files/links/{token}` — esta es la URL que se manda por correo o se muestra como "enlace de descarga", **nunca la presigned URL directa**.
- Endpoint público `GET /v1/files/links/{token}` (sin auth — el secreto es el token en sí):
  1. Busca el link; si no existe, está revocado, o `expires_at` ya pasó → `410 Gone`.
  2. Si sigue vigente, remintea una presigned URL fresca de corta duración (vía `PresignedUrlPolicy`, ej. 5 min) contra el `file_key` real y responde `302 Found` al presigned URL (no JSON — este flujo se abre desde un cliente de correo, no desde el SPA, así que no hay Authorization header que enviar).
  3. Si `max_downloads` está seteado, incrementa un contador y lo bloquea al llegar al tope.
- Esto es lo que reutilizará el motor de reportes (ADR 0012, fuera de alcance) para "descarga por enlace" y "adjunto por correo con expiración de X días" — aquí solo se deja el mecanismo genérico listo, sin acoplarlo a `GeneratedDocument` todavía.

### 7. `INTERNAL`/`TEMPORARY` — solo andamiaje, sin consumidor todavía

El enum, el key builder y `file_type_policy` ya soportan ambos valores de forma uniforme. No se construye controller/entidad para ellos en este plan — quedan listos para que el motor de reportes (ADR 0012, otra vertical) los reutilice después (`TEMPORARY` con `expiresAt` para cleanup, `INTERNAL` para archivos autenticados sin dueño de negocio específico). No se crea permiso `FILE_VIEW_INTERNAL` todavía por no tener consumidor real (evita permisos huérfanos).

### Migraciones nuevas (revisar próximo `V{n}` libre en `db/migration/`)

1. Tabla `attached_file` (mecanismo genérico: `owner_table` + `owner_uuid` + `visibility` + `category` + metadata de archivo).
2. Columna `image_key` en `ally_service`.
3. Filas de permisos nuevos (`MEMBER_DOCUMENT_VIEW/UPLOAD`; el resto de dominios que adjunten vía `attached_file` reusan sus permisos de dominio ya existentes, ver sección 4).
4. Tabla `file_type_policy` + seed de las 4 filas.
5. Tabla `file_download_link` (token, expiración de negocio, revocación, contador de descargas).
6. Permisos `ALLY_SERVICE_IMAGE_UPLOAD`/`ALLY_SERVICE_IMAGE_DELETE` (dominio `ALLIES`) — dedicados a la imagen de catálogo, no reusan `ALLY_UPDATE` (§5).

### Verificación

- `./gradlew test` con mocks de `StorageService` para los nuevos services, siguiendo el patrón de tests de `PaymentsService`.
- Con `storage.r2.enabled=false`: subir documento no debe romper (guarda metadata, igual que hoy con `attachSupportFile`).
- Con MinIO local (`storage.r2.enabled=true`): subir documento de afiliado y confirmar key `confidential/members/{uuid}/...`; pedir URL presignada y confirmar TTL clamped; subir imagen de servicio y confirmar que el endpoint público devuelve `imageUrl` accesible sin token; confirmar que `@PreAuthorize` bloquea documentos sin el permiso nuevo.
- Adjuntar un comprobante de transferencia a una comisión pagada vía `attached_file` (`owner_table="commissions"`, `category="BANK_TRANSFER_RECEIPT"`) y confirmar que `AttachedFileService.list("commissions", uuid)` lo devuelve sin necesidad de ninguna tabla/entidad nueva — validando que el mecanismo genérico sirve para un dominio no contemplado explícitamente en este plan.
- Con dos usuarios distintos, ambos con `_DOCUMENT_VIEW_OWN` (sin `_VIEW_ALL`): el usuario B no debe ver el archivo `shared=false` subido por el usuario A; tras hacer `PATCH .../share`, el usuario B sí debe verlo en su listado.
- Crear un `file_download_link` con `expiresAt` en el pasado y confirmar que `GET /v1/files/links/{token}` responde `410 Gone`; con `expiresAt` futuro, confirmar el `302` a una presigned URL fresca (distinta en cada llamada, TTL corto pese a que la validez de negocio sea de varios días).

## Referencias

- [ADR 0007 R2 as S3](../../../centro-optico-vicente/.ai/decisions/0007-r2-as-s3.md)
- AWS SDK for Java v2 docs
- Cloudflare R2 docs: https://developers.cloudflare.com/r2/
