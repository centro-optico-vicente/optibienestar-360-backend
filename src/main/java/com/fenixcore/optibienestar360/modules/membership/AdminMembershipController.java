package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCancelRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipReactivateRequest;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lifecycle transition surface — sits at the top of the URL space (no
 * parent member path) because cancel + reactivate operate on the membership
 * UUID directly. The admin UI typically navigates from
 * {@code /v1/admin/members/{uuid}/memberships} and dispatches the
 * transition by membership UUID, so the controller doesn't need the parent
 * member in its path.
 *
 * <p>Read endpoints (list, detail) stay under
 * {@code /v1/admin/members/{uuid}/memberships} — the sub-resource shape
 * helps the UI group history under the member detail page.</p>
 */
@RestController
@RequestMapping("/v1/admin/memberships")
@RequiredArgsConstructor
public class AdminMembershipController {

    private final MembershipLifecycleService lifecycleService;
    private final com.fenixcore.optibienestar360.modules.membership.service.MembershipsService membershipsService;

    /**
     * Cross-member list with filters: affiliate text, advisor, plan, plan type, status (lifecycle or
     * derived OVERDUE / DUE_SOON) and enrollment / next-due date ranges. Dates are {@code yyyy-MM-dd}.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('MEMBERSHIP_VIEW_ALL')")
    public ResponseEntity<org.springframework.data.domain.Page<com.fenixcore.optibienestar360.modules.membership.dto.MembershipListItemDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID memberUuid,
            @RequestParam(required = false) UUID promoterUuid,
            @RequestParam(required = false) UUID planUuid,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer dueSoonDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enrolledFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enrolledTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nextDueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nextDueTo,
            @PageableDefault(size = 20) Pageable pageable) {
        var criteria = new com.fenixcore.optibienestar360.modules.membership.dto.MembershipSearchCriteria(
                q, memberUuid, promoterUuid, planUuid, planType, status, dueSoonDays,
                enrolledFrom, enrolledTo, nextDueFrom, nextDueTo);
        return ResponseEntity.ok(membershipsService.search(criteria, pageable));
    }

    @PutMapping("/{uuid}/cancel")
    @PreAuthorize("hasAuthority('MEMBERSHIP_CANCEL')")
    public ResponseEntity<MembershipDto> cancel(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) MembershipCancelRequest request) {
        return ResponseEntity.ok(lifecycleService.cancel(uuid, request));
    }

    @PutMapping("/{uuid}/reactivate")
    @PreAuthorize("hasAuthority('MEMBERSHIP_REACTIVATE')")
    public ResponseEntity<MembershipDto> reactivate(
            @PathVariable UUID uuid,
            @Valid @RequestBody(required = false) MembershipReactivateRequest request) {
        return ResponseEntity.ok(lifecycleService.reactivate(uuid, request));
    }
}
