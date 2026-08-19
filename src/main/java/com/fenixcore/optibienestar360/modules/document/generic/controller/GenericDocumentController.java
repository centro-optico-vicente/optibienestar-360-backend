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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.Locale;

@RestController
@RequestMapping("/v1/documents")
@Tag(name = "Documentos Genéricos", description = "Generación de fichas y reportes genéricos para cualquier registro del sistema")
public class GenericDocumentController {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GenericRecordReportService recordReportService;
    private final GenericRecordResolverService recordResolverService;
    private final MessageSource messageSource;

    public GenericDocumentController(
            GenericRecordReportService recordReportService,
            GenericRecordResolverService recordResolverService,
            MessageSource messageSource
    ) {
        this.recordReportService = recordReportService;
        this.recordResolverService = recordResolverService;
        this.messageSource = messageSource;
    }

    public record GenericReportRequest(
            String title,
            String subtitle,
            String identifier,
            String format,
            Map<String, Object> data
    ) {}

    @PostMapping("/generic")
    @PreAuthorize("hasAnyAuthority('REPORT_PRINT', 'REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
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
    @PreAuthorize("hasAnyAuthority('REPORT_PRINT', 'REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
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
    @PreAuthorize("hasAnyAuthority('REPORT_PRINT', 'REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
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
        Locale locale = LocaleContextHolder.getLocale();

        if (messageSource != null) {
            try {
                String msg = messageSource.getMessage("entity.singular." + normalized, null, locale);
                if (msg != null && !msg.isBlank()) return msg;
            } catch (Exception ignored) {}
        }

        String clean = normalized.replace("_", " ");
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private String formatEntityPluralTitle(String entityOrTable) {
        if (entityOrTable == null || entityOrTable.isBlank()) return "Registros";
        String normalized = entityOrTable.trim().toLowerCase().replace("-", "_");
        Locale locale = LocaleContextHolder.getLocale();

        if (messageSource != null) {
            try {
                String msg = messageSource.getMessage("entity.plural." + normalized, null, locale);
                if (msg != null && !msg.isBlank()) return msg;
            } catch (Exception ignored) {}
        }

        String clean = normalized.replace("_", " ");
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
