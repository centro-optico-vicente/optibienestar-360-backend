package com.fenixcore.optibienestar360.core.audit.controller;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditQueryService;
import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin read side of the data-change audit trail (spec 16-audit.md §Endpoints
 * admin). The main endpoint covers both use cases: the full history of a
 * single record ({@code entityKey} + {@code entityUuid}) and cross-entity
 * filtering by actor / action / date range, plus an RSQL {@code filter} for
 * anything else. Insert-only and system-populated — there is no
 * create/update/delete here.
 *
 * <p>{@code list}'s response embeds {@code firstChange} directly (same
 * request, no round-trip) — mirrors {@code ListEntityLogsResponse
 * .first_entity_log} from adempiere-grpc-server's {@code logs.proto}: the
 * record's creation row travels alongside the page, so the client can always
 * show "created on ..." regardless of which page of a long history (500+
 * rows) it's currently viewing. Only populated when the query is scoped to
 * one record ({@code entityKey} + {@code entityUuid}); {@code null}
 * otherwise. {@code /first-change} stays available as a lightweight
 * standalone lookup (e.g. to show "created on ..." before the paginated view
 * is even opened) — same shape as {@code AdminSubsidyController}'s
 * {@code /{uuid}/log}.</p>
 */
@RestController
@RequestMapping("/v1/admin/audit/data-changes")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Consulta de la bitácora de cambios de datos")
public class AdminDataChangeAuditController {

    private final DataChangeAuditQueryService dataChangeAuditQueryService;

    @GetMapping
	@PreAuthorize("@auditAccess.canView(#entityKey)")
    @Operation(summary = "Lista la bitácora de cambios de datos, con filtros por entidad, actor, acción y fecha")
    public ResponseEntity<DataChangeAuditLogPageDto> list(
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String entityKey,
            @RequestParam(required = false) UUID entityUuid,
            @RequestParam(required = false) UUID actorUuid,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(dataChangeAuditQueryService.list(
                pageable, entityKey, entityUuid, actorUuid, action, from, to, filter));
    }

    @GetMapping("/first-change")
	@PreAuthorize("@auditAccess.canView(#entityKey)")
    @Operation(summary = "Fila más antigua (típicamente el CREATE) de una entidad — independiente de la paginación")
    public ResponseEntity<DataChangeAuditLogDto> firstChange(
            @RequestParam String entityKey,
            @RequestParam UUID entityUuid) {
        DataChangeAuditLogDto dto = dataChangeAuditQueryService.firstChange(entityKey, entityUuid)
                .orElseThrow(() -> new NoSuchElementException("audit.first_change.not_found"));
        return ResponseEntity.ok(dto);
    }

}
