# 17 — Marca en documentos generados (reportes, PDF y emails)

> **Marca (fuente de verdad):** [hub spec 11 — Identidad visual](https://github.com/centro-optico-vicente/centro-optico-vicente/blob/main/.ai/specs/11-brand-visual-identity.md), que viene del Manual de Marca OB360 (15-oct-2026) y de los archivos del diseñador (18-oct-2026).
> **Relacionadas:** [`15-reporting-documents.md`](15-reporting-documents.md) (motor), [`08-storage-r2.md`](08-storage-r2.md) (logo en R2), [`09-smtp.md`](09-smtp.md) (emails).
> **Estado:** 📝 especificación. Nada de esto está implementado (ver §1).

## 1. Estado actual (verificado en `origin/main`, 2026-10-18)

| Aspecto | Hoy |
|---|---|
| **Logo** | Ningún documento lo trae: 0 `<image>` en los 4 `.jrxml`, 0 `<img>` en `templates/documents/*.html` y en los 33 emails. `organizations.logo_key` existe (V86:32, clave R2) pero **nada lo lee**. No hay assets de marca en `resources/`. |
| **Fuentes** | Jasper usa `fontName="SansSerif"` (lógica). Solo está `jasperreports-fonts` 6.21.4 (DejaVu) y no hay font extension propia. En openhtmltopdf, `GenericHtmlPdfService` no registra fuentes (`useFont`) y el CSS dice `sans-serif`. Los emails usan `Arial, sans-serif`. |
| **Colores** | Fijos en código y fuera de la paleta. Jasper: slate/tailwind (`#0F172A`, `#38BDF8`…). HTML: `#0056b3` (paleta vieja de `LOGOTIPO.docx`). Emails: un acento distinto por tipo (verde `#1f7a4d`, azul `#1a56a0`, rojo `#b22222`, ámbar `#c98300`, púrpura `#7a5cc9`). |
| **Nombre y RIF** | Jasper declara `P_COMPANY_NAME` pero **no lo imprime**; además llega como `@RequestParam companyName` (`GenericDocumentController:219`), no desde `organizations`. No hay parámetro de RIF. Los emails tienen el nombre escrito en duro, 47 veces. |
| **Pie** | `P_REPORT_FOOTER` sale de `system_configs.report_footer`. Si está vacío, cae en `DEFAULT_REPORT_FOOTER = "Creado desde compañía de ejemplo"` (`SystemConfigService:17`). ⚠️ Ese texto aparece en documentos reales. |

## 2. Regla principal: marca resuelta en un solo lugar, con defaults en el classpath

Todo documento obtiene su marca de un único `BrandingService` (`core/branding/`), que entrega un `BrandingContext` inmutable:

```java
public record BrandingContext(
        String displayName,      // organizations.name → "OptiBienestar 360"
        String legalName,        // organizations.legal_name → razón social
        String taxId,            // organizations.tax_identifier → RIF (puede ser null)
        byte[] logoPng,          // logo horizontal a color, fondo claro
        byte[] logoNegativePng,  // logo blanco, fondo oscuro (cabeceras azules)
        byte[] isotypePng,       // isotipo cuadrado
        String logoDataUri,      // "data:image/png;base64,…" del logo a color (HTML/PDF)
        BrandPalette palette,    // §4
        String footer            // system_configs.report_footer o default §2.3
) {}
```

### 2.1 Cadena de resolución del logo (de mayor a menor prioridad)

1. **Logo configurado** por la organización: `organizations.logo_key` → bytes desde R2 (`StorageService.download`).
   - Formatos aceptados: PNG o JPG; máximo 1 MB.
   - Se guarda en caché (Redis o en memoria, TTL ≥ 1 h) y la caché se invalida al cambiar `logo_key`.
2. **Logo por defecto del producto** en el classpath: `src/main/resources/branding/` (§3). Se usa **siempre** que el paso 1 falle o no haya `logo_key`.
3. **Nunca** se rompe la generación por el logo. Si R2 falla, se registra un `WARN` (`branding.logo.fallback`) y se usa el default. Un documento sin logo no es un estado válido.

`logoNegativePng` e `isotypePng` salen siempre del classpath. Si en el futuro la organización puede subir su propio negativo, se agrega `organizations.logo_negative_key` con la misma cadena.

### 2.2 Cadena de resolución de fuentes

1. **Fuente de marca embebida:** Poppins (OFL 1.1, libre de redistribuir) en `src/main/resources/branding/fonts/`, pesos Regular 400, Medium 500, SemiBold 600 y Bold 700, en `.ttf`.
   - Jasper: una font extension propia (`jasperreports_extension.properties` + `fonts/fontsfamily-ob360.xml`) declara la familia `Poppins` con `pdfEmbedded="true"` y `pdfEncoding="Identity-H"`, para que salgan bien tildes y la ñ.
   - openhtmltopdf: `builder.useFont(() -> stream, "Poppins", weight, FontStyle.NORMAL, true)` por cada peso, y CSS `font-family: 'Poppins', 'DejaVu Sans', sans-serif`.
2. **Fallback:** DejaVu Sans, que ya está con `jasperreports-fonts`. Se usa si falta el `.ttf` o si cambia el diseño y se quita Poppins.
3. **Emails:** los clientes de correo no cargan fuentes web de forma confiable. Se usa `font-family: 'Poppins', Arial, sans-serif`, con Arial como alternativa ofimática del manual, y **sin** `@font-face` remoto.
4. ❓ Si el diseñador confirma **Satoshi**, solo se reemplazan los `.ttf` y el nombre de familia en `BrandingService`/la font extension. La licencia de Satoshi en Fontshare permite incrustarla.

### 2.3 Nombre, RIF y pie

- `displayName`, `legalName` y `taxId` salen **siempre** de `organizations`. El `@RequestParam companyName` de `GenericDocumentController` queda **deprecado**: se ignora y se registra en el log.
- Si `taxId` es null, se omite la línea "RIF: …". **No** se imprime un placeholder en documentos de cara al cliente. Los contratos sí usan `[RIF pendiente]` (`TermsTemplateRenderer`).
- **Pie por defecto:** el valor de `system_configs.report_footer`. Si está vacío, `"{displayName} · {legalName} · RIF {taxId}"` (omitiendo las partes nulas). Se elimina `"Creado desde compañía de ejemplo"`.

## 3. Assets por defecto en el classpath

```
src/main/resources/branding/
├── ob360-logo-horizontal.png   # 1200×269, color, fondo transparente, sin contorno sticker
├── ob360-logo-negativo.png     # 1200×269, blanco, para cabeceras Azul Profundo
├── ob360-isotipo.png           # 512×512
├── fonts/
│   ├── Poppins-Regular.ttf
│   ├── Poppins-Medium.ttf
│   ├── Poppins-SemiBold.ttf
│   ├── Poppins-Bold.ttf
│   └── OFL.txt                 # licencia, obligatoria al redistribuir
└── README.md                   # origen de cada archivo
```

- **Origen de los PNG:** los mismos del frontend (`optibienestar-360-frontend/app/assets/brand/`), derivados de los originales del diseñador en `~/workspace/fenix-core/optibienestar/logos/`. Si el diseñador entrega una versión nueva, se cambian en **ambos** repos.
- **Peso:** ≈ 240 KB de PNG y ≈ 600 KB de TTF. Es aceptable en el jar; no se descargan en runtime.

## 4. Paleta en documentos (`BrandPalette`)

Constantes en código, iguales a hub spec 11:

| Rol en el documento | Color | Uso |
|---|---|---|
| Barra de título y cabecera de tabla | Azul Profundo `#003F84` | Fondo, con texto blanco |
| Subtítulos y totales destacados | Azul Vital `#067DBC` | Texto |
| Encabezado de grupo y filas alternas | Gris Perla `#EEF2F6` | Fondo |
| Texto | Gris Carbón `#1B2733` | — |
| Texto secundario (fecha de emisión, pie) | `#64748B` | Se mantiene, contraste 4.8 |
| Bordes | `#E2E8F0` | Se mantiene |
| Montos negativos y errores | `#B42318` | Texto, contraste ≥ 4.5 |
| Positivo o "aprobado" | Verde `#477904` (lime-700) | **Nunca** `#7BC621` como texto (2.1:1) |
| Franja de marca en el pie | Azul Vital · Verde Bienestar · Verde Lima · Naranja Energía | Barra de 4 tramos de 3 px, como la papelería del manual |

**Reemplazos en los `.jrxml`:**

| Antes | Después |
|---|---|
| `#0F172A` / `#1E293B` (barras) | `#003F84` |
| `#38BDF8` (total) | `#067DBC` |
| `#F87171` | `#B42318` |
| `#F0F4F8` / `#F8FAFC` / `#F1F5F9` | `#EEF2F6` |

**HTML:** `#0056b3` → `#003F84` y `#004085` → `#0D498B`.

**Emails:** un solo acento de marca (`#003F84` en cabecera y botón; botón con texto blanco, contraste 10.3). El color semántico por tipo (rojo, ámbar, verde) se conserva **solo** en el bloque de estado, con los tonos de contraste AA de la tabla.

## 5. Aplicación por familia de documento

| Familia | Logo | Dónde | Cómo se pasa |
|---|---|---|---|
| **Jasper** (4 reportes) | Color (o negativo si la barra es azul) | Arriba a la izquierda del `title`, alto ≈ 28 px, con zona de protección (manual: x = alto de la "P") | Parámetro `P_LOGO` (`java.awt.Image` o `InputStream`), más `P_COMPANY_NAME` (displayName), `P_LEGAL_NAME`, `P_TAX_ID` y `P_BRAND_FOOTER`. Los rellena **un solo** helper (`JasperBrandingParameters.from(BrandingContext)`) usado por `JasperReportService`. |
| **HTML → PDF** (`generic_record_card`, `generic_table_list` y los transaccionales pendientes de spec 15) | Color | Cabecera | Variable de modelo `branding` (el `BrandingContext`): `<img th:src="${branding.logoDataUri}">`. Se usa data URI porque `withHtmlContent(html, null)` no tiene URI base. |
| **Emails** (33 plantillas) | Color, ≈ 160 px de ancho (≥ 120 px del manual) | Cabecera | **Imagen inline CID** (`MimeMessageHelper.addInline("brandLogo", …)`, `<img src="cid:brandLogo">`). No usar data URI (Gmail los bloquea) ni URL remota (bloqueada por defecto en muchos clientes). El nombre escrito en duro en las plantillas se reemplaza por `${branding.displayName}`, y el pie por `${branding.footer}`. |
| **Credencial y planilla** (spec 15, pendientes) | Isotipo y logo | Según su diseño | Mismo `BrandingContext`. |

## 6. Configuración por la organización (admin)

- Se puede reemplazar el **logo** subiendo un archivo: `PUT /v1/admin/organizations/me/logo` multipart, que guarda en R2 con `file_type_policies` y actualiza `logo_key`. Pendiente si el admin aún no lo tiene; hoy `logo_key` se edita como texto.
- La **paleta y la fuente** **no** son configurables por organización (una sola marca de producto). Si se necesita white-label, va en un ADR nuevo.
- **Sin configuración**, todo funciona con los defaults del classpath (§2–§3). Es el caso de un ambiente recién levantado.

## 7. Criterios de aceptación y tests

- [ ] **Sin `logo_key`:** los 4 reportes Jasper, los 2 HTML y un email de muestra salen con el logo por defecto, Poppins embebida (verificable con `pdffonts`: `Poppins-*`, `emb yes`), y nombre, razón social y RIF desde `organizations`.
- [ ] **Con `logo_key` válido:** se usa el logo de R2. Con `logo_key` roto o R2 caído, se usa el default y se registra un `WARN`; nunca un 500.
- [ ] Ningún documento imprime "Creado desde compañía de ejemplo".
- [ ] **Tests:**
  - `BrandingServiceTest`: cadena de fallback del logo, pie por defecto con partes nulas.
  - `JasperBrandingParametersTest`: rellena todos los parámetros.
  - Un IT por familia que genere el documento y verifique el logo (por ejemplo, el PDF contiene una imagen) y la fuente embebida (por ejemplo, con PDFBox).
- [ ] Al cambiar los PNG o TTF de `branding/`, actualizar `branding/README.md` y la sección 4.1 de hub spec 11.
