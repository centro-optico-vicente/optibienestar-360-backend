package com.fenixcore.optibienestar360.modules.card;

import com.fenixcore.optibienestar360.modules.card.dto.DigitalCardDto;
import com.fenixcore.optibienestar360.modules.card.service.DigitalCardService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service affiliate digital card ({@code GET /v1/me/digital-card}, V39 /
 * vertical-9). Gated by {@code MEMBER_VIEW_OWN} (granted to AFILIADO by V6);
 * the service scopes strictly to the caller's own member row via the user
 * account, and 404s when the user is not an enrolled affiliate.
 */
@RestController
@RequestMapping("/v1/me/digital-card")
@RequiredArgsConstructor
public class MyDigitalCardController {

    private final DigitalCardService digitalCardService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<DigitalCardDto> getMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(digitalCardService.getForUser(actor.getUuid()));
    }
}
