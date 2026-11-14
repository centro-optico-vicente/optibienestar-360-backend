package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.AssignPromoterRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BulkAssignPromoterRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateFilter;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateSource;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionResultDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.dto.PortfolioToSupervisorRequest;
import com.fenixcore.optibienestar360.modules.member.service.MemberPortfolioDistributionService;
import com.fenixcore.optibienestar360.modules.member.service.MemberPromoterService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Admin reassignment of the permanent member↔promoter link (v2 PDF 2.a).
 * Separate controller from {@code AdminMemberController} so the distinct
 * permission ({@code MEMBER_ASSIGN_PROMOTER}, gates only this action) and the
 * audit semantics stay visibly segregated from plain member CRUD.
 *
 * <p>Also hosts the portfolio distribution (ADR LOC-0016): {@code GET /advisor/candidates} lists the
 * movable members (unassigned or one advisor's portfolio) and {@code POST /advisor/distribute}
 * (with its write-free {@code /preview}) deals a selection to one or more advisors.</p>
 */
@RestController
@RequestMapping("/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberPromoterController {

    private final MemberPromoterService memberPromoterService;
    private final MemberPortfolioDistributionService distributionService;

    @PostMapping("/{memberUuid}/assign-advisor")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<MemberPromoterAssignmentDto> assignPromoter(
            @PathVariable UUID memberUuid,
            @Valid @RequestBody AssignPromoterRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(memberPromoterService.assign(
                memberUuid, request.promoterUuid(), request.referralCode(), request.reason(), actor.getUuid()));
    }

    @PostMapping("/advisor/bulk-assign")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<List<MemberPromoterAssignmentDto>> bulkAssignPromoter(
            @Valid @RequestBody BulkAssignPromoterRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(memberPromoterService.bulkAssign(
                request.memberUuids(), request.promoterUuid(), request.reason(), actor.getUuid()));
    }

    @PostMapping("/advisor/portfolio-to-supervisor")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<List<MemberPromoterAssignmentDto>> portfolioToSupervisor(
            @Valid @RequestBody PortfolioToSupervisorRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(memberPromoterService.reassignPortfolioToSupervisor(
                request.sourcePromoterUuid(), request.reason(), actor.getUuid()));
    }

    /**
     * Members that can be distributed: {@code source=UNASSIGNED} (no advisor or INSTITUCION) or
     * {@code source=ADVISOR&sourcePromoterUuid=} (that advisor's portfolio, even if inactive or the
     * system one); {@code q} over name/document, {@code planUuid} of the active membership,
     * {@code status} of the member. Paged ({@code page}, {@code size}, {@code sort} by
     * {@code displayName} — default, asc — or {@code createdAt}).
     */
    @GetMapping("/advisor/candidates")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<Page<MemberCandidateDto>> candidates(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam MemberCandidateSource source,
            @RequestParam(required = false) UUID sourcePromoterUuid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID planUuid,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(distributionService.candidates(
                new MemberCandidateFilter(source, sourcePromoterUuid, q, planUuid, status), pageable));
    }

    /** What {@link #distribute} would do — same body, same validation, nothing written. */
    @PostMapping("/advisor/distribute/preview")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<MemberDistributionResultDto> previewDistribution(
            @Valid @RequestBody MemberDistributionRequest request) {
        return ResponseEntity.ok(distributionService.preview(request));
    }

    /**
     * Deals the selected members ({@code memberUuids}, or every result of {@code filter} minus
     * {@code excludeMemberUuids}) to the {@code targets} in {@code EQUAL} parts or by {@code COUNT};
     * all or nothing, one audit row with the reason per moved member.
     */
    @PostMapping("/advisor/distribute")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<MemberDistributionResultDto> distribute(
            @Valid @RequestBody MemberDistributionRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(distributionService.distribute(request, actor.getUuid()));
    }

    @GetMapping("/{memberUuid}/advisor-history")
    @PreAuthorize("hasAuthority('MEMBER_ASSIGN_PROMOTER')")
    public ResponseEntity<List<MemberPromoterAssignmentDto>> promoterHistory(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(memberPromoterService.history(memberUuid));
    }
}
