# Playbook — Build y correr el backend en local

> **Creado 2026-10-12** (ver [auditoría hub 2026-09-23](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/notes/2026-09-23_audit.md) hallazgo H-FS-5): no había un playbook de "cómo correr esto en tu máquina" — el `README.md` documenta la tabla de env vars exhaustivamente pero no el paso a paso para llegar de "clonaste el repo" a "la app corre". Verificado contra `build.gradle`, `ci.yaml`, `application-dev.properties` reales.

## Pre-requisitos

- **Java 25** (`languageVersion = JavaLanguageVersion.of(25)` en `build.gradle` — no es un typo, es la versión real del toolchain).
- **Docker** (para Postgres/Redis locales — no hace falta instalarlos en el host).
- Git hooks activados una vez por clon: `git config core.hooksPath .githooks` (ver `README.md` — bloquea push directo a `main`, regla cero [ADR LOC-0004](../decisions/loc-0004-pr-and-branch-conventions.md)).

## Paso 1 — Postgres + Redis locales

**Camino rápido (igual al que usa CI, probado):** un Postgres plano, conectando como superusuario — evita crear roles.

```bash
docker run -d --name pg-dev -p 5432:5432 \
  -e POSTGRES_DB=optibienestar360 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15-alpine

docker run -d --name redis-dev -p 6379:6379 redis:7-alpine
```

**Camino "como producción"** (roles separados `optibienestar360_app`/`optibienestar360_migration`, más fiel al deploy real): usar [`database-stack`](https://github.com/fenix-core/database-stack) (`docker compose --profile postgres up`) y crear los roles con el mismo patrón que [hub `first-deploy.md`](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/playbooks/first-deploy.md) (`CREATE ROLE ... WITH LOGIN CREATEROLE`). Más fidelidad, más pasos — usar el camino rápido salvo que estés probando algo específico de permisos de rol.

## Paso 2 — Variables de entorno

`application-dev.properties` ya trae defaults razonables (ver tabla completa en `README.md`). Con el camino rápido del Paso 1, **sobreescribir** el usuario/password para apuntar al superusuario del contenedor (igual que hace `ci.yaml`):

```bash
export SPRING_PROFILES_ACTIVE=dev
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres
export DATABASE_MIGRATION_USER=postgres
export DATABASE_MIGRATION_PASSWORD=postgres
```

Sin este override, la app intenta loguear como `optibienestar360_app`/`changeme-dev` (los defaults de `application-dev.properties`), que no existen en un Postgres recién creado con el camino rápido — falla al arrancar con error de autenticación, no uno de schema.

## Paso 3 — Levantar la app

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Flyway migra automáticamente al arrancar (175+ migraciones reales a esta fecha — tarda unos segundos la primera vez). Confirmar que levantó:

```bash
curl -s http://localhost:8080/actuator/health | jq
# Esperado: {"status":"UP", ...}
```

## Paso 4 — Probar un endpoint autenticado

Ver la sección "Locale in dev requests" del `README.md` para el header `Accept-Language`. Para login, usar los usuarios sembrados en `V7__seed_users.sql` (ver [`deploy-rollout.md`](../../../centro-optico-vicente/.ai/playbooks/deploy-rollout.md) sección "Primer deploy: rotar contraseñas seed" para los emails/roles y el password placeholder — **válido también en dev**, no hace falta rotarlo localmente).

## Qué valida CI (`ci.yaml`) — para saber qué vas a romper antes de hacer push

```yaml
services:
  postgres:
    image: postgres:15-alpine
    env:
      POSTGRES_DB: optibienestar360_test
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
```

CI corre `./gradlew build --no-daemon` (compila + corre **toda** la suite de tests, incluyendo `@DataJpaTest`/`@SpringBootTest` con Testcontainers) contra un Postgres efímero igual al del Paso 1 — mismo usuario/password `postgres`/`postgres`, por eso el camino rápido de este playbook replica exactamente lo que ve CI. Si pasa local con ese mismo setup, es la señal más confiable de que también va a pasar en CI.

```bash
./gradlew build --no-daemon    # build + test completo, igual que CI
./gradlew test --tests "OptiBienestar360ApplicationTests"   # solo el smoke test de contexto
```

## Problemas comunes

- **`FATAL: password authentication failed`**: el contenedor Postgres no tiene las credenciales que la app espera — ver Paso 2. Es el error más común al seguir el README sin este playbook.
- **`Flyway checksum mismatch`**: alguien editó una migración ya aplicada en tu Postgres local. Recrear el contenedor (`docker rm -f pg-dev` + Paso 1 de nuevo) es más rápido que investigar — es una BD descartable local, no producción.
- **Java version mismatch**: `./gradlew` usa el toolchain de Gradle (`languageVersion = 25`), que puede auto-descargar el JDK 25 si no lo tenés instalado — no hace falta instalarlo a mano, pero la primera build tarda más por la descarga.
- **Puerto 5432/6379 ocupado**: si ya tenés `database-stack` u otro Postgres/Redis corriendo en esos puertos, cambiar el `-p` del `docker run` y los `DATABASE_PORT`/`REDIS_PORT` en consecuencia.

## Referencias

- `README.md` — tabla completa de variables de entorno
- [`new-migration.md`](new-migration.md) — comandos de verificación post-migración
- [hub `first-deploy.md`](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/playbooks/first-deploy.md) — setup de roles "como producción"
- [hub `release.md`](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/playbooks/release.md) — qué pasa después de que esto compila y pasa tests
