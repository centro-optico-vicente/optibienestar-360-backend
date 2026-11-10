package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileDto;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.MyAllyProfileService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The ally's own profile in the portal (hub ADR 0036). A self-registered ally
 * (role {@code ALIADO_PENDIENTE}) completes it while staff review the
 * request; an approved ally keeps it up to date. Reading needs
 * {@code ALLY_VIEW_OWN}, writing {@code ALLY_UPDATE_OWN}; both are also
 * membership based (any member reads, the OWNER writes). The advisor that
 * captured an ally (hub ADR 0037 §5) reaches the same endpoints while the
 * request is still a draft, with {@code ALLY_DRAFT_VIEW_ASSISTED} /
 * {@code ALLY_DRAFT_UPDATE_ASSISTED}; the service checks that the draft is theirs.
 */
@RestController
@RequestMapping({"/v1/me/allies/{allyUuid}/profile", "/v1/allies/me/{allyUuid}/profile"})
@RequiredArgsConstructor
public class MyAllyProfileController {

    private final MyAllyProfileService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ALLY_VIEW_OWN', 'ALLY_DRAFT_VIEW_ASSISTED')")
    public ResponseEntity<MyAllyProfileDto> get(@PathVariable UUID allyUuid,
                                                @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.get(allyUuid, actor.getUuid()));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ALLY_UPDATE_OWN', 'ALLY_DRAFT_UPDATE_ASSISTED')")
    public ResponseEntity<MyAllyProfileDto> update(@PathVariable UUID allyUuid,
                                                   @Valid @RequestBody MyAllyProfileUpdateRequest request,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.update(allyUuid, actor.getUuid(), request));
    }
}
