# optibienestar-360-backend

API REST de **OptiBienestar 360** (Centro Óptico Vicente). Java 25 + Spring Boot 4 (Jetty) + Gradle, PostgreSQL 15 (Flyway + JPA `validate`), Redis 7, Cloudflare R2 (S3), JWT, MapStruct, RSQL, JasperReports. Docs del repo en `.ai/` (español); visión cross-stack en el hub [`../centro-optico-vicente`](https://github.com/centro-optico-vicente/centro-optico-vicente/tree/main/.ai) (ADR sin prefijo = del hub; `LOC-` = de este repo).

Código: `src/main/java/com/fenixcore/optibienestar360/{core,security,common,modules/<módulo>/{entity,repository,dto,mapper,service,…}}`; controllers en la raíz de cada módulo. Migraciones en `src/main/resources/db/migration/`.

## Reglas duras

1. **BD = snapshot generado**, no replay de migraciones. Consultarlo con `scripts/db/table.sh` (abajo). Nunca leer enteros `schema-map.md` (300 KB), `schema.sql` ni `data.sql`.
2. **Migraciones Flyway** `V{N}__snake_case.sql`, inmutables una vez mergeadas; corregir con una nueva. JPA siempre `validate`. Al crear una, regenerar `src/main/resources/db/snapshot/` en el mismo PR (skill `new-migration`).
3. **Tablas** extienden `BaseEntity`: PK `BIGINT` `{tabla}_id` + `uuid` externo + auditoría + `is_active` + `status` ([ADR 0006](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/decisions/0006-table-conventions.md)). Soft delete (`setIsActive(false)`), nunca `delete()` en datos sensibles.
4. **API:** `/v1/{grupo}/…` kebab-case, JSON `camelCase`, convención `_Display` para valores presentacionales, errores RFC 7807. Listados devuelven `*ListItemDto` compacto; el detalle va por `GET /{uuid}`.
5. **Permisos granulares** (uno por acción: `X_VIEW_ALL`, `X_CREATE`…), nunca `*_MANAGE`. El JWT lleva permisos, no roles: para saber el rol del actor, cargarlo de BD.
6. **Idioma:** código, comentarios, logs y commits en inglés. Texto visible al usuario solo en `messages_*.properties` / `ValidationMessages_*.properties` / plantillas `<name>_<lang>.html`, vía código de MessageSource, nunca literal.
7. **Datos médicos:** `@PreAuthorize` + bitácora en cada acceso. **Pagos siempre manuales**, sin pasarelas.
8. **Git:** nunca commit a `main`; rama `<tipo>/<kebab>` con `git switch -c X --no-track origin/main`; sin `Co-Authored-By`.

## Qué leer según la tarea

| Tarea | Leer / usar |
|---|---|
| Qué tablas/columnas/FKs existen | `scripts/db/table.sh <tabla>` · `--find txt` · `--column col` · `--refs tabla` · `--columns tabla` |
| Datos sembrados / catálogos | `src/main/resources/db/snapshot/seed-catalog.md` (buscar con `grep`) |
| Última migración | `ls src/main/resources/db/migration \| sort -V \| tail -1` |
| Nueva migración / entidad / endpoint / rol | skills `new-migration`, `new-entity`, `new-endpoint`, `new-role` |
| PR con conflicto de migraciones o snapshot | skill `migration-conflict` |
| Convención de un área | `.ai/specs/NN-*.md` (índice en `.ai/specs/00-overview.md`): seguridad 04, roles 05, REST 06, R2 08, i18n 14, reportes 15, auditoría 16, filtros 19 |
| ¿Ya está decidido? | `.ai/decisions/README.md` (ADR locales) y el índice del hub `../centro-optico-vicente/.ai/decisions/README.md` |
| Regla de negocio | `../centro-optico-vicente/.ai/context/business-rules.md` (buscar la sección, no leer entero) |
| Qué existe / qué falta | `.ai/context/current-state.md` · `.ai/checklist.md` → `.ai/checklists/vertical-*.md` |

Reglas extra que se cargan solas al tocar migraciones, controllers/DTOs o `messages*.properties`: `.claude/rules/`.

## Comandos

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'   # API :8080, Swagger /swagger-ui/index.html
./gradlew test                                            # unit; *IT necesitan Postgres
scripts/db/snapshot.sh --fresh                            # regenerar snapshot de BD (o ./gradlew dbSnapshot)
```
