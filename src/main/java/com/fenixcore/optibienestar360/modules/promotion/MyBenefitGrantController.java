package com.fenixcore.optibienestar360.modules.promotion;

import com.fenixcore.optibienestar360.modules.promotion.dto.MembershipBenefitGrantDto;
import com.fenixcore.optibienestar360.modules.promotion.service.MembershipBenefitGrantService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The affiliate's own ally vouchers from campaigns (V257, hub ADR 0038 §7), any status, newest first. */
@RestController
@RequestMapping("/v1/me/benefit-grants")
@RequiredArgsConstructor
public class MyBenefitGrantController {

    private final MembershipBenefitGrantService grantService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<List<MembershipBenefitGrantDto>> mine(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(defaultValue = "false") boolean includePending) {
        // includePending (V260): ally benefits whose slot awaits confirmation, as PENDING_CONFIRMATION entries.
        return ResponseEntity.ok(includePending
                ? grantService.forUser(actor.getUuid(), true)
                : grantService.forUser(actor.getUuid()));
    }
}
