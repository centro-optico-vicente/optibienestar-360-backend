package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiariesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service "my family" surface ({@code GET /v1/me/family}, vertical-9) —
 * the JWT-authenticated affiliate's own beneficiaries. Gated by
 * {@code MEMBER_VIEW_OWN}; the service scopes strictly to the caller's member
 * record via the user account.
 */
@RestController
@RequestMapping("/v1/me/family")
@RequiredArgsConstructor
public class MyFamilyController {

    private final BeneficiariesService beneficiariesService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<List<BeneficiaryDto>> getMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(beneficiariesService.listForUser(actor.getUuid()));
    }
}
