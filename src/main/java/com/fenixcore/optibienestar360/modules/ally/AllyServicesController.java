package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.dto.ProposeAllyServiceRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServiceReviewService;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServicesProposeService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Ally-side surface at {@code /v1/aliado/services}. Distinct from the
 * admin-side {@code /v1/admin/allies/{uuid}/services} controller —
 * authorization here is membership-based (the actor must belong to the
 * target ally with OWNER or STAFF role) rather than permission-based, so
 * the URL prefix segregation keeps the two flows visibly separate.
 */
@RestController
@RequestMapping("/v1/aliado/services")
@RequiredArgsConstructor
public class AllyServicesController {

    private final AllyServicesProposeService proposeService;
    private final AllyServiceReviewService reviewService;

    /**
     * Propose a new service for the requested ally. Lands at
     * {@code reviewStatus=PROPOSED}; admin promotes via the workflow
     * endpoints. The Location header points at the admin-side detail URL
     * for convenience — the response body is the freshly-saved DTO.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AllyServiceDto> propose(@Valid @RequestBody ProposeAllyServiceRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails actor) {
        AllyServiceDto created = proposeService.propose(actor.getUuid(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/admin/allies/{allyUuid}/services/{uuid}")
                .buildAndExpand(request.allyUuid(), created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * The ally owner retires one of their own APPROVED services — it transitions
     * to REMOVED (leaves the public directory). Membership-based authorization
     * (active OWNER/STAFF on the parent ally); no {@code ALLY_SERVICE_APPROVE}.
     * An optional {@code reason} is recorded on the review log; a default is used
     * when omitted so the workflow invariant (REMOVED carries a reason) holds.
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AllyServiceDto> remove(@PathVariable UUID uuid,
                                                 @RequestParam(required = false) String reason,
                                                 @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.allyRemove(uuid, actor.getUuid(), reason));
    }

    /**
     * The ally owner reads the review history of one of their services — the same
     * shared log the admin sees, scoped to services of an ally the caller belongs
     * to (404 otherwise, so foreign services aren't enumerable).
     */
    @GetMapping("/{uuid}/log")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AllyServiceReviewLogDto>> log(@PathVariable UUID uuid,
                                                             @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(reviewService.allyLog(uuid, actor.getUuid()));
    }
}
