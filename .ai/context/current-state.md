# Estado actual del backend (snapshot)

> **Actualizado:** 2026-05-31
>
> Qué existe HOY en `optibienestar-360-backend`. Actualizar al cierre de cada sesión productiva.

## Resumen

| Aspecto | Estado |
|---|---|
| Bootstrap base | ✅ Spring Boot 4.0.6 + Java 25 + Gradle 9.4.1 |
| Estructura paquetes | ✅ `core/`, `security/`, `common/`, `modules/{auth,catalog,contact}` |
| Configuración seguridad | ✅ `SecurityConfig` + `JwtService` + filters + `RedisTokenBlacklistService` + 49+1 permisos |
| Migraciones Flyway | ✅ V1–V10 aplicadas (extensions, audit, app_roles, contact, users+roles+permission_domains, seeds, locations, personal, health catalogs) |
| Entidades JPA | ✅ User, Role, Permission, **PermissionDomain**, UserRole, UserPasswordHistory, SecurityPolicy, UserSessionLog, ContactMessage + 10 catálogos (Country/State/City/Gender/DocumentType/MaritalStatus/Occupation/MedicalSpecialty/ServiceCategory/AllyType) |
| Endpoints REST | ✅ `AuthController` (login/refresh/logout/recover), `MeController`, `AdminUserController` (CRUD + RSQL), `AdminRoleController` (read-only), **`PermissionController`** (GET catálogo), `ContactController`, `AdminCatalogsController`, `PublicCatalogsController`, `SystemInfoController` |
| Tests | ⚠️ Sin tests de integración aún (solo `contextLoads()` trivial) |
| Dockerfile | ✅ Multi-stage Alpine + Debian |
| CI/CD | ✅ GitHub Actions: build + publish Docker Hub |
| Configuración SMTP | ✅ `EmailService` + templates (recuperación de contraseña) |
| Configuración Redis | ✅ Token blacklist + refresh token store |
| Configuración S3/R2 | ✅ Cliente configurado (MinIO en dev, R2 en prod) |
| Swagger/OpenAPI | ✅ Configurado en `/v1/swagger-ui` con redirects 301 |

## Detalle

### `build.gradle` (dependencias instaladas)

```groovy
// Spring Boot 4.0.6
spring-boot-starter-web (sin Tomcat)
spring-boot-starter-jetty
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation
spring-boot-starter-cache
spring-boot-starter-data-redis
spring-boot-starter-actuator
spring-boot-starter-aspectj

// DB
org.postgresql:postgresql:42.7.11

// Auth
io.jsonwebtoken:jjwt-api:0.13.0
io.jsonwebtoken:jjwt-impl:0.13.0
io.jsonwebtoken:jjwt-jackson:0.13.0

// Cloud Storage (compatible con R2 vía endpoint)
io.awspring.cloud:spring-cloud-aws-starter-s3:4.0.2

// API docs
org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3

// Tools
org.mapstruct:mapstruct:1.6.3
io.github.perplexhub:rsql-jpa-spring-boot-starter:7.0.0
com.fasterxml.jackson.module:spring-boot-jackson2

// Test
spring-boot-starter-test
com.h2database:h2
```

### Configuraciones actuales

**`application.properties`:**
```properties
spring.application.name=optibienestar360
```

**`application-dev.properties`:**
```properties
server.port=${SERVER_PORT:8080}
server.jetty.threads.min=10
server.jetty.threads.max=100

spring.datasource.url=jdbc:postgresql://localhost:5432/optibienestar-360
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}
spring.jpa.hibernate.ddl-auto=update    # ⚠️ debe cambiarse a validate
spring.cache.type=simple

spring.cloud.aws.credentials.access-key=${S3_ACCESS_KEY_ID}
spring.cloud.aws.credentials.secret-key=${S3_SECRET_ACCESS_KEY}
spring.cloud.aws.region.static=${S3_REGION:us-east-1}

logging.level.org.springframework=DEBUG
logging.level.com.fenixcore=INFO
```

**`application-prod.properties`:** similar, con `ddl-auto=validate`, `cache.type=redis`.

### Código actual

`OptiBienestar360Application.java`:
```java
@SpringBootApplication
public class OptiBienestar360Application {
    public static void main(String[] args) {
        SpringApplication.run(OptiBienestar360Application.class, args);
    }
}
```

### Estructura del repo

```
optibienestar-360-backend/
├── CLAUDE.md                                  ← creado 2026-05-18
├── .ai/                                       ← creado 2026-05-18
├── build.gradle, settings.gradle
├── README.md, HELP.md
├── src/main/
│   ├── java/com/fenixcore/optibienestar360/
│   │   └── OptiBienestar360Application.java     ← ÚNICA clase
│   └── resources/
│       ├── application.properties
│       ├── application-dev.properties
│       └── application-prod.properties
├── src/test/                                  ← un test trivial
├── .vscode/                                   ← config Java VS Code
└── .github/
    └── java-upgrade/                          ← script upgrade Java
```

## Próximos pasos

Ver [`../checklist.md`](../checklist.md) sección FASE 1 Tarea 1.7. Empezar por:
1. Agregar Flyway al `build.gradle`
2. Crear migration `V1__initial_extensions.sql`
3. Cambiar `ddl-auto=validate` en TODOS los profiles
4. Crear estructura de paquetes `core/`, `security/`, `common/`, `modules/`
5. Crear `BaseEntity` abstracta

## Cambios recientes

- **2026-05-31** — Tabla `permission_domains` (10 dominios UI con `code`/`name`/`icon`/`display_order`) consolidada en V5; nuevo permiso `ROLE_PERMISSION_EDIT` consolidado en V6 (asignado solo a SYSTEM); `permissions.domain` (texto) → `domain_id` (FK); entidad `PermissionDomain` + repo + `Permission` refactor a `@ManyToOne`; DTOs `PermissionDto`/`PermissionDomainDto`; `PermissionService.getCatalog()` + `PermissionController GET /v1/admin/permissions` con `@PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")`; ADR 0009 espejado al backend; convenciones de trabajo con IA agregadas a `.ai/CLAUDE.md`.
- **2026-05-28** — Connection limits + statement timeouts por rol DB (V3 hardening, deployment env vars).
- **2026-05-25** — Endpoint `/v1/system-info` con metadata de release.
- **2026-05-18 → 2026-05-24** — Bootstrap completo del módulo auth (login/refresh/logout/recover/me/admin users), módulo catalog (10 catálogos públicos+admin), módulo contact. Migraciones V1–V10. Hardening Postgres (REVOKE PUBLIC, search_path, connection limits).
- **2026-05-18 (inicial)** — Bootstrap Spring Boot 4 + Java 25 + dependencias clave + `.ai/` local.

> Las secciones "Detalle" y "Estructura del repo" más abajo describen el estado de bootstrap del 2026-05-18 y necesitan refresh — usar como contexto histórico, no como verdad operativa.
