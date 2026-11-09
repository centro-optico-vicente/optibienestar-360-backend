package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.service.AllyRequestService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Last step of the ally's registration ({@code POST /v1/me/allies/{allyUuid}/submit},
 * hub ADR 0037 §2): the OWNER sends the request to review. The earlier steps
 * are the ally's own profile ({@code /v1/me/allies/{allyUuid}/profile},
 * licenses, opening hours, images). Gated by {@code ALLY_UPDATE_OWN}.
 */
@RestController
@RequestMapping({"/v1/me/allies/{allyUuid}/submit", "/v1/allies/me/{allyUuid}/submit"})
@RequiredArgsConstructor
public class MyAllyRegistrationController {

    private final AllyRequestService service;

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_UPDATE_OWN')")
    public ResponseEntity<AllyRequestCreatedDto> submit(@PathVariable UUID allyUuid,
                                                        @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.submit(allyUuid, actor.getUuid()));
    }
}
