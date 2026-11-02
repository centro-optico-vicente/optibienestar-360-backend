package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanUpdateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipTermRowDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyResultDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermSummaryDto;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipTermComplianceService;
import com.fenixcore.optibienestar360.modules.membership.service.PlansService;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over plans. The catalog is small (3 SKUs currently — Individual,
 * Familiar, Corporativo) so the listing default returns a high page size
 * sorted alphabetically; specialized filtering via RSQL stays available.
 */
@RestController
@RequestMapping("/v1/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final PlansService plansService;
    private final MembershipTermComplianceService termComplianceService;

    @GetMapping
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<PlanDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<PlanDto> page = plansService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, plansService.effectiveSort(pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(plansService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<PlanDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(plansService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLAN_CREATE')")
    public ResponseEntity<PlanDto> create(@Valid @RequestBody PlanCreateRequest request) {
        PlanDto created = plansService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_UPDATE')")
    public ResponseEntity<PlanDto> update(@PathVariable UUID uuid,
                                          @Valid @RequestBody PlanUpdateRequest request) {
        return ResponseEntity.ok(plansService.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(plansService.getUsage(uuid));
    }

    // ─── Membership term compliance (hub ADR 0034) ──────────────────────────

    /** Counts of live memberships that comply / differ from the plan's term rule + the suggested first expiry. */
    @GetMapping("/{uuid}/membership-terms")
    @PreAuthorize("hasAuthority('MEMBERSHIP_TERM_VIEW_ALL')")
    public ResponseEntity<PlanTermSummaryDto> termSummary(@PathVariable UUID uuid) {
        return ResponseEntity.ok(termComplianceService.summary(uuid));
    }

    /** Live memberships of the plan with their term snapshot; {@code compliance} = COMPLIES | DIFFERS. */
    @GetMapping("/{uuid}/membership-terms/memberships")
    @PreAuthorize("hasAuthority('MEMBERSHIP_TERM_VIEW_ALL')")
    public ResponseEntity<Page<MembershipTermRowDto>> termMemberships(
            @PathVariable UUID uuid,
            @RequestParam(required = false) MembershipTermComplianceService.Compliance compliance,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(termComplianceService.list(uuid, compliance, q, pageable));
    }

    /** Applies the plan's term rule to the given memberships (empty = all that differ) with a chosen first expiry. */
    @PostMapping("/{uuid}/membership-terms/apply")
    @PreAuthorize("hasAuthority('MEMBERSHIP_TERM_UPDATE')")
    public ResponseEntity<PlanTermApplyResultDto> termApply(@PathVariable UUID uuid,
                                                            @Valid @RequestBody PlanTermApplyRequest request) {
        return ResponseEntity.ok(termComplianceService.apply(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        plansService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
