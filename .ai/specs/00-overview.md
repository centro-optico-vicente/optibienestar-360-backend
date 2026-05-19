# Specs del backend — índice

> Especificaciones técnicas específicas del backend Spring Boot. Specs cross-stack viven en el [hub maestro](../../../centro-optico-vicente/.ai/specs/).

## Specs disponibles

| # | Spec | Foco |
|---|---|---|
| 01 | [Package structure](01-package-structure.md) | Estructura `core/`, `security/`, `common/`, `modules/` |
| 02 | [Database schema](02-database.md) | Schema PostgreSQL completo (todas las tablas) |
| 03 | [JPA + Flyway](03-jpa-flyway.md) | Convenciones de entidades y migraciones |
| 04 | [Security](04-security.md) | Spring Security, JWT, password encoding, CORS |
| 05 | [Roles & permissions](05-roles-permissions.md) | RBAC granular |
| 06 | [REST API](06-rest-api.md) | Controllers, paginación, filtros RSQL, Swagger |
| 07 | [Cache](07-cache.md) | Redis: validador, sesiones, rate limits |
| 08 | [Storage R2](08-storage-r2.md) | Upload/download/presigned URLs a Cloudflare R2 |
| 09 | [SMTP](09-smtp.md) | Spring Mail + Thymeleaf + cola async |
| 10 | [Validators](10-validators.md) | Validador en tiempo real para aliados (CRÍTICO p95 < 200ms) |
| 11 | [Billing manual](11-billing-manual.md) | Workflow pagos manuales con aprobación |
| 12 | [Commissions](12-commissions.md) | Cálculo comisiones + ciclos + referidos |
| 13 | [Observability](13-observability.md) | Actuator + logs estructurados + métricas |

## Specs cross-stack relevantes (hub)

| # | Spec | Por qué importa al backend |
|---|---|---|
| 01 | [Arquitectura](../../../centro-optico-vicente/.ai/specs/01-architecture.md) | Cómo el backend se inserta en el ecosistema |
| 03 | [Seguridad](../../../centro-optico-vicente/.ai/specs/03-security.md) | TLS, CORS, secretos, audit |
| 05 | [Domain model](../../../centro-optico-vicente/.ai/specs/05-domain-model.md) | Modelo conceptual |
| 06 | [Integración REST](../../../centro-optico-vicente/.ai/specs/06-integration.md) | Contracts REST con frontend/landing |

## Cómo crear una spec nueva

1. ¿Es backend-only o cross-stack?
   - Backend-only → crear acá con siguiente número.
   - Cross-stack → crear en `centro-optico-vicente/.ai/specs/`.
2. Agregar entrada en este índice.
3. Linkear desde el `checklist.md` si aplica.
