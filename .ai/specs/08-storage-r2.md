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
optisalud-prod/
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
STORAGE_R2_BUCKET=optisalud-prod
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
storage.r2.bucket=optisalud-dev
```

## Referencias

- [ADR 0007 R2 as S3](../../../centro-optico-vicente/.ai/decisions/0007-r2-as-s3.md)
- AWS SDK for Java v2 docs
- Cloudflare R2 docs: https://developers.cloudflare.com/r2/
