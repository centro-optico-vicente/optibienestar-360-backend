# Índice de ADR locales (backend)

> ADR propios del backend (prefijo `LOC-`). Los cross-stack están en el índice del hub: [`centro-optico-vicente/.ai/decisions/README.md`](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/README.md). Al crear uno: siguiente número libre y una fila aquí.

| ADR | Título | Estado |
|---|---|---|
| [LOC-0001](loc-0001-jetty-over-tomcat.md) | Jetty embebido sobre Tomcat | Aceptado |
| [LOC-0002](loc-0002-soft-delete.md) | Soft delete pattern obligatorio | Aceptado |
| [LOC-0003](loc-0003-audit-columns.md) | Audit columns obligatorias + Spring Data JPA Auditing | Aceptado |
| [LOC-0004](loc-0004-pr-and-branch-conventions.md) | Convenciones de Pull Request y ramas (Gitflow) | Aceptado |
| [LOC-0005](loc-0005-table-id-convention.md) | Convención de identificadores de tabla (BIGINT + UUID) | Aceptado |
| [LOC-0006](loc-0006-repository-search-conventions.md) | Convenciones de búsqueda en repositorios JPA | Aceptado |
| [LOC-0007](loc-0007-java-naming-conventions.md) | Convenciones de naming Java | Aceptado |
| [LOC-0009](loc-0009-code-conventions.md) | Convenciones de código (idioma, naming, comentarios) | Aceptado |
| [LOC-0010](loc-0010-localization-venezuela.md) | Localización: Venezuela como mercado primario | Aceptado |
| [LOC-0011](loc-0011-persons-identity-hub.md) | Persons como hub central de identidad | Aceptado |
| [LOC-0012](loc-0012-empty-state-http-conventions.md) | Convenciones HTTP para estado vacío (200 con representación vacía vs 404) | Aceptado |
| [LOC-0013](loc-0013-options-endpoint-conventions.md) | Convención `?q=` obligatorio + endpoint `/options` para selects | Aceptado |
| [LOC-0014](loc-0014-service-complexity-refactor-on-demand.md) | Refactor de servicios grandes: bajo demanda, no proactivo | Aceptado |
| [LOC-0015](loc-0015-openapi-contract-per-release.md) | Contrato OpenAPI publicado por release | Propuesto |
| [LOC-0016](loc-0016-portfolio-distribution.md) | Distribución de cartera y filtro "Sin asesor" | Aceptado |
