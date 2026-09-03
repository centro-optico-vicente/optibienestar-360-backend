package com.fenixcore.optibienestar360.modules.scheduling;

import com.fenixcore.optibienestar360.modules.scheduling.dto.ManualRunResponse;
import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobCreateRequest;
import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobDto;
import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobRunDto;
import com.fenixcore.optibienestar360.modules.scheduling.dto.ScheduledJobUpdateRequest;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.Outcome;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobExecutionService;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ManualRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobsService;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin surface for the scheduled-jobs framework. Mirrors the shape of
 * {@code AdminPlanController} (small catalog → high default page size,
 * sorted by natural key) plus the manual-trigger + runs-history endpoints
 * that are specific to this domain.
 *
 * <p>{@code POST /run-now} returns either HTTP 200 (sync completion) or
 * HTTP 202 (async fallback after the per-job {@code max_sync_seconds}
 * timeout). Frontend distinguishes by status code and either renders the
 * inline result or starts polling {@link ManualRunResponse#statusUrl()}.</p>
 */
@RestController
@RequestMapping("/v1/admin/scheduled-jobs")
@RequiredArgsConstructor
public class AdminScheduledJobController {

    private final ScheduledJobsService jobsService;
    private final JobExecutionService executionService;

    // ─── Job CRUD ───────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<ScheduledJobDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<ScheduledJobDto> page = jobsService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, jobsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    public ResponseEntity<ScheduledJobDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(jobsService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('JOB_CREATE')")
    public ResponseEntity<ScheduledJobDto> create(@Valid @RequestBody ScheduledJobCreateRequest request) {
        ScheduledJobDto created = jobsService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('JOB_UPDATE')")
    public ResponseEntity<ScheduledJobDto> update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody ScheduledJobUpdateRequest request) {
        return ResponseEntity.ok(jobsService.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(jobsService.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('JOB_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        jobsService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    // ─── Manual trigger (hybrid sync/async) ────────────────────────────────

    @PostMapping("/{uuid}/run-now")
    @PreAuthorize("hasAuthority('JOB_RUN_NOW')")
    public ResponseEntity<ManualRunResponse> runNow(@PathVariable UUID uuid,
                                                    @AuthenticationPrincipal CustomUserDetails actor) {
        ManualRunResult outcome = executionService.runNow(uuid, actor.getUuid());
        ManualRunResponse body = toResponse(uuid, outcome);
        return outcome.synced()
                ? ResponseEntity.ok(body)
                : ResponseEntity.accepted().body(body);
    }

    // ─── Run history + single (used by frontend polling for async runs) ───

    @GetMapping("/{uuid}/runs")
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    public ResponseEntity<Page<ScheduledJobRunDto>> listRuns(
            @PathVariable UUID uuid,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(jobsService.listRuns(uuid, pageable));
    }

    @GetMapping("/{uuid}/runs/{runUuid}")
    @PreAuthorize("hasAuthority('JOB_VIEW_ALL')")
    public ResponseEntity<ScheduledJobRunDto> getRun(@PathVariable UUID uuid,
                                                     @PathVariable UUID runUuid) {
        return ResponseEntity.ok(jobsService.getRun(uuid, runUuid));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static ManualRunResponse toResponse(UUID jobUuid, ManualRunResult outcome) {
        if (!outcome.synced()) {
            String statusUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/v1/admin/scheduled-jobs/{uuid}/runs/{runUuid}")
                    .buildAndExpand(jobUuid, outcome.runUuid())
                    .toUriString();
            return new ManualRunResponse(
                    outcome.runUuid(),
                    Outcome.RUNNING.name(),
                    null,
                    null,
                    statusUrl);
        }
        JobRunResult result = outcome.result();
        String terminal = result.success() ? Outcome.SUCCESS.name() : Outcome.FAILED.name();
        return new ManualRunResponse(
                outcome.runUuid(),
                terminal,
                result.summary(),
                result.errorMessage(),
                null);
    }
}
