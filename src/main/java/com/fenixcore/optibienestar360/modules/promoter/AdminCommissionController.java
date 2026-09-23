package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.ApproveCommissionsRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalActionResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalGroupDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutBySelectionRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionVoidRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.RejectCommissionsRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionApprovalService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionPayoutService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionReRatingService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionRetroactiveTopUpService;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Admin surface over the commissions ledger. Mostly read-only; the one
 * mutation it owns directly is {@link #voidCommission} (mark-as-paid comes
 * in bulk via {@code /payout} — see {@code CommissionPayoutService} — and
 * dispute isn't built yet).
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
    private final CommissionRetroactiveTopUpService commissionRetroactiveTopUpService;
    private final CommissionApprovalService commissionApprovalService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CommissionDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID promoterTypeUuid,
            @RequestParam(required = false) UUID promoterRankUuid,
            @RequestParam(required = false) UUID campaignUuid) {
        Page<CommissionDto> page = commissionsService.list(pageable, filter, q, includeInactive,
                promoterTypeUuid, promoterRankUuid, campaignUuid);
        return ResponseEntity.ok(new AppliedSortPage<>(page, commissionsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<CommissionDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(commissionsService.get(uuid));
    }

    /**
     * Voids one PENDING commission — e.g. a promoter's non-compliance
     * discovered before the period closes — excluding it from the next
     * {@code /payout} without touching the rest of that promoter's period.
     */
    @PostMapping("/{uuid}/void")
    @PreAuthorize("hasAuthority('COMMISSION_VOID')")
    public ResponseEntity<CommissionDto> voidCommission(
            @PathVariable UUID uuid, @Valid @RequestBody CommissionVoidRequest request) {
        return ResponseEntity.ok(commissionsService.voidCommission(uuid, request.reason()));
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
            @Valid @RequestBody CommissionPayoutRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(commissionPayoutService.execute(request, actor.getUuid()));
    }

    /**
     * Pays an ad-hoc set of {@code APPROVED} commission rows picked by hand
     * from the approval table (E.2), instead of every row inside a date
     * range ({@link #payout}). Every id must currently be {@code APPROVED};
     * the whole request is rejected (400) otherwise — see {@code
     * CommissionPayoutService#executeBySelection}. Pass {@code dryRun=true}
     * to preview totals without committing.
     */
    @PostMapping("/payout/by-selection")
    @PreAuthorize("hasAuthority('COMMISSION_PAYOUT')")
    public ResponseEntity<CommissionPayoutResponse> payoutBySelection(
            @Valid @RequestBody CommissionPayoutBySelectionRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(commissionPayoutService.executeBySelection(request, actor.getUuid()));
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

    /**
     * Settlement close/retroactive top-up (V105 legacy whole-period mode
     * when {@code periodStart}/{@code periodEnd} are set, V150 cut mode —
     * Fase A retroactive settlement axis — when {@code asOf} is set): for
     * every beneficiary with at least one PAID direct-inscription/collection
     * commission or hierarchy override, tops up the gap between the highest
     * band reached so far and what was already disbursed. Run after {@code
     * /re-rate} (and its hierarchy-override sibling) — those bump PENDING
     * rows in place; this covers what's already PAID and off-limits to
     * them. Reuses {@code COMMISSION_RE_RATE} (same actor/action, generic
     * ledger).
     */
    @PostMapping("/retroactive-topups")
    @PreAuthorize("hasAuthority('COMMISSION_RE_RATE')")
    public ResponseEntity<CommissionRetroactiveTopUpResponse> retroactiveTopUps(
            @Valid @RequestBody CommissionRetroactiveTopUpRequest request) {
        CommissionRetroactiveTopUpResponse response = request.asOf() != null
                ? commissionRetroactiveTopUpService.executeCut(request)
                : commissionRetroactiveTopUpService.execute(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Feeds the 2-level expandable approval table (V107, hub plan §4): one
     * node per promoter with the period's total-to-commission, each
     * carrying every individual commission of that period regardless of
     * status — {@code PENDING} rows are the ones actually actionable via
     * {@link #approve}/{@link #reject}; anything else comes back {@code
     * locked} so the UI shows the period's real total without letting the
     * gerente comercial touch already-settled rows.
     */
    @GetMapping("/approval-queue")
    @PreAuthorize("hasAuthority('COMMISSION_APPROVE')")
    public ResponseEntity<List<CommissionApprovalGroupDto>> approvalQueue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(commissionApprovalService.approvalQueue(periodStart, periodEnd));
    }

    /**
     * Approves the given PENDING commission rows (row-level granularity —
     * never bulk-by-promoter). Every id must currently be PENDING; the
     * whole request is rejected (400) otherwise.
     */
    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('COMMISSION_APPROVE')")
    public ResponseEntity<CommissionApprovalActionResponse> approve(
            @Valid @RequestBody ApproveCommissionsRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(commissionApprovalService.approveRows(request.commissionUuids(), actor.getUuid()));
    }

    /**
     * Rejects the given PENDING commission rows and cascades the void to
     * every hierarchy override that depends on each one, directly or
     * through a chain of overrides — see {@code CommissionApprovalService}.
     */
    @PostMapping("/reject")
    @PreAuthorize("hasAuthority('COMMISSION_APPROVE')")
    public ResponseEntity<CommissionApprovalActionResponse> reject(
            @Valid @RequestBody RejectCommissionsRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(commissionApprovalService.rejectRows(
                request.commissionUuids(), actor.getUuid(), request.reason()));
    }
}
