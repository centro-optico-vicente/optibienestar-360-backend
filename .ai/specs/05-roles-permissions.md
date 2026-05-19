# 05 — Roles y permisos (RBAC granular)

## Roles base

| Rol | Cantidad esperada | Resumen |
|---|---|---|
| `ADMIN` | 2-5 | Super-usuario del sistema |
| `OPERADOR` | 5-20 | Personal interno con permisos limitados |
| `ALIADO_USER` | 200-1000 | Operador por cuenta de aliado |
| `AFILIADO_USER` | hasta 100k | Afiliado titular |
| `PROMOTOR` | 10-100 | Vendedor de membresías |
| `OPERADOR_MEDICO` (opcional) | pocos | Operador con acceso médico |

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

### ADMIN
Todos los permisos. Implementación: marca `is_super = TRUE` o asignar explícitamente todos.

### OPERADOR
- Todos los USER_* excepto CHANGE_ROLE
- Todos los MEMBER_* excepto MEDICAL_RECORD_*
- Todos los ALLY_*
- Todos los PLAN_*, MEMBERSHIP_*
- PAYMENT_REGISTER + APPROVE + REJECT + VIEW_ALL
- PROMOTER_*, COMMISSION_VIEW_ALL + PAYOUT
- REFERRAL_CODE_CREATE + VIEW_ALL
- REPORT_VIEW_DASHBOARD + EXPORT

### OPERADOR_MEDICO (opcional)
- Todos los del OPERADOR
- MEDICAL_RECORD_VIEW + UPDATE

### ALIADO_USER
- ALLY_VIEW_OWN
- ALLY_VALIDATE_MEMBER
- ALLY_REGISTER_USAGE

### AFILIADO_USER
- MEMBER_VIEW_OWN
- MEMBERSHIP_VIEW_OWN
- PAYMENT_VIEW_OWN + REGISTER (sólo los propios)
- REFERRAL_CODE_VIEW_OWN

### PROMOTOR
- MEMBER_CREATE (asignado a sí mismo automáticamente)
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

`V11__seed_roles.sql` + `V11b__seed_permissions.sql` + `V11c__assign_permissions_to_roles.sql` (o todo en V11 si es manejable).

## Auditoría

Cambios de roles/permisos:
- INSERT en `audit_log` con `action = 'ROLE_CHANGE'`
- Forzar refresh de JWT en el siguiente request (claims viejos no reflejan cambio)

## Referencias

- [04-security.md](04-security.md)
- [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md)
- [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md)
- [`../playbooks/new-role.md`](../playbooks/new-role.md)
