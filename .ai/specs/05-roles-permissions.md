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

## Referencias

- [04-security.md](04-security.md)
- [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md)
- [hub `stakeholders.md`](../../../centro-optico-vicente/.ai/context/stakeholders.md)
- [`../playbooks/new-role.md`](../playbooks/new-role.md)
