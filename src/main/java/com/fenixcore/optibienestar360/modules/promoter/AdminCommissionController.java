package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionPayoutService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionReRatingService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only admin surface over the commissions ledger. Mutations
 * (mark-as-paid / void / dispute) live in their own bullets once the
 * payout flow lands.
 *
 * <p>Default sort {@code earnedAt DESC} matches the V26 composite index
 * {@code (member_id, earned_at DESC)} for the per-member view; the
 * canonical liquidation query {@code ?filter=promoter.uuid==X;status==PENDING}
 * is backed by {@code idx_commissions_promoter_status_period}.</p>
 */
@RestController
@RequestMapping("/v1/admin/commissions")
@RequiredArgsConstructor
public class AdminCommissionController {

    private final CommissionsService commissionsService;
    private final CommissionPayoutService commissionPayoutService;
    private final CommissionReRatingService commissionReRatingService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CommissionDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<CommissionDto> page = commissionsService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, commissionsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<CommissionDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(commissionsService.get(uuid));
    }

    /**
     * Closes a period: marks every PENDING commission inside the date
     * range as PAID, generates a CSV breakdown per promoter, and emails
     * each promoter the summary + CSV. Pass {@code dryRun=true} to
     * preview totals without committing.
     */
    @PostMapping("/payout")
    @PreAuthorize("hasAuthority('COMMISSION_PAYOUT')")
    public ResponseEntity<CommissionPayoutResponse> payout(
            @Valid @RequestBody CommissionPayoutRequest request) {
        return ResponseEntity.ok(commissionPayoutService.execute(request));
    }

    /**
     * Month-close retroactive re-rating (vertical-8 Ítem A): bumps every
     * PENDING INSCRIPTION commission in the period to the highest volume
     * band the promoter's monthly count reached. Pass {@code dryRun=true}
     * to preview the deltas before committing.
     */
    @PostMapping("/re-rate")
    @PreAuthorize("hasAuthority('COMMISSION_RE_RATE')")
    public ResponseEntity<CommissionReRatingResponse> reRate(
            @Valid @RequestBody CommissionReRatingRequest request) {
        return ResponseEntity.ok(commissionReRatingService.execute(request));
    }
}
