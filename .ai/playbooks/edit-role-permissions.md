# Playbook — Editar permisos de un rol

> Cambiar el conjunto de permisos de un rol existente desde el panel, y entender
> cómo se propaga el cambio a los usuarios que ya tienen sesión abierta.

## Cuándo

- Ampliar o recortar lo que un rol puede hacer (marcar/desmarcar permisos).
- **No** es para crear roles/permisos nuevos → ver [`new-role.md`](new-role.md).

## Requisitos

- El actor necesita el permiso **`ROLE_PERMISSION_EDIT`** (hoy lo tienen SYSTEM y
  ADMINISTRADOR). Sin él, la pantalla de Roles ni siquiera aparece en el menú, y
  el `PUT` responde 403.

## Paso 1 — Editar desde el panel

1. **Seguridad → Roles** → seleccionar el rol.
2. Marcar/desmarcar permisos (agrupados por dominio) y **Guardar**.
3. El front hace `PUT /v1/admin/roles/{uuid}/permissions` con el set completo
   (**semántica de reemplazo**: el rol queda exactamente con lo enviado; enviar
   vacío quita todos).

Tres reglas que el backend hace cumplir (devuelven error legible, no rompen nada):

| Situación | Respuesta |
|---|---|
| Editar el rol **SYSTEM** sin ser actor SYSTEM | **403** `role.system.not_editable` |
| Un permiso enviado no existe | **422** `role.permission.uuid.unknown` |
| El cambio te dejaría **a ti** sin `ROLE_PERMISSION_EDIT` (anti-lockout) | **422** `role.auto_lockout` |

## Paso 2 — Qué pasa automáticamente (propagación)

Al guardar, el backend marca a **cada usuario que tiene ese rol** como
"invalidado ahora": setea en Redis la clave `user_inv:<userUuid>` con el epoch
actual (fan-out sobre todos los usuarios activos del rol).

En **cada request**, el `JwtAuthenticationFilter` compara el `iat` (fecha de
emisión) del access token contra `user_inv:<userUuid>`:

- Si `iat < user_inv` → el token es **anterior** al cambio de permisos → se
  rechaza (el request queda sin autenticar → **401**).
- El cliente (el front) refresca automáticamente ante el 401
  (`POST /v1/auth/refresh`), y el refresh **relee los permisos de la BD** y emite
  un access token nuevo con las claims actualizadas.

**Consecuencia: la propagación es inmediata — en el siguiente request del
usuario afectado, de forma transparente.** No hay que esperar a que expire nada
ni cerrar sesión a nadie.

### Cómo verificar

```bash
# El marcador quedó puesto para un usuario del rol (valor = epoch en segundos):
redis-cli GET user_inv:<userUuid>

# O revisar los logs del backend al refrescar ese usuario:
#   "Stale token rejected by user epoch: subject=<uuid> iat=... epoch=..."
```

## El caso de los ~15 minutos (y cómo forzar el refresh)

> **Importante:** en operación normal **no** hay ventana de 15 minutos y **no**
> hace falta ningún comando manual. Lo de abajo es solo para un modo de falla.

El fan-out del Paso 2 es **best-effort**: si Redis está caído en el momento de
guardar, `markUserInvalidatedNow` **se salta silenciosamente** (queda un
`WARN "Redis unavailable — could not mark user … as invalidated"` en el log). En
ese caso el marcador `user_inv:` no se puso, y los usuarios afectados siguen con
sus permisos viejos **hasta que su access token expire por sí solo** — como
máximo `jwt.access-expiration-minutes` (**15 min** por defecto) — y recién ahí el
refresh les trae los permisos nuevos.

Para forzar la propagación inmediata sin esperar esos 15 min, **re-armar el
marcador a mano** (una vez que Redis volvió):

```bash
# Un usuario puntual — poné el epoch actual y un TTL holgado (24h, igual al del sistema):
redis-cli SET user_inv:<userUuid> $(date +%s) EX 86400
```

```bash
# Todos los usuarios de un rol, si el fan-out entero se perdió. Requiere la lista
# de UUIDs de usuarios con ese rol (obtenerla del panel o de la BD):
for u in <uuid1> <uuid2> <uuid3>; do
  redis-cli SET user_inv:$u $(date +%s) EX 86400
done
```

Alternativa "dura" (cerrar sesión del todo, no solo forzar refresh): revocar los
refresh tokens del usuario — borra el set `user_refresh:<userUuid>` y sus claves
`refresh:<jti>` — con lo que el próximo refresh falla y el usuario debe volver a
loguearse. Es más agresivo; normalmente el `SET user_inv:` alcanza.

### ⚠️ No hacer

- **NO** `redis-cli DEL user_inv:<userUuid>` para "forzar refresh": eso **borra**
  el marcador de invalidación → hace lo **contrario** (deja vivir el token viejo).
  El marcador se **pone** (`SET`), no se borra.
- La clave **no** es `refresh:<userUuid>`. `refresh:<jti>` guarda refresh tokens
  (indexados por JTI, no por usuario); borrarlos desloguea, no propaga permisos.

## Nota — provider `memory`

Con `AUTH_TOKEN_BLACKLIST_PROVIDER=memory` (single-node, sin Redis) el marcador
vive en memoria del proceso en vez de Redis: la propagación funciona igual dentro
del nodo, pero no hay `redis-cli` que tocar ni forma de re-armarlo a mano —
reiniciar el backend limpia todos los marcadores (y con ellos, cualquier
invalidación pendiente).

## Referencias

- `JwtAuthenticationFilter` — el chequeo `iat < user_inv` por request.
- `RoleService.updateRolePermissions` → `invalidateAllUsersOf` — el fan-out.
- `RedisTokenBlacklistService` — claves `user_inv:`, `refresh:`, `user_refresh:`,
  `blacklist:` y sus TTL (`USER_INV_TTL = 24h`).
- `AuthService.refresh` — relee permisos de BD al emitir el nuevo access token.
- Config: `jwt.access-expiration-minutes` (15), `auth.token-blacklist.provider`.
