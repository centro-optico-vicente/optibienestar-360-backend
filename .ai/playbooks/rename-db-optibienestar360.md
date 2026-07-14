# Playbook — Cutover de BD e infra a OptiBienestar 360

Rebrand `OptiSalud+ → OptiBienestar 360`. La marca, el código, el paquete Java y
los slugs de repo/imagen ya se migraron por PR. Este playbook cubre lo que **no**
puede ir en un simple cambio de texto: los **identificadores de datos e infra**
(roles/BD PostgreSQL, bucket R2, dominio de `problem+json`).

> ⚠️ **Por qué no es una migración Flyway normal.** PostgreSQL prohíbe renombrar
> el rol de la sesión actual, y en una BD ya inicializada Flyway se conecta *como*
> `optisalud_migration`. Además no se puede renombrar la BD a la que estás
> conectado. Por eso el rename en **producción** se hace fuera de banda, como
> superusuario, en ventana de mantenimiento, **antes** de desplegar el nuevo
> config. `V31__rename_roles_optibienestar360.sql` solo automatiza el rename en
> bootstraps frescos/CI (donde Flyway corre como `postgres`).

## Mapa de nombres

| Antes | Después |
|---|---|
| BD `optisalud` | `optibienestar360` |
| rol `optisalud_migration` | `optibienestar360_migration` |
| rol `optisalud_app` | `optibienestar360_app` |
| rol `optisalud_readonly` | `optibienestar360_readonly` |
| bucket R2 `optisalud-prod` / `-dev` | `optibienestar-360-prod` / `-dev` |
| dominio `problems-optisalud.centroopticovicente.com` | `problems-optibienestar360.centroopticovicente.com` |
| Redis prefix `optisalud:` | `optibienestar360:` |

Las contraseñas de los roles **se conservan** (PostgreSQL 15 usa SCRAM-SHA-256,
que no incrusta el nombre del rol; con MD5 se perderían).

## Procedimiento (producción)

1. **Ventana de mantenimiento**: detener la app / ambas réplicas (sin conexiones
   activas a la BD).
2. **Roles + BD** (como `postgres`):
   ```bash
   # roles (conectado a cualquier BD)
   psql -U postgres -d optibienestar360 -f scripts/ops/rename-db-to-optibienestar360.sql
   # BD (conectado a 'postgres', tras cerrar sesiones a la BD vieja)
   psql -U postgres -d postgres -c "ALTER DATABASE optisalud RENAME TO optibienestar360;"
   ```
3. **Secrets/env**: actualizar en el `.env` de despliegue (plantilla ya migrada en
   `deployment/env_template.env` del hub):
   `DATABASE_NAME=optibienestar360`, `DATABASE_USER=optibienestar360_app`,
   `DATABASE_MIGRATION_USER=optibienestar360_migration`,
   `DATABASE_READONLY_USER=optibienestar360_readonly`,
   `STORAGE_R2_BUCKET=optibienestar-360-prod`,
   `PROBLEMS_BASE_URL=https://problems-optibienestar360.centroopticovicente.com`.
4. **R2 (Cloudflare)**: los buckets no se renombran in-place. Crear
   `optibienestar-360-prod` / `optibienestar-360-dev`, migrar objetos
   (`rclone copy r2:optisalud-prod r2:optibienestar-360-prod`), repuntar las
   credenciales/URLs de backup.
5. **DNS**: crear el registro `problems-optibienestar360.centroopticovicente.com`
   (o mantener el viejo con redirect hasta migrar los tipos de error publicados).
6. **Desplegar** la nueva imagen. Flyway se conecta como
   `optibienestar360_migration`, corre `V31` como no-op y la app levanta con los
   nombres nuevos.
7. **Verificar**: login app, health, un flujo que toque R2 y un error `problem+json`.

## Fresh / CI

No requiere pasos manuales: `postgres` bootstrapea, V3 crea los roles
`optisalud_*` y **V31** los renombra a `optibienestar360_*` automáticamente.

## Docker Hub (pendiente, fuera de BD)

Las imágenes `fenixcoreenterprises/optisalud-plus-{frontend,backend}` aún no
están renombradas a `optibienestar-360-{frontend,backend}`. Renombrar/crear los
repos en Docker Hub y actualizar `deployment/docker-compose.yaml` del hub antes
del próximo deploy (las refs de código/CI ya apuntan al nombre nuevo).
