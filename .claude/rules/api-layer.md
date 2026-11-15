---
paths:
  - "src/main/java/**/*Controller.java"
  - "src/main/java/**/dto/**"
  - "src/main/java/**/mapper/**"
---
# Controllers, DTOs y mappers

- Rutas `/v1/{grupo}/…` kebab-case; admin bajo `/v1/admin/…`, propio del usuario bajo `/v1/me/…` o `/v1/<rol>/me/…`, público bajo `/v1/public/…`.
- JSON `camelCase`. Valores presentacionales con `_Display` (FK → `<rel>Uuid` + `<rel>_Display`; fecha/monto/enum → `<campo>` + `<campo>_Display`, resueltos por `Locale` en el servidor) — ADR 0014 del hub.
- Exponer `uuid`, nunca el `BIGINT` interno.
- Listados: `?q=` + RSQL + paginación, DTO compacto `*ListItemDto`; selects usan `/options` ([LOC-0013](../../.ai/decisions/loc-0013-options-endpoint-conventions.md)). Detalle por `GET /{uuid}`.
- Estado vacío ([LOC-0012](../../.ai/decisions/loc-0012-empty-state-http-conventions.md)): sub-recurso 1:1 → 200 con `exists`; búsqueda por id inexistente → 404; listas → 200 `[]`.
- `@PreAuthorize("hasAuthority('X_ACTION')")` con permiso granular por acción. Errores de negocio con `LocalizedBusinessException` + código de `messages_*.properties`.
- MapStruct para mapear; nada de lógica de negocio en controller ni mapper.
