package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.MyReferralDto;
import com.fenixcore.optibienestar360.modules.promoter.service.ReferralsService;
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
 * Affiliate's own referrals surface. Mirrors {@code MyPaymentsController}:
 * single GET, paginated, no RSQL — the affiliate sees their full referral
 * history, not a curated query.
 *
 * <p>Default sort {@code createdAt DESC} covers all statuses, including
 * {@code PENDING_ENROLLMENT} rows that don't have {@code enrolledAt} yet.</p>
 */
@RestController
@RequestMapping("/v1/me/referrals")
@RequiredArgsConstructor
public class MyReferralsController {

    private final ReferralsService referralsService;

    @GetMapping
    @PreAuthorize("hasAuthority('REFERRAL_CODE_VIEW_OWN')")
    public ResponseEntity<Page<MyReferralDto>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(referralsService.listForUser(actor.getUuid(), pageable));
    }
}
