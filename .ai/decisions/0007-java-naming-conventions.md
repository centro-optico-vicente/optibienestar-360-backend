# ADR 0007 (local backend) — Convenciones de naming Java

**Estado:** Aceptado  
**Fecha:** 2026-05-24  
**Fuente cross-stack:** [ADR 0009 hub — Convenciones de código](../../../centro-optico-vicente/.ai/decisions/0009-code-conventions.md)

## Decisión

Todo código Java en este repositorio sigue las convenciones del ADR 0009 del hub. Este documento las repite para referencia rápida dentro del repo.

### Idioma

- **Código** (clases, métodos, variables, constantes, paquetes, comentarios, logs): **inglés**
- **Datos de dominio** (valores en DB, textos de UI, emails): **español venezolano**
- **Documentación `.ai/`**: **español**

### Naming por tipo

| Tipo | Convención | Ejemplo |
|---|---|---|
| Clases, interfaces, enums, records | `PascalCase` | `UserService`, `LoginRequest`, `UserStatus` |
| Métodos | `camelCase` | `findByUuid()`, `generateAccessToken()` |
| Variables y campos de instancia | `camelCase` | `passwordHash`, `failedLoginAttempts` |
| Parámetros | `camelCase` | `userUuid`, `refreshToken` |
| Constantes estáticas (`static final`) | `UPPER_SNAKE_CASE` | `BEARER_PREFIX`, `BLACKLIST_PREFIX`, `MAX_ATTEMPTS` |
| Valores de `enum` | `UPPER_SNAKE_CASE` | `ACTIVE`, `PENDING_REVIEW`, `USER_LOGOUT` |
| Paquetes | `lowercase.dotted` | `com.fenixcore.optisaludplus.modules.auth` |
| Archivos de migración Flyway | `V{N}__{snake_case}.sql` | `V5__users_and_roles.sql` |

### Naming descriptivo

Nombres concretos que describen el propósito, no abreviaciones ni genéricos:

| ❌ Evitar | ✅ Preferir |
|---|---|
| `e`, `ex` en catch (salvo bloque de una línea) | `mailException`, `jwtException` |
| `data`, `result`, `value` solos | `userData`, `tokenValidationResult` |
| `m`, `u`, `r` | `member`, `user`, `role` |
| `handle`, `doStuff`, `process` | `handleFailedAttempt`, `collectPermissions` |
| `i`, `j` fuera de loops triviales | `index`, `attemptCount` |

### Comentarios

Default: **no escribir comentarios.** Solo cuando el **por qué** no es obvio:

```java
// BCrypt comparison is constant-time — do NOT short-circuit with equals() before this call
if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) { ... }

// SHA-256 hash stored, never the raw token — prevents DB-level token theft
user.setPasswordResetToken(sha256(rawToken));
```

No comentar lo que el nombre ya dice. No comentar PRs, fechas, autores (eso es git blame).

### Constantes — referencia de las ya existentes

```java
// JWT filters
private static final String AUTHORIZATION_HEADER = "Authorization";
private static final String BEARER_PREFIX = "Bearer ";

// Redis prefixes
private static final String BLACKLIST_PREFIX = "blacklist:";
private static final String REFRESH_PREFIX   = "refresh:";
private static final String USER_REFRESH_SET  = "user_refresh:";

// Error messages
private static final String GENERIC_AUTH_ERROR = "Credenciales incorrectas"; // español: dato visible al usuario
```

> Los mensajes de error visibles al usuario van en **español** (ej. `"Credenciales incorrectas"`) — son datos de UI, no identificadores de código.
