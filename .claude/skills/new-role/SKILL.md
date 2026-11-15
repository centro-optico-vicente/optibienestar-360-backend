---
name: new-role
description: Crear un rol o permiso nuevo, o cambiar qué permisos tiene un rol (migración, @PreAuthorize, propagación a sesiones abiertas). Usar al tocar roles, permisos o accesos.
---
# Roles y permisos

| Caso | Playbook |
|---|---|
| Permiso o rol **nuevo** | [`.ai/playbooks/new-role.md`](../../../.ai/playbooks/new-role.md) (migración modelo: paso 2) |
| Cambiar los permisos de un rol **existente** | Se hace desde el panel, no con migración: [`.ai/playbooks/edit-role-permissions.md`](../../../.ai/playbooks/edit-role-permissions.md) |

Lo que más se olvida:
- Permisos **granulares por acción** (`X_VIEW_ALL`, `X_VIEW_OWN`, `X_CREATE`, `X_UPDATE`, `X_DELETE`, `X_REPORT`…), nunca `X_MANAGE`. `domain_id` obligatorio (por `permission_domains.code`).
- `SYSTEM` recibe solo cada permiso nuevo (trigger `trg_permissions_grant_system`); los demás roles hay que asignarlos explícitamente en la migración.
- `@PreAuthorize("hasAuthority('X')")`, nunca `hasRole`. El JWT lleva permisos, no roles: para saber el rol del actor, cargarlo de BD (`findWithRolesByUuid` + `hasSystemRole`).
- `USER_CHANGE_ROLE` no significa "cambiar roles": marca escrituras solo-SYSTEM sobre catálogos. El que habilita gestionar roles es `ROLE_PERMISSION_EDIT`.
- Etiquetas visibles (`permission_domains.label`, descripciones) en español; nombres en inglés.
- Si el frontend debe mostrar u ocultar algo con el permiso, mencionarlo en el PR (`v-can`, menú).
