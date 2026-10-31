package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryAccessRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiariesService;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiaryAccessService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service "my family" surface ({@code GET /v1/me/family}, vertical-9) —
 * the JWT-authenticated affiliate's own beneficiaries. Gated by
 * {@code MEMBER_VIEW_OWN}; the service scopes strictly to the caller's member
 * record via the user account.
 *
 * <p>{@code POST} (V202, {@code MEMBER_BENEFICIARY_CREATE_OWN}) lets the
 * affiliate add a beneficiary under the same plan rules as the admin add.</p>
 */
@RestController
@RequestMapping("/v1/me/family")
@RequiredArgsConstructor
public class MyFamilyController {

    private final BeneficiariesService beneficiariesService;
    private final BeneficiaryAccessService accessService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<List<BeneficiaryDto>> getMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(beneficiariesService.listForUser(actor.getUuid()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBER_BENEFICIARY_CREATE_OWN')")
    public ResponseEntity<BeneficiaryDto> add(@Valid @RequestBody BeneficiaryCreateRequest request,
                                              @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiariesService.addForUser(actor.getUuid(), request));
    }

    /** Grants (or re-sends) the beneficiary's own portal login (V195); the body's email is optional if on file. */
    @PostMapping("/{uuid}/access")
    @PreAuthorize("hasAuthority('BENEFICIARY_ACCESS_GRANT_OWN')")
    public ResponseEntity<BeneficiaryDto> grantAccess(@PathVariable UUID uuid,
                                                      @Valid @RequestBody(required = false) BeneficiaryAccessRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(accessService.grant(actor.getUuid(), uuid, request));
    }

    @DeleteMapping("/{uuid}/access")
    @PreAuthorize("hasAuthority('BENEFICIARY_ACCESS_REVOKE_OWN')")
    public ResponseEntity<BeneficiaryDto> revokeAccess(@PathVariable UUID uuid,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(accessService.revoke(actor.getUuid(), uuid));
    }
}
