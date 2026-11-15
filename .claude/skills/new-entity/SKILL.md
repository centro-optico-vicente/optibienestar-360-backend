---
name: new-entity
description: Crear una entidad de negocio completa en el backend (migración, entity, repository, DTOs, mapper, service, controller, permisos, tests). Usar al agregar un recurso CRUD nuevo.
---
# Nueva entidad

Seguir [`.ai/playbooks/new-entity.md`](../../../.ai/playbooks/new-entity.md) (9 pasos). Antes de escribir:

- Copiar el patrón de un módulo vecino parecido en `src/main/java/com/fenixcore/optibienestar360/modules/` en vez de inventar estructura (ej. `bank` para catálogo simple, `member` para recurso con hijos).
- Migración con la skill `new-migration`; entidad extiende `BaseEntity`.
- Permisos granulares por acción en la misma migración y `entity_config` si aplica (paso 7 del playbook).
- Listado compacto `*ListItemDto` + detalle `GET /{uuid}`; `/options` si alimenta selects.
- Al terminar: marcar el ítem en `.ai/checklists/` y una línea en `.ai/context/current-state.md`.
