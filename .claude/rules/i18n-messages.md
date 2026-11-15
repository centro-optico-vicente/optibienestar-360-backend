---
paths:
  - "src/main/resources/messages*.properties"
  - "src/main/resources/ValidationMessages*.properties"
  - "src/main/resources/templates/**"
---
# Mensajes y plantillas

- Cada código nuevo va en los **tres** archivos (`messages.properties`, `_es`, `_en`; igual para `ValidationMessages`). Agregarlo con `grep -n` del prefijo para ubicar la sección y editar ahí; no leer el archivo entero.
- Códigos `dominio.subdominio.motivo` en inglés; textos en español claro para el usuario final de Venezuela (`es-VE`).
- Plantillas de correo `<name>_es.html` y `<name>_en.html` en pareja. Detalle en [`.ai/specs/14-i18n.md`](../../.ai/specs/14-i18n.md).
