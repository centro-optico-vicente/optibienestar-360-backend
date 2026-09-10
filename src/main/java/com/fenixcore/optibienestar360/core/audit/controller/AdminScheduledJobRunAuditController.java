package com.fenixcore.optibienestar360.core.audit.controller;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobRunDto;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.Outcome;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.TriggerSource;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin read side of the scheduled-job execution trail, across every job —
 * the "Ejecuciones programadas" screen in the Seguridad menu, sibling to
 * {@code AdminLoginAuditController}/{@code AdminDataChangeAuditController}/
 * {@code AdminReportAuditController}. {@code AdminScheduledJobController}'s
 * own {@code GET /{uuid}/runs} stays scoped to one job's detail page; this is
 * the unscoped, cross-job equivalent (kept in a separate controller — nesting
 * it under {@code /v1/admin/scheduled-jobs} would collide with that
 * controller's {@code {uuid}} path variable).
 */
@RestController
@RequestMapping("/v1/admin/audit/job-runs")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Consulta de la bitácora de ejecuciones de tareas programadas")
public class AdminScheduledJobRunAuditController {

    private final ScheduledJobsService jobsService;

    @GetMapping
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    @Operation(summary = "Lista el historial de ejecuciones de todas las tareas programadas, con filtros por tarea, resultado, origen y fecha")
    public ResponseEntity<AppliedSortPage<ScheduledJobRunDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID jobUuid,
            @RequestParam(required = false) Outcome outcome,
            @RequestParam(required = false) TriggerSource triggeredBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Page<ScheduledJobRunDto> page = jobsService.listAllRuns(pageable, jobUuid, outcome, triggeredBy, from, to);
        return ResponseEntity.ok(new AppliedSortPage<>(page, jobsService.effectiveSortRuns(pageable)));
    }

}
