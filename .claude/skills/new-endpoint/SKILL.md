---
name: new-endpoint
description: Agregar un endpoint REST a un módulo existente del backend (listado con filtros, /options, upload, descarga presigned, cache o idempotencia). Usar al exponer una operación nueva de la API.
---
# Nuevo endpoint

1. Ubicar el controller del módulo (`modules/<módulo>/*Controller.java`) y copiar el estilo del endpoint más parecido.
2. Leer solo la sección aplicable de [`.ai/playbooks/new-endpoint.md`](../../../.ai/playbooks/new-endpoint.md) (`grep -n '^## ' .ai/playbooks/new-endpoint.md`): listado §5, `/options` §5b, upload §6, presigned §7, cache §8, idempotencia §9, tests §10.
3. Convenciones de ruta/JSON/estado vacío/permisos: `.claude/rules/api-layer.md` (se carga sola al tocar el controller).
4. Permiso nuevo → migración (skill `new-migration`). Texto de error → `messages*.properties` en los 3 idiomas.
5. Si el frontend lo consume, mencionar en el PR la ruta y la forma del DTO.
