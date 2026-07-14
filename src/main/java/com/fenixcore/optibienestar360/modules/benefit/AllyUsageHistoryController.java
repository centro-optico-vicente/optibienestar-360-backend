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
 * Ally-side history view of benefit usages — counterpart to
 * {@code POST /v1/ally/benefit-usage}. The operator at the counter sees
 * what their ally has registered, newest first.
 *
 * <p>Scope is implicit through the auth principal: the repository JPQL
 * resolves "my allies" from the {@code AllyUser} pivot (V12) so a single
 * user spanning multiple allies sees all of their usages in one feed.</p>
 */
@RestController
@RequestMapping("/v1/ally/usage-history")
@RequiredArgsConstructor
public class AllyUsageHistoryController {

    private final BenefitUsagesService benefitUsagesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<Page<BenefitUsageDto>> list(
            @PageableDefault(size = 20, sort = "usageDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(benefitUsagesService.listForAllyUser(actor.getUuid(), pageable));
    }
}
