package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusAwardsService;
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
 * Promoter self-service view of their own bonuses (v2 PDF #5). Resolves the
 * promoter from the JWT subject; 404 when the caller is not a promoter.
 */
@RestController
@RequestMapping("/v1/promoter/me/bonuses")
@RequiredArgsConstructor
public class PromoterBonusesController {

    private final BonusAwardsService bonusAwardsService;

    @GetMapping
    @PreAuthorize("hasAuthority('BONUS_VIEW_OWN')")
    public ResponseEntity<Page<BonusAwardDto>> myBonuses(
            @AuthenticationPrincipal CustomUserDetails actor,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bonusAwardsService.listOwn(actor.getUuid(), pageable));
    }
}
