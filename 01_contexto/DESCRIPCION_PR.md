# feat(system): SystemConfig singleton table, dynamic optional PDF footer & granular report security

## 📌 Resumen de Cambios

Este PR introduce el soporte de configuración global del sistema (`SystemConfig`) con patrón *singleton*, inyectando dinámicamente el pie de página opcional en el motor de reportes PDF/HTML genéricos (`GenericHtmlPdfService`), resolviendo problemas de i18n en los encabezados de las plantillas y alineando el esquema de seguridad con los permisos granulares por dominio (`<DOMAIN>_REPORT_GENERATE`).

---

## 🛠️ Detalle de Cambios Realizados

### 1. ⚙️ Configuración Global del Sistema (`SystemConfig`)
- **Base de datos (`V67__system_configs.sql`):** Se creó la tabla `app.system_configs` (patrón de 1 fila activa nuleable para parámetros globales) con la columna `report_footer TEXT` opcional.
- **Backend Service & Controller:**
  - `SystemConfig.java`: Entidad JPA auditada con extensión de `BaseAuditEntity`.
  - `SystemConfigRepository.java`: Método `findFirstByActiveTrue()`.
  - `SystemConfigService.java`: Gestión de ciclo de vida del pie de página de reportes (`getReportFooter()`, `updateReportFooter()`).
  - `SystemConfigController.java`: Endpoints `GET /v1/system-configs` y `PUT /v1/system-configs`.
  - `UpdateSystemConfigRequest.java`: DTO con validación de tamaño máximo opcional (`@Size(max = 500)`).

### 2. 🖨️ Plantillas HTML y Renderizado PDF
- **Pie de página dinámico:** Se inyecta la variable `reportFooter` desde `GenericHtmlPdfService` en el contexto global de Thymeleaf para todos los reportes en PDF.
- **Formato Opcional:** Si `reportFooter` no está configurado (es `null` o vacío), las plantillas `generic_record_card.html` y `generic_table_list.html` no renderizan ningún texto en el margen inferior `@bottom-left` del CSS.

### 3. 🌐 Internacionalización (i18n)
- **Corrección de Encabezado Faltante:** Se añadió la clave `document.record_card.general_information=Información General` en `messages_es.properties`, `messages.properties` y `messages_en.properties` (corrigiendo el fallback `??document.record_card.general_information_es??`).
- **Títulos de Entidades:** Se agregaron traducciones singulares y plurales para `system_configs` y `referrals`.
- **Validación DTO:** Se incorporó el mensaje `system_config.report_footer.max_size`.

### 4. 🔐 Seguridad y Permisos Granulares
- **Alineación con PR #188:** Se actualizó `@PreAuthorize` en `GenericDocumentController` y `SystemConfigController` para evaluar los permisos granulares por dominio (`<DOMAIN>_REPORT_GENERATE` como `ALLY_REPORT_GENERATE`, `MEMBER_REPORT_GENERATE`, `USER_REPORT_GENERATE`, `REPORT_REPORT_GENERATE`, etc.).
- **Limpieza:** Se removió el permiso genérico `REPORT_PRINT`.

### 5. 📚 Documentación para Frontend
- Se creó `01_contexto/GUIA_PERMISOS_REPORTES_FRONTEND.md` con ejemplos Vue 3 / Nuxt 3 para la visibilidad condicional de botones (`v-if="hasPermission(...)"`).

---

## 🧪 Plan de Verificación

- [x] **Pruebas Unitarias:** Ejecutadas con éxito (`.\gradlew.bat test --tests "com.fenixcore.optibienestar360.modules.system.SystemConfigServiceTest" --tests "com.fenixcore.optibienestar360.modules.document.generic.GenericRecordReportServiceTest"`).
- [x] **Migración Flyway:** Verificada secuencialmente como `V67` tras las migraciones `V60-V66`.
- [x] **Construcción Completa:** Verificada mediante Gradle sin errores de compilación (`BUILD SUCCESSFUL`).
