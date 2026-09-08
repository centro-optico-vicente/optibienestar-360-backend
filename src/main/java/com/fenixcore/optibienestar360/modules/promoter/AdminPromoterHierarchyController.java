package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.AssignSupervisorRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.ChangeRankRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterHierarchyNodeDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterSupervisorAssignmentDto;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterHierarchyService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin reassignment of a promoter's supervisor (V101, hub plan §1). Separate
 * controller from {@code AdminPromoterController} so the distinct permission
 * ({@code PROMOTER_ASSIGN_SUPERVISOR}, gates only this action) and the audit
 * semantics stay visibly segregated from plain promoter CRUD — mirrors
 * {@code AdminMemberPromoterController} for the member↔promoter link (V35).
 */
@RestController
@RequestMapping("/v1/admin/promoters")
@RequiredArgsConstructor
public class AdminPromoterHierarchyController {

    private final PromoterHierarchyService hierarchyService;

    /**
     * The full org chart, nested — feeds a Nuxt tree/org-chart view directly
     * (see {@link PromoterHierarchyNodeDto} on how the client edits it: move
     * / detach both go through {@code assign-supervisor} below, not a
     * separate endpoint). Gated by the existing {@code PROMOTER_VIEW_ALL}
     * (read-only) — no new permission needed for a view built entirely from
     * data that permission already exposes.
     */
    @GetMapping("/hierarchy-tree")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<List<PromoterHierarchyNodeDto>> hierarchyTree() {
        return ResponseEntity.ok(hierarchyService.buildTree(Instant.now()));
    }

    @PostMapping("/{promoterUuid}/assign-supervisor")
    @PreAuthorize("hasAuthority('PROMOTER_ASSIGN_SUPERVISOR')")
    public ResponseEntity<PromoterSupervisorAssignmentDto> assignSupervisor(
            @PathVariable UUID promoterUuid,
            @Valid @RequestBody AssignSupervisorRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(hierarchyService.assignSupervisor(
                promoterUuid, request.supervisorUuid(), request.reason(), actor.getUuid()));
    }

    @GetMapping("/{promoterUuid}/supervisor-history")
    @PreAuthorize("hasAuthority('PROMOTER_ASSIGN_SUPERVISOR')")
    public ResponseEntity<List<PromoterSupervisorAssignmentDto>> supervisorHistory(
            @PathVariable UUID promoterUuid) {
        return ResponseEntity.ok(hierarchyService.history(promoterUuid));
    }

    /**
     * Feeds the second step of the "change rank" flow: the admin picks the
     * target rank first, then this lists who is actually eligible to
     * supervise a promoter at that rank (every active promoter whose own
     * rank is strictly above it) — never the raw rank catalog, so the UI
     * can't offer a supervisor the backend would reject anyway. Empty when
     * {@code rankUuid} is the top rank (no supervisor applies) or when
     * nobody currently holds a higher rank yet.
     */
    @GetMapping("/eligible-supervisors")
    @PreAuthorize("hasAuthority('PROMOTER_CHANGE_RANK')")
    public ResponseEntity<List<OptionDto>> eligibleSupervisors(
            @RequestParam UUID rankUuid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok(hierarchyService.eligibleSupervisorOptions(rankUuid, q, limit));
    }

    /**
     * Ascends or demotes {@code promoterUuid} to {@code newRankUuid},
     * reassigning their supervisor in the same call (V104, hub plan §1
     * follow-up — resolves pregunta 8 by construction: a promoter is never
     * left with an invalid supervisor for their new rank, since the client
     * must supply one already validated against {@link #eligibleSupervisors}
     * unless the new rank is the top of the chain).
     */
    @PostMapping("/{promoterUuid}/change-rank")
    @PreAuthorize("hasAuthority('PROMOTER_CHANGE_RANK')")
    public ResponseEntity<PromoterDto> changeRank(
            @PathVariable UUID promoterUuid,
            @Valid @RequestBody ChangeRankRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(hierarchyService.changeRank(
                promoterUuid, request.newRankUuid(), request.newSupervisorUuid(), request.reason(), actor.getUuid()));
    }
}
