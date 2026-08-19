# Guía de Integración de los Servicios de Reportes (PDF y Excel)

Esta documentación está dirigida a los desarrolladores del **Frontend (Nuxt / Vue 3)** para la integración, consumo y extensión de los nuevos servicios de reportes en PDF y exportaciones a Excel (XLSX) de **OptiBienestar 360**.

---

## 1. Visión General de los Servicios de Reportes

El módulo de reportes genéricos en el Backend (`/v1/documents`) permite generar:

1. **Reporte de Listado Completo / Tabla (`GET /v1/documents/tables/{targetTable}`)**: Imprime la lista completa de registros autorizados para esa entidad en formato tabla (A4 Horizontal / Landscape para PDF, o Hoja de Cálculo limpia en XLSX).
2. **Ficha Individual de Registro (`GET /v1/documents/records/{entityOrTable}/{identifier}`)**: Imprime el detalle completo de un único registro especificando su `UUID` o ID (A4 Vertical / Portrait para PDF, o XLSX).
3. **Reporte por Payload Personalizado (`POST /v1/documents/generic`)**: Recibe cualquier objeto JSON y genera una ficha formateada automáticamente.

---

## 2. Endpoints del Backend

| Endpoint | Método | Descripción | Parámetros Query |
|---|---|---|---|
| `/v1/documents/tables/{targetTable}` | `GET` | Genera reporte de tabla completa | `format` (`PDF` \| `XLSX`), `title`, `subtitle`, `limit` (default: 500) |
| `/v1/documents/records/{entityOrTable}/{identifier}` | `GET` | Genera ficha individual de registro | `format` (`PDF` \| `XLSX`), `title`, `subtitle` |
| `/v1/documents/generic` | `POST` | Genera reporte desde un JSON body | Body JSON (`title`, `subtitle`, `identifier`, `format`, `data`) |

> **Seguridad y Permisos**:
> Todos los endpoints requieren el encabezado `Authorization: Bearer <JWT_TOKEN>`.
> El backend evalúa automáticamente el rol del usuario conectado. Si el usuario no es un actor `SYSTEM`, los registros técnicos de infraestructura y usuarios del sistema (ej. *Fénix Core*) quedan excluidos automáticamente del reporte.

---

## 3. Mapeo de Slugs y Tablas Soportadas

El parámetro `{targetTable}` o `{entityOrTable}` acepta tanto los nombres en plural/singular en inglés como los slugs de ruta del Dashboard:

| Módulo en Frontend | Ruta en Dashboard | Slug aceptado en Endpoint |
|---|---|---|
| **Aliados** | `/dashboard/allies` | `allies` o `ally` |
| **Afiliados / Miembros** | `/dashboard/members` | `members` o `member` |
| **Pagos** | `/dashboard/payments` | `payments` o `payment` |
| **Planes** | `/dashboard/plans` | `plans` o `plan` |
| **Promotores** | `/dashboard/promoters` | `promoters` o `promoter` |
| **Usuarios** | `/dashboard/users` | `users` o `user` |
| **Roles** | `/dashboard/roles` | `roles` o `role` |
| **Comisiones** | `/dashboard/commissions` | `commissions` o `commission` |
| **Tareas Programadas** | `/dashboard/scheduled-jobs` | `scheduled_jobs` o `scheduled-jobs` |
| **Membresías** | `/dashboard/memberships` | `memberships` o `membership` |

---

## 4. Arquitectura en el Frontend (Nuxt 3 / Vue 3)

El frontend incluye un composable y un componente listo para usar:

### A. Composable (`useDocumentReports.ts`)

Ubicación: `app/composables/useDocumentReports.ts`

```typescript
const { downloadTableReport, downloadRecordReport, isDownloading } = useDocumentReports()
```

#### Métodos Disponibles:
1. `downloadTableReport(tableSlug: string, format: 'PDF' | 'XLSX', options?: { title?: string, subtitle?: string, limit?: number })`:
   Descarga el archivo PDF o XLSX con la lista de la tabla especificada.
2. `downloadRecordReport(tableSlug: string, identifier: string, format: 'PDF' | 'XLSX', options?: { title?: string, subtitle?: string })`:
   Descarga la ficha individual del registro en PDF o XLSX.

---

### B. Componente Reutilizable (`<ReportPrintButton />`)

Ubicación: `app/components/ReportPrintButton.vue`

Este componente renderiza un botón principal de **Imprimir PDF** (con icono de impresora) y un botón adjunto de **Exportar a Excel** (con icono de hoja de cálculo).

#### Props del Componente:

| Prop | Tipo | Requerido | Descripción |
|---|---|---|---|
| `tableName` | `string` | No | Slug de la tabla (ej: `'allies'`). Si se omite, se infiere automáticamente de la ruta activa (`useRoute().path`). |
| `recordUuid` | `string` | No | UUID del registro. Si se envía, se generará la **Ficha Individual**. Si se omite, se generará el **Listado Completo**. |
| `title` | `string` | No | Título personalizado para el encabezado del reporte. |
| `subtitle` | `string` | No | Subtítulo personalizado para el encabezado del reporte. |

---

## 5. Ejemplos de Uso en el Frontend

### Ejemplo 1: Botón de Imprimir Listado en una Vista de Tabla (`index.vue`)

En cualquier vista de listado de Dashboard (ej. `app/pages/dashboard/allies/index.vue`), simplemente agrega el componente `<ReportPrintButton />` en la cabecera de la página:

```vue
<template>
  <div class="space-y-5">
    <!-- Cabecera -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold">Listado de Aliados</h1>
        <p class="text-sm text-gray-500">Gestión de aliados comerciales</p>
      </div>

      <!-- Botones de Acción -->
      <div class="flex items-center gap-2">
        <!-- Botón de Imprimir PDF y Exportar Excel -->
        <ReportPrintButton />

        <!-- Botón de Crear Nuevo -->
        <UButton color="primary" icon="i-lucide-plus" @click="openCreate">
          Nuevo Aliado
        </UButton>
      </div>
    </div>

    <!-- Tabla de datos... -->
  </div>
</template>
```

---

### Ejemplo 2: Botón de Imprimir Ficha Individual en Detalle (`[uuid].vue`)

En la vista de detalle de un registro (ej. `app/pages/dashboard/allies/[uuid].vue`):

```vue
<script setup lang="ts">
const route = useRoute()
const allyUuid = computed(() => route.params.uuid as string)
</script>

<template>
  <div class="space-y-5">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold">Detalle del Aliado</h1>

      <div class="flex items-center gap-2">
        <!-- Imprime la ficha individual del aliado -->
        <ReportPrintButton :record-uuid="allyUuid" />

        <UButton color="neutral" variant="ghost" to="/dashboard/allies">
          Volver
        </UButton>
      </div>
    </div>
  </div>
</template>
```

---

### Ejemplo 3: Invocación Programática desde JavaScript / TypeScript

Si necesitas disparar la descarga desde un botón personalizado o un menú desplegable:

```vue
<script setup lang="ts">
const { downloadTableReport, downloadRecordReport, isDownloading } = useDocumentReports()

// Descargar lista de pagos en Excel
async function exportPaymentsToExcel() {
  await downloadTableReport('payments', 'XLSX', {
    title: 'Reporte Mensual de Pagos',
    subtitle: 'Correspondiente a Agosto 2026'
  })
}

// Descargar ficha de un afiliado en PDF
async function printMemberCard(memberUuid: string) {
  await downloadRecordReport('members', memberUuid, 'PDF', {
    title: 'Ficha de Afiliación Oficial'
  })
}
</script>

<template>
  <div class="flex gap-3">
    <UButton 
      :loading="isDownloading" 
      icon="i-lucide-file-spreadsheet" 
      color="success" 
      @click="exportPaymentsToExcel"
    >
      Exportar Pagos (Excel)
    </UButton>
  </div>
</template>
```

---

### Ejemplo 4: Consumo por cURL / HTTP Crudo

Para probar desde cURL o Postman:

```bash
# Descargar listado de aliados en PDF
curl -X GET "https://api.centroopticovicente.com/v1/documents/tables/allies?format=PDF" \
  -H "Authorization: Bearer <TU_TOKEN_JWT>" \
  --output listado_aliados.pdf

# Descargar ficha de un pago en Excel (XLSX)
curl -X GET "https://api.centroopticovicente.com/v1/documents/records/payments/a567d6e2-56ff-4311-b404-84197c0e0d18?format=XLSX" \
  -H "Authorization: Bearer <TU_TOKEN_JWT>" \
  --output ficha_pago.xlsx
```

---

## 6. Aspectos Clave de Presentación en los Reportes

* **Moneda**: Todos los valores numéricos monetarios se formatean automáticamente en Bolívares (`Bs. 1.250,50`).
* **Fechas**: Formateadas en español venezolano (`dd/MM/yyyy` y `hh:mm a`).
* **Disposición PDF**:
  * **Listados / Tablas**: Renderizado en **Landscape (Horizontal)** con fuente compacta para que quepan hasta 8 columnas de información sin recortes.
  * **Fichas Individuales**: Renderizado en **Portrait (Vertical)** estructurado en secciones y tarjetas limpias.
