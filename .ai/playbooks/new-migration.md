# Playbook — Nueva migración Flyway

> **Reescrito 2026-10-10** contra el código real (ver [auditoría 2026-09-23](../notes/2026-09-23_audit.md), recomendación #4) — la versión anterior enseñaba PK `UUID` simple; la real es `BIGINT` interno + `uuid` externo. Ver [`new-entity.md`](new-entity.md) para el patrón JPA correspondiente.

> Decisión implementada: [ADR 0005 cross-stack](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md).

## Cuándo crear una migración

- Crear/modificar/borrar **schema** (tablas, columnas, índices, vistas, funciones, triggers, extensiones)
- Insertar/modificar **seeds** (datos iniciales de catálogos)
- Crear nuevos **enums** declarados (ENUM type)

NO crear migración para:
- Datos transaccionales (eso es vía endpoints)
- Cambios "experimentales" en dev (usar branch + DB local fresca)

## Procedimiento

### 1. Decidir el número

Verificar la última migración existente:

```bash
ls -1 src/main/resources/db/migration/ | sort -V | tail
# Devuelve la última migración real del repo (a esta fecha, V176+) — no asumir
# un número fijo, siempre correr el comando.

# Próximo: el que sigue a la última que devolvió el comando de arriba.
```

### 2. Crear el archivo

Naming: `V{N}__{snake_case_descripcion_breve}.sql`

```bash
touch src/main/resources/db/migration/V15__ally_users.sql
```

Reglas:
- Doble underscore entre número y descripción (`V15__`, no `V15_`)
- Descripción breve y clara (no spaces, sólo underscores)
- Una migración = un cambio cohesivo (no mezclar 5 cambios no relacionados)

### 3. Escribir el SQL

#### Template para nueva tabla

```sql
-- ====================================================
-- V{N}__ally_users.sql
-- Tabla de usuarios operadores de aliados (recepcionistas, etc.)
-- Patrón real: V115__payment_categories_and_methods.sql
-- ====================================================

SET search_path TO app, public;

CREATE TABLE ally_users
(
    ally_users_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    ally_id          BIGINT       NOT NULL REFERENCES allies (allies_id),
    user_id          BIGINT       NOT NULL REFERENCES users (users_id),
    -- specific fields...
    role_within_ally VARCHAR(50)  NOT NULL DEFAULT 'OPERATOR',

    -- audit + status (obligatorios por ADR 0006)
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    status           VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,

    CONSTRAINT uq_ally_users_ally_user UNIQUE (ally_id, user_id),
    CONSTRAINT ck_ally_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- índices
CREATE INDEX idx_ally_users_ally ON ally_users (ally_id) WHERE is_active = TRUE;
CREATE INDEX idx_ally_users_user ON ally_users (user_id) WHERE is_active = TRUE;

-- trigger updated_at
CREATE TRIGGER trg_ally_users_updated_at
    BEFORE UPDATE ON ally_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- comentarios opcionales para documentación interna
COMMENT ON TABLE ally_users IS 'Usuarios operadores que actúan en nombre de un aliado';
COMMENT ON COLUMN ally_users.role_within_ally IS 'Rol del usuario dentro del aliado (no rol del sistema)';
```

**PK y FK, regla real:** la PK siempre es el nombre de tabla completo en plural + `_id` (`ally_users_id`, no `ally_user_id`). Las FK usan el nombre de la entidad referenciada en **singular** + `_id` (`ally_id` referencia `allies(allies_id)`, `user_id` referencia `users(users_id)`) — son dos reglas distintas, no confundirlas (ver [ADR 0006 del hub](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md), corregido 2026-10-09 con esta misma distinción).

#### Template para agregar columna

```sql
-- V20__add_phone_to_members.sql
ALTER TABLE members ADD COLUMN phone_secondary VARCHAR(20) NULL;
CREATE INDEX idx_members_phone_secondary ON members(phone_secondary) WHERE phone_secondary IS NOT NULL;
COMMENT ON COLUMN members.phone_secondary IS 'Teléfono alternativo opcional';
```

#### Template para seeds

```sql
-- V{N}__seed_example.sql  (formato genérico de un seed)
-- Para el seed real de roles ver V6__seed_roles.sql.
INSERT INTO example_things (name, description)
VALUES
    ('FOO', 'Descripción de foo'),
    ('BAR', 'Descripción de bar');
```

#### Template para nuevo índice

```sql
-- V25__add_index_payments_membership_status.sql
CREATE INDEX CONCURRENTLY idx_payments_membership_status
    ON payments(membership_id, status)
    WHERE is_active = TRUE;
```

> ⚠️ `CONCURRENTLY` evita locks pero NO se puede usar dentro de transacción Flyway por defecto. Configurar `spring.flyway.outOfOrder=false` y opcionalmente usar repeatable migrations (`R__`) o ejecutar manualmente fuera de Flyway en mantenimiento.

### 4. Crear/modificar entidad JPA correspondiente

Ver [`new-entity.md`](new-entity.md).

JPA debe coincidir exactamente con el schema (Hibernate validate fallaría al arrancar si no).

### 5. Probar localmente

```bash
# Opción A: dejar que la app aplique al arrancar
./gradlew bootRun --args='--spring.profiles.active=dev'

# Opción B: forzar Flyway sin arrancar app
./gradlew flywayMigrate -i

# Verificar
psql -d optibienestar360 -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Validar JPA mappea bien
./gradlew test --tests "OptiBienestar360ApplicationTests"
```

### 6. Verificar antes de commit

- [ ] La migración corre sin errores en DB limpia
- [ ] La migración corre sin errores en DB existente (con datos previos)
- [ ] Hibernate `validate` no falla al arrancar
- [ ] Si agrega tabla: tiene PK `BIGINT` (nombre de tabla en plural + `_id`) + columna `uuid` separada, audit columns, is_active, status, trigger updated_at
- [ ] Si agrega FK: tiene índice asociado
- [ ] Naming sigue convención (`V{N}__snake_case.sql`)
- [ ] Comentarios opcionales en columnas/tablas no triviales

### 7. Reglas críticas

#### Inmutabilidad

Una migración aplicada a producción es **INMUTABLE**. Si necesitás corregir:

❌ NO modificar `V15__ally_users.sql` después de mergeado
✅ Crear `V16__fix_ally_users_X.sql` con la corrección

Razón: Flyway compara checksums. Si modificás un archivo ya aplicado, falla al arranque.

#### Backward compatibility

Si la migración cambia algo en uso por código existente:
- **Aditiva** (add column nullable, add table) → safe
- **Destructiva** (drop column, rename, change type) → 2-fase:
  1. Versión N: agregar nuevo, mantener viejo
  2. Deploy app que escribe en ambos, lee del nuevo
  3. Versión N+1: drop viejo

#### Datos críticos

Si la migración transforma datos:
- **Backup ANTES** (incluso si ya hay backup diario, hacer uno extra antes)
- Probar en DB de staging primero
- Considerar ejecutar en mantenimiento si tabla > 100K filas

## Casos especiales

### Crear ENUM type

```sql
-- V22__create_payment_method_enum.sql
CREATE TYPE payment_method AS ENUM ('ZELLE', 'TRANSFERENCIA', 'EFECTIVO');

ALTER TABLE payments ADD COLUMN method payment_method NOT NULL DEFAULT 'ZELLE';
```

> En JPA: `@Enumerated(EnumType.STRING)` + `@JdbcType(PostgreSQLEnumJdbcType.class)` o usar `VARCHAR + CHECK constraint` (más flexible para agregar valores sin migración). **Dato real:** el propio catálogo de métodos de pago terminó siendo una **tabla** (`payment_methods`, V115), no un `ENUM` ni un `CHECK` fijo — administrable sin deploy. Preferir tabla-catálogo sobre `ENUM`/`CHECK` cuando el negocio pueda querer agregar valores sin esperar un release (ver `new-entity.md` para el patrón de catálogo completo).

### Crear extensión

```sql
-- V1__initial_extensions.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS unaccent;     -- full-text search en español
CREATE EXTENSION IF NOT EXISTS citext;       -- emails case-insensitive
```

### Crear función + trigger genérico

```sql
-- V2__base_audit_function.sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Usar en cada tabla nueva:

```sql
CREATE TRIGGER trg_{table}_before_update_set_updated_at
    BEFORE UPDATE ON {table}
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

### Crear vista (cuando es estable)

```sql
-- V{N}__digital_cards_view.sql
CREATE OR REPLACE VIEW digital_cards_v AS
SELECT
    m.members_id,
    m.uuid AS member_uuid,
    m.full_name,
    m.document_number,
    ms.memberships_id,
    ms.status AS membership_status,
    p.name AS plan_name,
    ms.next_due_date
FROM members m
JOIN memberships ms ON ms.member_id = m.members_id
JOIN plans p ON ms.plan_id = p.plans_id
WHERE m.is_active = TRUE AND ms.is_active = TRUE;
```

Nota: la PK de cada tabla es plural (`members_id`, `memberships_id`, `plans_id`); el FK que las referencia es singular (`memberships.member_id`, `memberships.plan_id`) — mismo patrón del Paso 3.

### Migración repeatable (raras)

Para vistas/funciones que se actualizan frecuentemente sin perder tracking:

```
R__digital_cards_view.sql
```

Flyway re-aplica si el checksum cambia. Útil para vistas evolutivas.

## Validación post-aplicación

```bash
# 1. Ver historial Flyway
psql -d optibienestar360 -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# 2. Verificar schema
psql -d optibienestar360 -c "\d+ {tabla_nueva}"

# 3. Verificar índices
psql -d optibienestar360 -c "\di {tabla_nueva}*"

# 4. Verificar JPA arranca
./gradlew test --tests "OptiBienestar360ApplicationTests"
```

## Cuando algo sale mal

### Migración falla en producción
1. **NO modificar el archivo** ya commiteado.
2. Si es fixable con SQL adicional: crear `V{N+1}__rollback_X.sql`.
3. Si rompe completamente: restore desde backup + análisis post-mortem.

### Hibernate validate falla al arrancar
- Mensaje típico: "missing column X in table Y" o "column type mismatch"
- Significa: schema y entidad no coinciden
- Verificar diferencias y corregir entidad (NO modificar migración)

### Checksum mismatch
- Significa: alguien modificó un archivo ya aplicado
- Recuperar versión original del git o ejecutar `flyway repair`

## Referencias

- [ADR 0005 Flyway + JPA validate](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)
- [ADR 0006 Table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
- [`../specs/02-database.md`](../specs/02-database.md) — schema completo
- [Flyway docs](https://flywaydb.org/documentation/)
