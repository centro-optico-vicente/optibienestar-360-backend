# optibienestar-360-backend

Backend REST API del ecosistema **OptiBienestar 360 / Centro Óptico Vicente**. Spring Boot 4.0.6 + Java 25 + PostgreSQL 15 + Redis 7 + Cloudflare R2.

**Parte del ecosistema:** [centro-optico-vicente](https://github.com/fenix-core/centro-optico-vicente) (hub maestro)

## Documentación

Toda la documentación específica del backend para asistencia IA vive en [`.ai/`](.ai/). **Empezar por [`.ai/CLAUDE.md`](.ai/CLAUDE.md)**.

Para decisiones cross-stack (modelo de dominio, infraestructura, integración con frontend/landing), ver el **hub maestro**: [`../centro-optico-vicente/.ai/`](../centro-optico-vicente/.ai/).

| Necesito | Archivo |
|---|---|
| Brief de entrada al backend | [`.ai/CLAUDE.md`](.ai/CLAUDE.md) |
| Checklist operacional (tareas backend) | [`.ai/checklist.md`](.ai/checklist.md) |
| Schema de base de datos | [`.ai/specs/02-database.md`](.ai/specs/02-database.md) |
| Convenciones de paquetes y arquitectura | [`.ai/specs/01-package-structure.md`](.ai/specs/01-package-structure.md) |
| Playbooks (nueva entidad, migración, endpoint) | [`.ai/playbooks/`](.ai/playbooks/) |
| Reglas de negocio | [`../centro-optico-vicente/.ai/context/business-rules.md`](../centro-optico-vicente/.ai/context/business-rules.md) |
| ADRs cross-stack | [`../centro-optico-vicente/.ai/decisions/`](../centro-optico-vicente/.ai/decisions/) |

## Stack

- **Java 25** + **Spring Boot 4.0.6** + **Gradle 9.4.1**
- **Servidor:** Jetty embebido (más liviano que Tomcat)
- **Persistencia:** Spring Data JPA + Hibernate + PostgreSQL 15
- **Migraciones:** Flyway (pendiente instalar) — DDL versionado + JPA en `validate` mode
- **Auth:** JWT con jjwt 0.13.0 (access 15min + refresh 30d)
- **Cache:** Spring Cache + Redis 7
- **Storage:** Spring Cloud AWS S3 4.0.2 → Cloudflare R2
- **API docs:** springdoc-openapi (Swagger UI)
- **DTO mapping:** MapStruct 1.6.3
- **Filtering:** RSQL JPA 7.0.0 para listings
- **Tests:** JUnit 5 + Testcontainers

## Cómo correr (local)

```bash
# Requisitos: Java 25, Postgres 15 local, Redis 7 local
./gradlew bootRun --args='--spring.profiles.active=dev'
# API en http://localhost:8080
# Swagger en http://localhost:8080/swagger-ui/index.html
```

Variables clave en `application-dev.properties`. Ver [`.ai/specs/01-package-structure.md`](.ai/specs/01-package-structure.md).

## Estado actual

Bootstrap completo con dependencias instaladas; **sin entidades ni endpoints aún**. Ver [`.ai/context/current-state.md`](.ai/context/current-state.md).
