package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceApproveRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReasonRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServiceReviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-side review workflow for ally services (v2 PDF #6). The review queue +
 * approve / reject / remove transitions, all gated by
 * {@code ALLY_SERVICE_APPROVE} (the log read is a general admin read, gated by
 * {@code ALLY_VIEW_ALL}). Distinct from the per-ally CRUD at
 * {@code /v1/admin/allies/{uuid}/services} — this surface is cross-ally and
 * organized around the workflow, not the parent ally.
 */
@RestController
@RequestMapping("/v1/admin/ally-services")
@RequiredArgsConstructor
public class AdminAllyServiceReviewController {

    private final AllyServiceReviewService reviewService;

    /** Review queue: PROPOSED + IN_REVIEW, oldest first (FIFO), optional ally/category filters. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_APPROVE')")
    public ResponseEntity<Page<AllyServiceDto>> pending(
            @RequestParam(required = false) UUID allyUuid,
            @RequestParam(required = false) UUID serviceCategoryUuid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.pendingQueue(allyUuid, serviceCategoryUuid, pageable));
    }

    @PostMapping("/{uuid}/approve")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_APPROVE')")
    public ResponseEntity<AllyServiceDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) AllyServiceApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        String comment = request != null ? request.comment() : null;
        return ResponseEntity.ok(reviewService.approve(uuid, actor.getUuid(), comment));
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_APPROVE')")
    public ResponseEntity<AllyServiceDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody AllyServiceReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.reject(uuid, actor.getUuid(), request.reason()));
    }

    @PostMapping("/{uuid}/remove")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_APPROVE')")
    public ResponseEntity<AllyServiceDto> remove(
            @PathVariable UUID uuid,
            @Valid @RequestBody AllyServiceReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.adminRemove(uuid, actor.getUuid(), request.reason()));
    }

    @GetMapping("/{uuid}/log")
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<AllyServiceReviewLogDto>> log(@PathVariable UUID uuid) {
        return ResponseEntity.ok(reviewService.adminLog(uuid));
    }
}
