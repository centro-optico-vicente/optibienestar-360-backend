package com.fenixcore.optibienestar360.modules.benefit;

import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.service.BenefitUsagesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service benefit-usage history ({@code GET /v1/me/usage-history},
 * vertical-9) — the affiliate's own consumption feed across every ally. Gated
 * by {@code MEMBER_VIEW_OWN}; scope is the caller's own membership history via
 * the repository query, newest usage first.
 */
@RestController
@RequestMapping("/v1/me/usage-history")
@RequiredArgsConstructor
public class MyUsageHistoryController {

    private final BenefitUsagesService benefitUsagesService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<Page<BenefitUsageDto>> list(
            @PageableDefault(size = 20, sort = "usageDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(benefitUsagesService.listForMemberUser(actor.getUuid(), pageable));
    }
}
