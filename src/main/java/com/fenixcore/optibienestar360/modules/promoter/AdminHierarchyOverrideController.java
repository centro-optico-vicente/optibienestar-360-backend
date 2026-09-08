package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideReRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin month-close operations over {@code promoter_hierarchy_overrides}
 * (V102, hub plan ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2,
 * PR3). Reuses {@code COMMISSION_RE_RATE} (V51) rather than a new permission —
 * same actor (finance/admin closing the month), same "recompute pending rows
 * for a closed period" action, just a different table.
 */
@RestController
@RequestMapping("/v1/admin/hierarchy-overrides")
@RequiredArgsConstructor
public class AdminHierarchyOverrideController {

    private final HierarchyOverrideReRatingService reRatingService;

    /**
     * Month-close retroactive re-rating: resyncs any override whose basis
     * went stale because its source was re-rated after the cascade first
     * ran, then bumps every PENDING override to the highest team-volume band
     * its beneficiary's team reached. Pass {@code dryRun=true} to preview the
     * deltas before committing.
     */
    @PostMapping("/re-rate")
    @PreAuthorize("hasAuthority('COMMISSION_RE_RATE')")
    public ResponseEntity<HierarchyOverrideReRatingResponse> reRate(
            @Valid @RequestBody HierarchyOverrideReRatingRequest request) {
        return ResponseEntity.ok(reRatingService.execute(request));
    }
}
