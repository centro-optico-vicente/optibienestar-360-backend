# Estado actual del backend (snapshot)

> **Actualizado:** 2026-05-18
>
> Qué existe HOY en `optisalud-plus-backend`. Actualizar al cierre de cada sesión productiva.

## Resumen

| Aspecto | Estado |
|---|---|
| Bootstrap base | ✅ Spring Boot 4.0.6 + Java 25 + dependencias instaladas |
| Estructura paquetes | ❌ Solo `OptiSaludPlusApplication.java` (sin módulos) |
| Configuración seguridad | ❌ Sin `SecurityConfig`, `JwtService`, filters |
| Migraciones Flyway | ❌ No instalado (sólo `ddl-auto=update` en dev) |
| Entidades JPA | ❌ Cero |
| Endpoints REST | ❌ Cero |
| Tests | ❌ Solo `contextLoads()` trivial |
| Dockerfile | ❌ No existe |
| CI/CD | ⚠️ Workflow existe en `.github/`pero genérico |
| Configuración SMTP | ❌ No instalado |
| Configuración Redis | ⚠️ Cliente instalado, no configurado |
| Configuración S3/R2 | ⚠️ Cliente instalado, no configurado |
| Swagger/OpenAPI | ⚠️ Dependencia instalada, no configurado |

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
spring.application.name=optisaludplus
```

**`application-dev.properties`:**
```properties
server.port=${SERVER_PORT:8080}
server.jetty.threads.min=10
server.jetty.threads.max=100

spring.datasource.url=jdbc:postgresql://localhost:5432/optisalud
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

`OptiSaludPlusApplication.java`:
```java
@SpringBootApplication
public class OptiSaludPlusApplication {
    public static void main(String[] args) {
        SpringApplication.run(OptiSaludPlusApplication.class, args);
    }
}
```

### Estructura del repo

```
optisalud-plus-backend/
├── CLAUDE.md                                  ← creado 2026-05-18
├── .ai/                                       ← creado 2026-05-18
├── build.gradle, settings.gradle
├── README.md, HELP.md
├── src/main/
│   ├── java/com/fenixcore/optisaludplus/
│   │   └── OptiSaludPlusApplication.java     ← ÚNICA clase
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

- **2026-05-18** — Bootstrap del `.ai/` local del backend (CLAUDE.md, checklist subset, skills, MEMORY, current-state).
- **Anterior** — Bootstrap inicial Spring Boot 4 + Java 25 + dependencias clave instaladas.
