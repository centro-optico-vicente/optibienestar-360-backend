package com.fenixcore.optisaludplus.modules.ally;

import com.fenixcore.optisaludplus.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optisaludplus.modules.ally.dto.ProposeAllyServiceRequest;
import com.fenixcore.optisaludplus.modules.ally.service.AllyServicesProposeService;
import com.fenixcore.optisaludplus.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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
}
