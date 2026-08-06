# 05 — Roles y permisos (RBAC granular)

## Roles base

| Rol | Cantidad esperada | Resumen |
|---|---|---|
| `SYSTEM` | 1-2 | Administrador técnico — acceso total incluyendo gestión de roles |
| `ADMINISTRADOR` | 2-5 | Administrador del negocio — acceso operativo completo sin gestión de roles |
| `OPERADOR` | 5-20 | Personal interno sin acceso médico ni gestión de roles |
| `OPERADOR_MEDICO` | pocos | Operador con acceso adicional a historial médico |
| `ALIADO` | 200-1000 | Operador del aliado — validación de afiliados y registro de uso |
| `AFILIADO` | hasta 100k | Portal del afiliado — consulta y gestión de datos propios |
| `PROMOTOR` | 10-100 | Vendedor de membresías y consulta de comisiones propias |

**Convención de nombres:** `SYSTEM` en inglés (rol técnico), resto en español sin sufijos redundantes.

Detalle de cada perfil en [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md).

## Permisos granulares

### Users

- `USER_CREATE`, `USER_UPDATE`, `USER_DELETE`, `USER_VIEW_ALL`
- `USER_CHANGE_ROLE`
- `USER_RESET_PASSWORD`

### Members

- `MEMBER_CREATE`, `MEMBER_UPDATE`, `MEMBER_DELETE`
- `MEMBER_VIEW_ALL`, `MEMBER_VIEW_OWN`
- `MEMBER_UPLOAD_DOCUMENT`
- `MEDICAL_RECORD_VIEW`, `MEDICAL_RECORD_UPDATE` (sensible)

### Allies

- `ALLY_CREATE`, `ALLY_UPDATE`, `ALLY_DELETE`
- `ALLY_VIEW_ALL`, `ALLY_VIEW_OWN`
- `ALLY_AGREEMENT_MANAGE`
- `ALLY_VALIDATE_MEMBER` (uso del validador)
- `ALLY_REGISTER_USAGE`

### Memberships / Plans

- `PLAN_CREATE`, `PLAN_UPDATE`, `PLAN_DELETE`, `PLAN_VIEW_ALL`
- `MEMBERSHIP_CREATE`, `MEMBERSHIP_UPDATE`, `MEMBERSHIP_CANCEL`, `MEMBERSHIP_REACTIVATE`
- `MEMBERSHIP_VIEW_ALL`, `MEMBERSHIP_VIEW_OWN`

### Payments

- `PAYMENT_REGISTER` (cualquiera puede crear PENDING_REVIEW)
- `PAYMENT_APPROVE`, `PAYMENT_REJECT` (sólo admin/operador)
- `PAYMENT_VIEW_ALL`, `PAYMENT_VIEW_OWN`

### Promoters / Commissions

- `PROMOTER_CREATE`, `PROMOTER_UPDATE`, `PROMOTER_VIEW_ALL`
- `COMMISSION_VIEW_ALL`, `COMMISSION_VIEW_OWN`
- `COMMISSION_PAYOUT`

### Referrals

- `REFERRAL_CODE_CREATE`, `REFERRAL_CODE_VIEW_ALL`, `REFERRAL_CODE_VIEW_OWN`

### Reports

- `REPORT_VIEW_DASHBOARD`
- `REPORT_EXPORT`

## Asignación rol → permisos

> Los conteos de abajo son la **baseline del seed V6** (~50 permisos) y quedaron desactualizados: migraciones posteriores sumaron permisos por dominio (`JOB_*` V22, `NOTIFICATION_*` V28, `ROLE_*` V32, `CATALOG_*_WRITE` V33, `CATALOG_VIEW_ALL` V34, y más allá de V34 también `SUBSIDY_*` V41 y `COMMISSION_TIER_*`/`LEADERBOARD_*` V42) y SYSTEM los recibe automáticamente (trigger V30). El total real hoy es de **~86 permisos**, no 49-50, y sigue creciendo con cada migración. Fuente de verdad del catálogo vivo: `GET /v1/admin/permissions` y las migraciones `db/migration/`. Actualizar estos números es un ítem aparte.

### SYSTEM (49 permisos)
Todos los permisos sin excepción. CROSS JOIN en el seed.

### ADMINISTRADOR (48 permisos)
Todos excepto `USER_CHANGE_ROLE` (cambiar roles es operación técnica, no de negocio).

### OPERADOR (43 permisos)
- Todos los USER_* excepto USER_CHANGE_ROLE
- Todos los MEMBER_* excepto MEDICAL_RECORD_*
- Todos los ALLY_*
- Todos los PLAN_*, MEMBERSHIP_*
- PAYMENT_REGISTER + APPROVE + REJECT + VIEW_ALL
- Todos los PROMOTER_*, COMMISSION_VIEW_ALL + PAYOUT
- REFERRAL_CODE_CREATE + VIEW_ALL
- REPORT_VIEW_DASHBOARD + EXPORT

### OPERADOR_MEDICO (45 permisos)
Igual que OPERADOR más MEDICAL_RECORD_VIEW + MEDICAL_RECORD_UPDATE.

### ALIADO (3 permisos)
- ALLY_VIEW_OWN
- ALLY_VALIDATE_MEMBER
- ALLY_REGISTER_USAGE

### AFILIADO (5 permisos)
- MEMBER_VIEW_OWN
- MEMBERSHIP_VIEW_OWN
- PAYMENT_VIEW_OWN + PAYMENT_REGISTER (sólo los propios)
- REFERRAL_CODE_VIEW_OWN

### PROMOTOR (5 permisos)
- MEMBER_CREATE (para sus afiliados)
- MEMBERSHIP_CREATE (para sus afiliados)
- PAYMENT_REGISTER (para sus afiliados)
- COMMISSION_VIEW_OWN
- REFERRAL_CODE_VIEW_OWN

## Implementación

### Anotaciones en endpoints

```java
@PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
public PaymentDTO approve(@PathVariable UUID id) { /* ... */ }

@PreAuthorize("hasAnyAuthority('MEMBER_VIEW_ALL', 'MEMBER_VIEW_OWN')")
public MemberDTO findById(@PathVariable UUID id) { /* ... */ }

// Para "VIEW_OWN" con check adicional en service:
@PreAuthorize("hasAuthority('MEMBER_VIEW_OWN') and @memberSecurity.canRead(#id, authentication)")
public MemberDTO findMine(@PathVariable UUID id) { /* ... */ }
```

### Component custom `MemberSecurity`

```java
@Component("memberSecurity")
public class MemberSecurity {
    public boolean canRead(UUID memberId, Authentication auth) {
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        if (user.hasAuthority("MEMBER_VIEW_ALL")) return true;
        if (user.hasAuthority("MEMBER_VIEW_OWN")) {
            // verificar que memberId pertenece a user.getUserId()
            return memberRepository.existsByMemberIdAndUserId(memberId, user.getUserId());
        }
        return false;
    }
}
```

### Asignación inicial (seed Flyway)

`V5__seed_roles.sql` — roles, permisos y asignación rol→permisos en un solo archivo.

## Auditoría

Cambios de roles/permisos:
- INSERT en `audit_log` con `action = 'ROLE_CHANGE'`
- Forzar refresh de JWT en el siguiente request (claims viejos no reflejan cambio)

## RBAC dinámico y modelos de autorización (evaluación)

> **Estado (2026-07-22):** fase mínima **implementada** — roles con permisos editables desde el panel.
>
> - **Tabla `permission_domains`** (V5) — agrupa los permisos por dominio con `code`/`name`/`icon`/`display_order`, para que el panel los renderice con label en español + icono y en orden. Hoy son **14 dominios**: los 10 de V5 (`USERS`, `MEMBERS`, `ALLIES`, `PLANS`, `MEMBERSHIPS`, `PAYMENTS`, `PROMOTERS`, `COMMISSIONS`, `REFERRALS`, `REPORTS`) + `SCHEDULED_JOBS` (V22), `NOTIFICATIONS` (V28), `CATALOGS` (V32) y `SUBSIDIES` (V41). Cada `permission` tiene su `domain_id` FK.
> - **Permiso `ROLE_PERMISSION_EDIT`** — es el gate de la edición de permisos de rol. Sembrado a **SYSTEM** en V6 y otorgado a **ADMINISTRADOR** en V31 (ADMINISTRADOR edita cualquier rol **salvo SYSTEM**, que queda reservado a actores SYSTEM). Nota de reconciliación: el `USER_CHANGE_ROLE` que la versión anterior de este doc citaba como contraparte **ya no existe** — V32 lo renombró a `CATALOG_WRITE` y V33 lo partió en `CATALOG_<entidad>_WRITE`; la escritura de catálogos y la gestión de roles son hoy permisos separados y sin relación (ver [`../playbooks/edit-role-permissions.md`](../playbooks/edit-role-permissions.md)).
> - **Los 3 endpoints** de la superficie de edición, todos implementados y gateados por `ROLE_PERMISSION_EDIT`:
>   - `GET /v1/admin/permissions` — catálogo agrupado dominios → permisos, ordenado y listo para renderizar (`PermissionController`).
>   - `GET /v1/admin/roles/{uuid}/permissions` — el set actual de UUIDs de permisos del rol.
>   - `PUT /v1/admin/roles/{uuid}/permissions` — reemplaza el set (semántica de reemplazo), con **guard SYSTEM** (403 `role.system.not_editable` si un actor no-SYSTEM edita SYSTEM), **anti-lockout** (422 `role.auto_lockout` si el actor se quitaría a sí mismo `ROLE_PERMISSION_EDIT`) y validación de permisos (422 `role.permission.uuid.unknown` si un UUID no existe). Cubierto por `RoleServiceGuardsTest` (lógica) + `AdminRoleControllerIT` (authz + mapeo HTTP).
>
> Más allá de la fase mínima, V32 sumó el **CRUD de roles** (`GET`/`POST`/`PUT`/`DELETE /v1/admin/roles`) con permisos propios `ROLE_VIEW` / `ROLE_CREATE` / `ROLE_UPDATE` / `ROLE_DELETE`.

### Punto clave: la autorización ya es *por permiso*, no por rol

Los `@PreAuthorize` usan `hasAuthority('PERMISSION')` y el JWT lleva el claim `permissions` (aplanado desde los roles del usuario). El nombre del rol **nunca** se evalúa en la autorización. Consecuencia:

- **Roles editables ya son compatibles sin tocar ningún `@PreAuthorize`.** Si un admin crea/edita un rol y le asigna permisos existentes, solo cambia qué permisos acumula el usuario; el código de autorización no se entera.
- **Restricción:** los **permisos permanecen fijos en código** (cada string de `hasAuthority('X')` debe existir y estar referenciado). Crear permisos en runtime no sirve — no habría `@PreAuthorize` que los use. Por eso el modelo dinámico sería: **permisos = catálogo fijo (solo lectura)**, **roles = contenedores editables** de esos permisos.
- **El _token staleness_ ya está resuelto:** al cambiar los permisos de un rol, `RoleService.updateRolePermissions` marca a cada usuario afectado (`user_inv:<userUuid>` en Redis con el epoch actual), y el `JwtAuthenticationFilter` compara en **cada request** el `iat` del access token contra esa marca → rechaza el token emitido antes del cambio → el cliente refresca y el refresh **relee los permisos de BD**. **La propagación es inmediata**, no la ventana de 15 min que consideraba la versión anterior de este doc. Detalle operativo y el único modo de falla (Redis caído al guardar → el marcador best-effort se salta) en [`../playbooks/edit-role-permissions.md`](../playbooks/edit-role-permissions.md).

### Comparativa de modelos

| Modelo | Qué es | Ventajas | Desventajas | Encaje en OptiBienestar |
|---|---|---|---|---|
| **RBAC granular** *(actual)* | Permisos agrupados en roles; autorización por permiso. Permisos fijos en código, roles en BD. | Simple y predecible; fácil de auditar; nativo en Spring (`@PreAuthorize`); sin lookups extra (permisos en el JWT); ya implementado. | Permisos no editables en runtime; los casos contextuales (p. ej. "solo sus propios afiliados") requieren checks ad-hoc en service; puede crecer el nº de permisos. | ✅ Cubre ~95% de los casos. Los `VIEW_OWN` ya se resuelven con components custom (`@memberSecurity.canRead`). **Recomendado mantener.** |
| **ABAC** (Attribute-Based) | Decisiones por atributos (usuario, recurso, entorno) evaluados en reglas. Ej.: "ver si `recurso.region == usuario.region` y en horario laboral". | Muy flexible; reglas contextuales sin tocar código por cada caso; escala a políticas complejas. | Alta complejidad (motor de reglas + gestión de políticas); más difícil de auditar/razonar; riesgo de reglas contradictorias; evaluación por request. | ⚠️ Los pocos casos contextuales actuales ya se cubren con checks puntuales → ABAC completo es sobre-ingeniería hoy. |
| **OPA** (Open Policy Agent) | Motor de políticas externo (sidecar/servicio) con lenguaje Rego; centraliza la autorización fuera de la app. | Políticas declarativas, centralizadas y versionables; desacopladas del código; reutilizable cross-servicio; testeable aislado. OSS (compatible [ADR 0004](../../../centro-optico-vicente/.ai/decisions/0004-only-free-tools.md)). | Infra extra (sidecar/servicio) → choca con "single VPS / infra mínima" ([ADR 0003](../../../centro-optico-vicente/.ai/decisions/0003-infrastructure.md)); latencia por decisión; curva de Rego; otro componente que mantener y monitorear. | ❌ Overkill para un monolito Spring en un VPS. Tendría sentido recién con arquitectura multi-servicio o políticas muy dinámicas. |

**Veredicto:** mantener **RBAC granular**; los casos contextuales se resuelven con `PermissionEvaluator`/components custom (patrón ya en uso). Reevaluar **ABAC/OPA** solo si aparece multi-servicio o reglas de negocio fuertemente dependientes de atributos/contexto.

## Referencias

- [04-security.md](04-security.md)
- [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md)
- [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md)
- [`../playbooks/new-role.md`](../playbooks/new-role.md)
