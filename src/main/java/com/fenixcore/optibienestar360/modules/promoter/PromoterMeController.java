package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountDto;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterDashboardService;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterPayoutAccountsService;
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
 * Promoter self-service surface at {@code /v1/me/advisor} (v2 PDF #4). The
 * dashboard resolves the promoter from the JWT-authenticated user, so no
 * promoter id travels in the URL. Gated on {@code PROMOTER_VIEW_OWN} (granted to
 * ASESOR + ADMINISTRADOR + SYSTEM in V35); a user who holds the permission but
 * has no promoter row gets a 404 ({@code me.promoter.not_found}).
 */
@RestController
// Primary /v1/me/...; /v1/<collection>/me/... is the secondary path (not deprecated).
// Deprecated alias (last entry), remove once frontend >= #235 is deployed
@RequestMapping({"/v1/me/advisor", "/v1/advisors/me", "/v1/promoter/me"})
@RequiredArgsConstructor
public class PromoterMeController {

    private final PromoterDashboardService dashboardService;
    private final PromoterPayoutAccountsService payoutAccountsService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    public ResponseEntity<PromoterDashboardDto> dashboard(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(dashboardService.getMyDashboard(actor.getUuid()));
    }

    /**
     * Read-only list of the advisor's own payout accounts (V184) — only the
     * caller's, resolved from the JWT user. Gated on its own
     * {@code PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN}; edits stay admin-only.
     */
    @GetMapping("/payout-accounts")
    @PreAuthorize("hasAuthority('PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN')")
    public ResponseEntity<List<PromoterPayoutAccountDto>> myPayoutAccounts(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(payoutAccountsService.listMine(actor.getUuid()));
    }
}
