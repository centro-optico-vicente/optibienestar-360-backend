package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHourDto;
import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHoursRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyOpeningHoursService;
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

import java.util.List;
import java.util.UUID;

/**
 * Ally-portal opening hours (hub ADR 0031 §7). Any active member of the ally
 * reads them; only an active OWNER replaces them. Membership is checked in
 * {@link AllyOpeningHoursService}: a foreign or unknown ally answers 403.
 */
@RestController
// Primary /v1/me/...; /v1/<collection>/me/... is the secondary path (not deprecated).
@RequestMapping({"/v1/me/allies/{allyUuid}/opening-hours", "/v1/allies/me/{allyUuid}/opening-hours"})
@RequiredArgsConstructor
public class MyAllyOpeningHoursController {

    private final AllyOpeningHoursService openingHoursService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OpeningHourDto>> get(@PathVariable UUID allyUuid,
                                                    @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(openingHoursService.getForMember(allyUuid, actor.getUuid()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ALLY_OPENING_HOURS_UPDATE_OWN')")
    public ResponseEntity<List<OpeningHourDto>> replace(@PathVariable UUID allyUuid,
                                                        @Valid @RequestBody OpeningHoursRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(openingHoursService.replaceForOwner(allyUuid, actor.getUuid(), request));
    }
}
