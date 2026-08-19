# Guía de Permisos Granulares para Reportes y Exportaciones (Frontend Nuxt 3)

Esta guía documenta el mapa completo de permisos granulares por dominio creados en el backend para controlar la visibilidad y ejecución de reportes PDF y exportaciones Excel (XLSX) en **OptiBienestar 360**.

---

## 1. 🛡️ Catálogo de Permisos Granulares por Dominio

Ya **no existe un permiso general único** (`REPORT_PRINT`). La generación de reportes y fichas ahora se rige estrictamente por el permiso granular del módulo correspondiente (`<DOMAIN>_REPORT_GENERATE`):

| Módulo / Tabla Backend | Permiso Requerido | Descripción |
|---|---|---|
| **Aliados** (`allies`) | `ALLY_REPORT_GENERATE` | Imprimir ficha de aliado y exportar listado de aliados |
| **Afiliados / Miembros** (`members`) | `MEMBER_REPORT_GENERATE` | Imprimir ficha de afiliado y exportar listado de afiliados |
| **Usuarios** (`users`) | `USER_REPORT_GENERATE` | Imprimir ficha de usuario y exportar listado de usuarios |
| **Planes** (`plans`) | `PLAN_REPORT_GENERATE` | Imprimir ficha de plan y exportar listado de planes |
| **Membresías** (`memberships`) | `MEMBERSHIP_REPORT_GENERATE` | Imprimir ficha de membresía y exportar listado de membresías |
| **Pagos** (`payments`) | `PAYMENT_REPORT_GENERATE` | Imprimir recibo/ficha de pago y exportar tabla de pagos |
| **Promotores** (`promoters`) | `PROMOTER_REPORT_GENERATE` | Imprimir ficha de promotor y exportar listado de promotores |
| **Comisiones** (`commissions`) | `COMMISSION_REPORT_GENERATE` | Imprimir liquidación y exportar comisiones |
| **Referidos** (`referrals`) | `REFERRAL_REPORT_GENERATE` | Exportar e imprimir listado de referidos |
| **Panel Global / System Config** (`system-configs`) | `REPORT_REPORT_GENERATE` | Ver configuración del sistema y reportes globales |
| **Compartir Enlace** | `REPORT_SHARE` | Generar enlaces públicos o compartidos de reportes |

---

## 2. 💻 Ejemplo de Integración en el Frontend (Vue 3 / Nuxt 3)

### Paso 2.1: Validación de Visibilidad de Botones en Plantillas (`v-if`)

En cada vista de módulo (ej. `pages/dashboard/allies/index.vue`), valida la presencia del permiso correspondiente antes de mostrar los botones de **Imprimir PDF** o **Exportar Excel**:

```vue
<template>
  <div class="flex items-center gap-2">
    <!-- Botón de Exportar Excel -->
    <button 
      v-if="hasPermission('ALLY_REPORT_GENERATE')"
      @click="downloadAllyReport('XLSX')"
      class="btn btn-outline-success"
    >
      <i class="i-lucide-file-spreadsheet mr-1" /> Exportar Excel
    </button>

    <!-- Botón de Imprimir PDF -->
    <button 
      v-if="hasPermission('ALLY_REPORT_GENERATE')"
      @click="downloadAllyReport('PDF')"
      class="btn btn-primary"
    >
      <i class="i-lucide-printer mr-1" /> Imprimir Ficha
    </button>
  </div>
</template>

<script setup lang="ts">
const { hasPermission } = useAuth() // O tu composable / store de autenticación
</script>
```

---

## 3. 🌐 Endpoints Backend y Permisos que Evalúan

| Endpoint | Método | Permiso Evaluado en `@PreAuthorize` |
|---|---|---|
| `/v1/documents/records/{entityOrTable}/{identifier}` | `GET` | Cualquiera de los permisos `<DOMAIN>_REPORT_GENERATE` |
| `/v1/documents/tables/{targetTable}` | `GET` | Cualquiera de los permisos `<DOMAIN>_REPORT_GENERATE` |
| `/v1/documents/generic` | `POST` | Cualquiera de los permisos `<DOMAIN>_REPORT_GENERATE` |
| `/v1/system-configs` | `GET` | `JOB_VIEW_ALL`, `ROLE_VIEW`, `USER_VIEW_ALL` o `REPORT_REPORT_GENERATE` |
| `/v1/system-configs` | `PUT` | `JOB_UPDATE`, `ROLE_UPDATE` o `USER_UPDATE` |

---

## 4. 🔑 Resumen para Roles

- **SYSTEM / ADMINISTRADOR**: Poseen automáticamente todos los permisos `*_REPORT_GENERATE`.
- **OPERADOR / OPERADOR_MEDICO / Otros Roles**: Se les debe asignar explícitamente los permisos de reporte de cada módulo según su alcance operativo (ej. `ALLY_REPORT_GENERATE` para gestión de aliados).
