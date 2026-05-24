# Memoria navegable (backend)

> Índice por situación. Para visión cross-stack, ver [hub maestro](../../../centro-optico-vicente/.ai/context/MEMORY.md).

## Situaciones comunes

### "Acabo de llegar al backend, ¿qué leo primero?"
1. [`../CLAUDE.md`](../CLAUDE.md) — brief
2. [`current-state.md`](current-state.md) — qué existe HOY
3. [`../specs/01-package-structure.md`](../specs/01-package-structure.md) — estructura paquetes
4. [`../specs/02-database.md`](../specs/02-database.md) — schema
5. [`../checklist.md`](../checklist.md) — índice de tareas → [fase-0](../checklists/fase-0-bootstrap.md) · [fase-1](../checklists/fase-1-bootstrap-backend-spring-boot.md) · [fase-2](../checklists/fase-2-afiliaciones-y-membresias.md)
6. [`../checklist-vertical.md`](../checklist-vertical.md) — orden de ejecución Fase 5 por alcances

### "Voy a crear una entidad nueva"
1. [`../playbooks/new-entity.md`](../playbooks/new-entity.md)
2. Para schema: [`../specs/02-database.md`](../specs/02-database.md)
3. Para naming de tablas/columnas: [ADR 0006 cross-stack](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
4. Para naming Java (camelCase, PascalCase, UPPER_SNAKE_CASE): [`../decisions/0007-java-naming-conventions.md`](../decisions/0007-java-naming-conventions.md)
5. Para búsquedas en repositorios (uuid, código, FK compuesta): [`../decisions/0006-repository-search-conventions.md`](../decisions/0006-repository-search-conventions.md)

### "Voy a crear una migración Flyway"
1. [`../playbooks/new-migration.md`](../playbooks/new-migration.md)
2. [`../specs/03-jpa-flyway.md`](../specs/03-jpa-flyway.md)
3. [ADR 0005 cross-stack](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)

### "Voy a crear un endpoint REST"
1. [`../playbooks/new-endpoint.md`](../playbooks/new-endpoint.md)
2. [`api-conventions.md`](api-conventions.md)
3. [`../specs/06-rest-api.md`](../specs/06-rest-api.md)
4. Para integración: [hub `06-integration.md`](../../../centro-optico-vicente/.ai/specs/06-integration.md)

### "Voy a tocar algo de auth/security"
1. [`../specs/04-security.md`](../specs/04-security.md)
2. [`../specs/05-roles-permissions.md`](../specs/05-roles-permissions.md)
3. Cross-stack: [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md)

### "Voy a trabajar en cache Redis"
1. [`../specs/07-cache.md`](../specs/07-cache.md)
2. Para el validador específicamente: [`../specs/10-validators.md`](../specs/10-validators.md)

### "Voy a tocar storage de archivos (R2)"
1. [`../specs/08-storage-r2.md`](../specs/08-storage-r2.md)
2. [ADR 0007 cross-stack](../../../centro-optico-vicente/.ai/decisions/0007-r2-as-s3.md)

### "Voy a implementar el validador en tiempo real"
1. [`../specs/10-validators.md`](../specs/10-validators.md)
2. Reglas: [hub `business-rules.md` sección "Validador"](../../../centro-optico-vicente/.ai/context/business-rules.md)

### "Voy a implementar el workflow de pagos"
1. [`../specs/11-billing-manual.md`](../specs/11-billing-manual.md)
2. Reglas: [hub `business-rules.md` sección "Workflow"](../../../centro-optico-vicente/.ai/context/business-rules.md)
3. [ADR 0008 cross-stack](../../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md)

### "Voy a calcular comisiones"
1. [`../specs/12-commissions.md`](../specs/12-commissions.md)
2. Reglas: [hub `business-rules.md` sección "Comisiones"](../../../centro-optico-vicente/.ai/context/business-rules.md)

### "Voy a configurar SMTP"
1. [`../specs/09-smtp.md`](../specs/09-smtp.md)

### "Algo no compila / test falla"
1. Verificar `current-state.md` para confirmar estado actual
2. Si es config: revisar `application-{profile}.properties`
3. Si es JPA: revisar entidad vs migración Flyway (Hibernate validate falla si no coinciden)
