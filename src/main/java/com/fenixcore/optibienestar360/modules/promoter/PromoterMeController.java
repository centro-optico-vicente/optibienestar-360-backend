package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterDashboardService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Promoter self-service surface at {@code /v1/promoter/me} (v2 PDF #4). The
 * dashboard resolves the promoter from the JWT-authenticated user, so no
 * promoter id travels in the URL. Gated on {@code PROMOTER_VIEW_OWN} (granted to
 * PROMOTOR + ADMINISTRADOR + SYSTEM in V35); a user who holds the permission but
 * has no promoter row gets a 404 ({@code me.promoter.not_found}).
 */
@RestController
@RequestMapping("/v1/promoter/me")
@RequiredArgsConstructor
public class PromoterMeController {

    private final PromoterDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_OWN')")
    public ResponseEntity<PromoterDashboardDto> dashboard(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(dashboardService.getMyDashboard(actor.getUuid()));
    }
}
