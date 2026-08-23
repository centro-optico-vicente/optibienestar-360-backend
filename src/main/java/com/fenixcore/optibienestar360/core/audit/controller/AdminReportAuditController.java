package com.fenixcore.optibienestar360.core.audit.controller;

import com.fenixcore.optibienestar360.core.audit.ReportAuditQueryService;
import com.fenixcore.optibienestar360.core.audit.dto.ReportAuditLogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Admin read side of the report-generation audit trail (spec 16-audit.md
 * §Reportes) — "who generated what report, for which record, when", plus a
 * presigned download link. Insert-only and system-populated from
 * {@code GenericDocumentController}; there is no create/update/delete here.
 */
@RestController
@RequestMapping("/v1/admin/audit/reports")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Consulta de la bitácora de generación de reportes")
public class AdminReportAuditController {

    private final ReportAuditQueryService reportAuditQueryService;

    @GetMapping
	@PreAuthorize("@auditAccess.canViewReports(#entityKey)")
    @Operation(summary = "Lista la bitácora de generación de reportes, con filtros por entidad, actor, formato y fecha")
    public ResponseEntity<Page<ReportAuditLogDto>> list(
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String entityKey,
            @RequestParam(required = false) UUID entityUuid,
            @RequestParam(required = false) UUID actorUuid,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(reportAuditQueryService.list(
                pageable, reportType, entityKey, entityUuid, actorUuid, format, from, to, filter));
    }

    @GetMapping("/{uuid}/download")
	@PreAuthorize("@auditAccess.canViewReportDownload(#uuid)")
    @Operation(summary = "URL presignada para descargar el archivo de un reporte previamente generado")
    public ResponseEntity<Map<String, String>> download(@PathVariable UUID uuid) {
        return ResponseEntity.ok(Map.of("url", reportAuditQueryService.downloadUrl(uuid)));
    }

}
