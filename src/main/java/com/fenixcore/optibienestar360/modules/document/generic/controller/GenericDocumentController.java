package com.fenixcore.optibienestar360.modules.document.generic.controller;

import com.fenixcore.optibienestar360.core.audit.ReportAuditService;
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
import java.util.UUID;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.sql.DataSource;

import com.fenixcore.optibienestar360.modules.document.jasper.JasperReportService;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.Locale;

@RestController
@RequestMapping("/v1/documents")
@Tag(name = "Documentos Genéricos", description = "Generación de fichas y reportes genéricos para cualquier registro del sistema")
public class GenericDocumentController {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    // Maps DB/route table names to the canonical singular audit entity key used by AuditEntityAccess.
    private static final Map<String, String> TABLE_TO_AUDIT_ENTITY_KEY = Map.of(
            "allies", "ally"
    );

    private final GenericRecordReportService recordReportService;
    private final GenericRecordResolverService recordResolverService;
    private final MessageSource messageSource;
    private final ReportAuditService reportAuditService;
    private final JasperReportService jasperReportService;
    private final DataSource dataSource;
    private final PromoterRepository promoterRepository;
    private final PlanRepository planRepository;
    private final SystemConfigService systemConfigService;

    public GenericDocumentController(
            GenericRecordReportService recordReportService,
            GenericRecordResolverService recordResolverService,
            MessageSource messageSource,
            ReportAuditService reportAuditService,
            JasperReportService jasperReportService,
            DataSource dataSource,
            PromoterRepository promoterRepository,
            PlanRepository planRepository,
            SystemConfigService systemConfigService
    ) {
        this.recordReportService = recordReportService;
        this.recordResolverService = recordResolverService;
        this.messageSource = messageSource;
        this.reportAuditService = reportAuditService;
        this.jasperReportService = jasperReportService;
        this.dataSource = dataSource;
        this.promoterRepository = promoterRepository;
        this.planRepository = planRepository;
        this.systemConfigService = systemConfigService;
    }

    public record GenericReportRequest(
            String title,
            String subtitle,
            String identifier,
            String format,
            Map<String, Object> data
    ) {}

    @PostMapping("/generic")
    @PreAuthorize("hasAnyAuthority('REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
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

        reportAuditService.recordGeneration("GENERIC", null, null, safeIdentifier,
                selectedFormat.name(), request.data(), rendered.content(), rendered.fileName(), rendered.contentType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    @GetMapping("/records/{entityOrTable}/{identifier}")
    @PreAuthorize("hasAnyAuthority('REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
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

        UUID entityUuid = UUID_PATTERN.matcher(identifier).matches() ? UUID.fromString(identifier) : null;
        reportAuditService.recordGeneration("RECORD", toAuditEntityKey(entityOrTable), entityUuid,
                businessIdentifier, selectedFormat.name(), null, rendered.content(), rendered.fileName(), rendered.contentType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    @GetMapping("/tables/{targetTable}")
    @PreAuthorize("hasAnyAuthority('REPORT_REPORT_GENERATE', 'USER_REPORT_GENERATE', 'MEMBER_REPORT_GENERATE', 'ALLY_REPORT_GENERATE', 'PLAN_REPORT_GENERATE', 'MEMBERSHIP_REPORT_GENERATE', 'PAYMENT_REPORT_GENERATE', 'PROMOTER_REPORT_GENERATE', 'COMMISSION_REPORT_GENERATE', 'REFERRAL_REPORT_GENERATE')")
    @Operation(summary = "Genera un reporte de listado de registros para una tabla específica en PDF o XLSX")
    public ResponseEntity<byte[]> generateTableDocument(
            @PathVariable String targetTable,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal CustomUserDetails actor
    ) {
        java.util.UUID actorUuid = actor != null ? actor.getUuid() : null;
        String generatedBy = actor != null && actor.getUsername() != null ? actor.getUsername() : "Usuario Sistema";

        java.util.List<?> records = recordResolverService.findRecordsByTable(targetTable, limit, actorUuid, q, includeInactive);
        JasperFormat selectedFormat = "XLSX".equalsIgnoreCase(format) ? JasperFormat.XLSX : JasperFormat.PDF;

        String documentTitle = (title != null && !title.isBlank()) ? title : "Listado de " + formatEntityPluralTitle(targetTable);

        RenderedDocument rendered = recordReportService.generateGenericTableDocument(
                records,
                documentTitle,
                subtitle,
                generatedBy,
                selectedFormat
        );

        reportAuditService.recordGeneration("TABLE", toAuditEntityKey(targetTable), null,
                null, selectedFormat.name(), Map.of("limit", limit, "q", q == null ? "" : q, "includeInactive", includeInactive),
                rendered.content(), rendered.fileName(), rendered.contentType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .contentType(MediaType.parseMediaType(rendered.contentType()))
                .body(rendered.content());
    }

    @GetMapping("/jasper/{reportName}")
    @PreAuthorize("hasAuthority('REPORT_REPORT_GENERATE') or (#reportName.matches('(?i)comision(es)?|commission(s)?|pagos?-comision(es)?|payouts?|commission-payouts?') and (hasAuthority('COMMISSION_REPORT_GENERATE') or hasAuthority('COMMISSION_VIEW_ALL') or hasAuthority('COMMISSION_VIEW_OWN'))) or (#reportName.matches('(?i)pagos?(-afiliados)?|payments?') and (hasAuthority('PAYMENT_REPORT_GENERATE') or hasAuthority('PAYMENT_VIEW_ALL') or hasAuthority('PAYMENT_VIEW_OWN')))")
    @Operation(summary = "Genera un reporte Jasper profesional (comisiones, pagos de comisiones o pagos de afiliados) en PDF o XLSX con filtros")
    public ResponseEntity<byte[]> generateJasperReport(
            @PathVariable String reportName,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String promoter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String appliesTo,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String payoutReference,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String targetCurrency,
            @RequestParam(required = false) String conversionDate
    ) {
        String normalizedReport = reportName.trim().toLowerCase();
        String templatePath;
        String baseFilename;
        String entityKey;

        if ("comisiones".equals(normalizedReport) || "commissions".equals(normalizedReport)) {
            templatePath = "reports/reporte-comisiones.jrxml";
            baseFilename = "reporte-comisiones";
            entityKey = "commission";
        } else if ("pagos-comisiones".equals(normalizedReport) || "commission-payouts".equals(normalizedReport) || "payouts".equals(normalizedReport)) {
            templatePath = "reports/reporte-pagos-comisiones.jrxml";
            baseFilename = "reporte-pagos-comisiones";
            entityKey = "commission_payout";
        } else if ("pagos".equals(normalizedReport) || "payments".equals(normalizedReport) || "pagos-afiliados".equals(normalizedReport)) {
            templatePath = "reports/reporte-pagos.jrxml";
            baseFilename = "reporte-pagos-afiliados";
            entityKey = "payment";
        } else {
            throw new IllegalArgumentException("Reporte Jasper desconocido: " + reportName + ". Disponibles: comisiones, pagos-comisiones, pagos.");
        }

        JasperFormat selectedFormat = "XLSX".equalsIgnoreCase(format) ? JasperFormat.XLSX : JasperFormat.PDF;

        // Resolve promoter ID if UUID or numeric ID is provided
        Long promoterId = null;
        if (promoter != null && !promoter.isBlank()) {
            if (UUID_PATTERN.matcher(promoter.trim()).matches()) {
                promoterId = promoterRepository.findByUuid(UUID.fromString(promoter.trim()))
                        .map(Promoter::getId)
                        .orElse(null);
            } else {
                try {
                    promoterId = Long.parseLong(promoter.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Resolve plan ID if UUID or numeric ID is provided
        Long planId = null;
        if (plan != null && !plan.isBlank()) {
            if (UUID_PATTERN.matcher(plan.trim()).matches()) {
                planId = planRepository.findByUuid(UUID.fromString(plan.trim()))
                        .map(Plan::getId)
                        .orElse(null);
            } else {
                try {
                    planId = Long.parseLong(plan.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        String resolvedTargetCurrency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.trim().toUpperCase()
                : "USD";
        String resolvedConversionDate = (conversionDate != null && !conversionDate.isBlank())
                ? conversionDate.trim()
                : LocalDate.now().toString();

        Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("P_START_DATE", (startDate != null && !startDate.isBlank()) ? startDate.trim() : null);
        parameters.put("P_END_DATE", (endDate != null && !endDate.isBlank()) ? endDate.trim() : null);
        parameters.put("P_COMPANY_NAME", (companyName != null && !companyName.isBlank()) ? companyName.trim() : "");

        String reportFooter = systemConfigService != null ? systemConfigService.getReportFooter() : null;
        if (reportFooter == null || reportFooter.isBlank()) {
            reportFooter = SystemConfigService.DEFAULT_REPORT_FOOTER;
        }
        parameters.put("P_REPORT_FOOTER", reportFooter != null ? reportFooter.trim() : "");

        if ("commission".equals(entityKey)) {
            checkReportAuthority(java.util.List.of("COMMISSION_REPORT_GENERATE", "COMMISSION_VIEW_ALL", "COMMISSION_VIEW_OWN"));
            parameters.put("P_PROMOTER_ID", promoterId);
            parameters.put("P_STATUS", (status != null && !status.isBlank()) ? status.trim() : null);
            parameters.put("P_APPLIES_TO", (appliesTo != null && !appliesTo.isBlank()) ? appliesTo.trim() : null);
            parameters.put("P_TARGET_CURRENCY", resolvedTargetCurrency);
            parameters.put("P_CONVERSION_DATE", resolvedConversionDate);
        } else if ("commission_payout".equals(entityKey)) {
            checkReportAuthority(java.util.List.of("COMMISSION_REPORT_GENERATE", "COMMISSION_VIEW_ALL", "COMMISSION_VIEW_OWN", "COMMISSION_PAYOUT"));
            parameters.put("P_PROMOTER_ID", promoterId);
            parameters.put("P_PAYOUT_REFERENCE", (payoutReference != null && !payoutReference.isBlank()) ? payoutReference.trim() : null);
            parameters.put("P_TARGET_CURRENCY", resolvedTargetCurrency);
        } else {
            checkReportAuthority(java.util.List.of("PAYMENT_REPORT_GENERATE", "PAYMENT_VIEW_ALL", "PAYMENT_VIEW_OWN"));
            parameters.put("P_STATUS", (status != null && !status.isBlank()) ? status.trim() : null);
            parameters.put("P_PAYMENT_METHOD", (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.trim() : null);
            parameters.put("P_PLAN_ID", planId);
            parameters.put("P_PROMOTER_ID", promoterId);
            parameters.put("P_TARGET_CURRENCY", resolvedTargetCurrency);
        }

        byte[] reportBytes;
        try (Connection connection = dataSource.getConnection()) {
            reportBytes = jasperReportService.generateReportWithConnection(templatePath, parameters, connection, selectedFormat);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar reporte Jasper (" + reportName + "): " + e.getMessage(), e);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        String filename = baseFilename + "_" + timestamp + selectedFormat.getFileExtension();

        reportAuditService.recordGeneration(
                "JASPER",
                "commission_payout".equals(entityKey) ? "commission" : entityKey,
                null,
                null,
                selectedFormat.name(),
                parameters,
                reportBytes,
                filename,
                selectedFormat.getContentType()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(selectedFormat.getContentType()))
                .body(reportBytes);
    }

    public ResponseEntity<byte[]> generateJasperReport(
            String reportName,
            String format,
            String startDate,
            String endDate,
            String promoter,
            String status,
            String appliesTo,
            String paymentMethod,
            String plan,
            String payoutReference,
            String companyName
    ) {
        return generateJasperReport(
                reportName, format, startDate, endDate, promoter, status, appliesTo,
                paymentMethod, plan, payoutReference, companyName, null, null
        );
    }

    private String toAuditEntityKey(String entityOrTable) {
        if (entityOrTable == null) return "unknown";
        String normalized = entityOrTable.trim().toLowerCase().replace("-", "_");
        if (TABLE_TO_AUDIT_ENTITY_KEY.containsKey(normalized)) {
            return TABLE_TO_AUDIT_ENTITY_KEY.get(normalized);
        }
        if (normalized.equals("allies")) return "ally";
        if (normalized.equals("cities")) return "city";
        if (normalized.equals("countries")) return "country";
        if (normalized.endsWith("ies")) return normalized.substring(0, normalized.length() - 3) + "y";
        if (normalized.endsWith("statuses")) return normalized.substring(0, normalized.length() - 2);
        if (normalized.endsWith("s") && !normalized.equals("status")) return normalized.substring(0, normalized.length() - 1);
        return normalized;
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
            try {
                String msg = messageSource.getMessage("entity.singular." + normalized, null, Locale.forLanguageTag("es"));
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
            try {
                String msg = messageSource.getMessage("entity.plural." + normalized, null, Locale.forLanguageTag("es"));
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

    private void checkReportAuthority(java.util.List<String> allowedPermissions) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        boolean hasAllowed = auth.getAuthorities().stream()
                .anyMatch(a -> allowedPermissions.contains(a.getAuthority()));
        boolean hasGlobal = auth.getAuthorities().stream()
                .anyMatch(a -> "REPORT_REPORT_GENERATE".equals(a.getAuthority()));
        if (!hasAllowed && !hasGlobal) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Acceso denegado: se requiere alguno de los permisos " + allowedPermissions + " o REPORT_REPORT_GENERATE");
        }
    }
}
