package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductApproveRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductReasonRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.service.AllyProductReviewService;
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
 * Admin-side review workflow for ally offers (v2 PDF #6, hub ADR 0026). The
 * review queue + approve / reject / remove transitions, all gated by
 * {@code ALLY_PRODUCT_APPROVE} (the log read is a general admin read, gated by
 * {@code ALLY_PRODUCT_VIEW_ALL}). Distinct from the per-ally CRUD at
 * {@code /v1/admin/allies/{uuid}/products} — this surface is cross-ally and
 * organized around the workflow, not the parent ally.
 */
@RestController
// /v1/admin/ally-services is a deprecated alias kept while the frontend moves to ally-products.
@RequestMapping({"/v1/admin/ally-products", "/v1/admin/ally-services"})
@RequiredArgsConstructor
public class AdminAllyProductReviewController {

    private final AllyProductReviewService reviewService;

    /** Review queue: PROPOSED + IN_REVIEW, oldest first (FIFO), optional ally/category/free-text filters. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_APPROVE')")
    public ResponseEntity<Page<AllyProductDto>> pending(
            @RequestParam(required = false) UUID allyUuid,
            @RequestParam(required = false) UUID productCategoryUuid,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.pendingQueue(allyUuid, productCategoryUuid, q, pageable));
    }

    @PostMapping("/{uuid}/approve")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_APPROVE')")
    public ResponseEntity<AllyProductDto> approve(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) AllyProductApproveRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        String comment = request != null ? request.comment() : null;
        return ResponseEntity.ok(reviewService.approve(uuid, actor.getUuid(), comment));
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_APPROVE')")
    public ResponseEntity<AllyProductDto> reject(
            @PathVariable UUID uuid,
            @Valid @RequestBody AllyProductReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.reject(uuid, actor.getUuid(), request.reason()));
    }

    @PostMapping("/{uuid}/remove")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_APPROVE')")
    public ResponseEntity<AllyProductDto> remove(
            @PathVariable UUID uuid,
            @Valid @RequestBody AllyProductReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.adminRemove(uuid, actor.getUuid(), request.reason()));
    }

    @GetMapping("/{uuid}/log")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_VIEW_ALL')")
    public ResponseEntity<List<AllyProductReviewLogDto>> log(@PathVariable UUID uuid) {
        return ResponseEntity.ok(reviewService.adminLog(uuid));
    }
}
