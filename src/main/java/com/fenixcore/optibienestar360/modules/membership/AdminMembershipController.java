package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCancelRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipReactivateRequest;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
