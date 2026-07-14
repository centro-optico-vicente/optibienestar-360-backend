package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/members/{memberUuid}/memberships} —
 * subscription history + enrollment for a member.
 *
 * <p>Lifecycle transitions (cancel / reactivate) live on a separate
 * controller under {@code /v1/admin/memberships/{uuid}/...} because they
 * operate on the membership UUID directly without needing the parent
 * member path.</p>
 */
@RestController
@RequestMapping("/v1/admin/members/{memberUuid}/memberships")
@RequiredArgsConstructor
public class AdminMemberMembershipsController {

    private final MembershipsService membershipsService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBERSHIP_VIEW_ALL')")
    public ResponseEntity<List<MembershipDto>> list(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(membershipsService.listForMember(memberUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBERSHIP_VIEW_ALL')")
    public ResponseEntity<MembershipDto> get(@PathVariable UUID memberUuid,
                                             @PathVariable UUID uuid) {
        return ResponseEntity.ok(membershipsService.get(memberUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBERSHIP_CREATE')")
    public ResponseEntity<MembershipDto> enroll(@PathVariable UUID memberUuid,
                                                @Valid @RequestBody MembershipCreateRequest request) {
        MembershipDto created = membershipsService.enroll(memberUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
