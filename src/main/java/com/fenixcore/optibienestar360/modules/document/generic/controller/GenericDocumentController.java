package com.fenixcore.optibienestar360.modules.document.generic.controller;

import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordReportService;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordResolverService;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/v1/documents")
@Tag(name = "Documentos Genéricos", description = "Generación de fichas y reportes genéricos para cualquier registro del sistema")
public class GenericDocumentController {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Map<String, String> SINGULAR_ENTITY_NAMES = Map.ofEntries(
            Map.entry("allies", "Aliado"),
            Map.entry("ally", "Aliado"),
            Map.entry("members", "Afiliado"),
            Map.entry("member", "Afiliado"),
            Map.entry("payments", "Pago"),
            Map.entry("payment", "Pago"),
            Map.entry("plans", "Plan"),
            Map.entry("plan", "Plan"),
            Map.entry("promoters", "Promotor"),
            Map.entry("promoter", "Promotor"),
            Map.entry("commissions", "Comisión"),
            Map.entry("commission", "Comisión"),
            Map.entry("users", "Usuario"),
            Map.entry("user", "Usuario"),
            Map.entry("roles", "Rol"),
            Map.entry("role", "Rol"),
            Map.entry("countries", "País"),
            Map.entry("country", "País"),
            Map.entry("states", "Estado"),
            Map.entry("state", "Estado"),
            Map.entry("cities", "Ciudad"),
            Map.entry("city", "Ciudad"),
            Map.entry("genders", "Género"),
            Map.entry("gender", "Género"),
            Map.entry("document_types", "Tipo de Documento"),
            Map.entry("document-types", "Tipo de Documento"),
            Map.entry("marital_statuses", "Estado Civil"),
            Map.entry("marital-statuses", "Estado Civil"),
            Map.entry("occupations", "Ocupación"),
            Map.entry("occupation", "Ocupación"),
            Map.entry("medical_specialties", "Especialidad Médica"),
            Map.entry("medical-specialties", "Especialidad Médica"),
            Map.entry("service_categories", "Categoría de Servicio"),
            Map.entry("service-categories", "Categoría de Servicio"),
            Map.entry("ally_types", "Tipo de Aliado"),
            Map.entry("ally-types", "Tipo de Aliado"),
            Map.entry("promoter_types", "Tipo de Promotor"),
            Map.entry("promoter-types", "Tipo de Promotor"),
            Map.entry("scheduled_jobs", "Tarea Programada"),
            Map.entry("scheduled-jobs", "Tarea Programada"),
            Map.entry("beneficiaries", "Beneficiario"),
            Map.entry("beneficiary", "Beneficiario"),
            Map.entry("memberships", "Membresía"),
            Map.entry("membership", "Membresía")
    );

    private static final Map<String, String> PLURAL_ENTITY_NAMES = Map.ofEntries(
            Map.entry("allies", "Aliados"),
            Map.entry("ally", "Aliados"),
            Map.entry("members", "Afiliados"),
            Map.entry("member", "Afiliados"),
            Map.entry("payments", "Pagos"),
            Map.entry("payment", "Pagos"),
            Map.entry("plans", "Planes"),
            Map.entry("plan", "Planes"),
            Map.entry("promoters", "Promotores"),
            Map.entry("promoter", "Promotores"),
            Map.entry("commissions", "Comisiones"),
            Map.entry("commission", "Comisiones"),
            Map.entry("users", "Usuarios"),
            Map.entry("user", "Usuarios"),
            Map.entry("roles", "Roles"),
            Map.entry("role", "Roles"),
            Map.entry("countries", "Países"),
            Map.entry("country", "Países"),
            Map.entry("states", "Estados"),
            Map.entry("state", "Estados"),
            Map.entry("cities", "Ciudades"),
            Map.entry("city", "Ciudades"),
            Map.entry("genders", "Géneros"),
            Map.entry("gender", "Géneros"),
            Map.entry("document_types", "Tipos de Documento"),
            Map.entry("document-types", "Tipos de Documento"),
            Map.entry("marital_statuses", "Estados Civiles"),
            Map.entry("marital-statuses", "Estados Civiles"),
            Map.entry("occupations", "Ocupaciones"),
            Map.entry("occupation", "Ocupaciones"),
            Map.entry("medical_specialties", "Especialidades Médicas"),
            Map.entry("medical-specialties", "Especialidades Médicas"),
            Map.entry("service_categories", "Categorías de Servicio"),
            Map.entry("service-categories", "Categorías de Servicio"),
            Map.entry("ally_types", "Tipos de Aliado"),
            Map.entry("ally-types", "Tipos de Aliado"),
            Map.entry("promoter_types", "Tipos de Promotor"),
            Map.entry("promoter-types", "Tipos de Promotor"),
            Map.entry("scheduled_jobs", "Tareas Programadas"),
            Map.entry("scheduled-jobs", "Tareas Programadas"),
            Map.entry("beneficiaries", "Beneficiarios"),
            Map.entry("beneficiary", "Beneficiarios"),
            Map.entry("memberships", "Membresías"),
            Map.entry("membership", "Membresías")
    );

    private final GenericRecordReportService recordReportService;
    private final GenericRecordResolverService recordResolverService;

    public GenericDocumentController(
            GenericRecordReportService recordReportService,
            GenericRecordResolverService recordResolverService
    ) {
        this.recordReportService = recordReportService;
        this.recordResolverService = recordResolverService;
    }

    public record GenericReportRequest(
            String title,
            String subtitle,
            String identifier,
            String format,
            Map<String, Object> data
    ) {}

    @PostMapping("/generic")
    @Operation(summary = "Genera un reporte o ficha genérica en PDF o XLSX para cualquier payload de registro")
    public ResponseEntity<byte[]> generateGenericDocument(@RequestBody GenericReportRequest request) {
        JasperFormat selectedFormat = "XLSX".equalsIgnoreCase(request.format()) ? JasperFormat.XLSX : JasperFormat.PDF;

        String safeIdentifier = (request.identifier() != null && UUID_PATTERN.matcher(request.identifier()).matches())
                ? null
                : request.identifier();

        RenderedDocument rendered = recordReportService.generateGenericRecordDocument(
                request.data(),
                request.title(),
                request.subtitle(),
                safeIdentifier,
                "Usuario Sistema",
                selectedFormat
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    @GetMapping("/records/{entityOrTable}/{identifier}")
    @Operation(summary = "Genera un reporte o ficha genérica buscando el registro por tabla/entidad e identificador (UUID o ID)")
    public ResponseEntity<byte[]> generateDocumentByRecord(
            @PathVariable String entityOrTable,
            @PathVariable String identifier,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        String generatedBy = actor != null && actor.getUsername() != null ? actor.getUsername() : "Usuario Sistema";
        Object record = recordResolverService.findRecordByTableAndId(entityOrTable, identifier);
        JasperFormat selectedFormat = "XLSX".equalsIgnoreCase(format) ? JasperFormat.XLSX : JasperFormat.PDF;

        String documentTitle = (title != null && !title.isBlank()) ? title : "Ficha de " + formatEntitySingularTitle(entityOrTable);
        String businessIdentifier = resolveBusinessIdentifier(record, identifier);

        RenderedDocument rendered = recordReportService.generateGenericRecordDocument(
                record,
                documentTitle,
                subtitle,
                businessIdentifier,
                generatedBy,
                selectedFormat
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    @GetMapping("/tables/{targetTable}")
    @Operation(summary = "Genera un reporte de listado de registros para una tabla específica en PDF o XLSX")
    public ResponseEntity<byte[]> generateTableDocument(
            @PathVariable String targetTable,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(defaultValue = "500") int limit,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        java.util.UUID actorUuid = actor != null ? actor.getUuid() : null;
        String generatedBy = actor != null && actor.getUsername() != null ? actor.getUsername() : "Usuario Sistema";

        java.util.List<?> records = recordResolverService.findRecordsByTable(targetTable, limit, actorUuid);
        JasperFormat selectedFormat = "XLSX".equalsIgnoreCase(format) ? JasperFormat.XLSX : JasperFormat.PDF;

        String documentTitle = (title != null && !title.isBlank()) ? title : "Listado de " + formatEntityPluralTitle(targetTable);

        RenderedDocument rendered = recordReportService.generateGenericTableDocument(
                records,
                documentTitle,
                subtitle,
                generatedBy,
                selectedFormat
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    private String formatEntitySingularTitle(String entityOrTable) {
        if (entityOrTable == null || entityOrTable.isBlank()) return "Registro";
        String normalized = entityOrTable.trim().toLowerCase().replace("-", "_");
        if (SINGULAR_ENTITY_NAMES.containsKey(normalized)) {
            return SINGULAR_ENTITY_NAMES.get(normalized);
        }
        String clean = entityOrTable.replace("_", " ").replace("-", " ");
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private String formatEntityPluralTitle(String entityOrTable) {
        if (entityOrTable == null || entityOrTable.isBlank()) return "Registros";
        String normalized = entityOrTable.trim().toLowerCase().replace("-", "_");
        if (PLURAL_ENTITY_NAMES.containsKey(normalized)) {
            return PLURAL_ENTITY_NAMES.get(normalized);
        }
        String clean = entityOrTable.replace("_", " ").replace("-", " ");
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private String resolveBusinessIdentifier(Object record, String rawIdentifier) {
        if (record != null) {
            Class<?> clazz = record.getClass();
            // 1. If it has Tax ID / Tax document (e.g. Ally)
            try {
                Method getDocType = clazz.getMethod("getTaxDocumentType");
                Method getDocNum = clazz.getMethod("getTaxDocumentNumber");
                Object type = getDocType.invoke(record);
                Object num = getDocNum.invoke(record);
                if (num != null && !num.toString().isBlank()) {
                    return (type != null ? type.toString() + "-" : "") + num.toString();
                }
            } catch (Exception ignored) {}

            // 2. If it has Identity Document / Cedula (e.g. Member)
            try {
                Method getDocType = clazz.getMethod("getDocumentType");
                Method getDocNum = clazz.getMethod("getDocumentNumber");
                Object type = getDocType.invoke(record);
                Object num = getDocNum.invoke(record);
                if (num != null && !num.toString().isBlank()) {
                    return (type != null ? type.toString() + "-" : "") + num.toString();
                }
            } catch (Exception ignored) {}

            // 3. If it has business Code (e.g. Plan, Catalog, Scheduled Job)
            try {
                Method getCode = clazz.getMethod("getCode");
                Object code = getCode.invoke(record);
                if (code != null && !code.toString().isBlank()) {
                    return code.toString();
                }
            } catch (Exception ignored) {}

            // 4. If it has Referral code (e.g. Promoter)
            try {
                Method getRef = clazz.getMethod("getReferralCode");
                Object ref = getRef.invoke(record);
                if (ref != null && !ref.toString().isBlank()) {
                    return ref.toString();
                }
            } catch (Exception ignored) {}

            // 5. If it has Reference number (e.g. Payment)
            try {
                Method getRefNum = clazz.getMethod("getReferenceNumber");
                Object ref = getRefNum.invoke(record);
                if (ref != null && !ref.toString().isBlank()) {
                    return ref.toString();
                }
            } catch (Exception ignored) {}
        }

        // If received identifier is a raw UUID, omit it to avoid exposing technical UUIDs
        if (rawIdentifier != null && UUID_PATTERN.matcher(rawIdentifier).matches()) {
            return null;
        }

        return rawIdentifier;
    }
}
