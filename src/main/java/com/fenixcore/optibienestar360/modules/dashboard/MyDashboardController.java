package com.fenixcore.optibienestar360.modules.dashboard;

import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardDto;
import com.fenixcore.optibienestar360.modules.dashboard.service.DashboardService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home dashboard of the intranet ({@code GET /v1/me/dashboard}). Any
 * authenticated user may call it; there is no dedicated permission because
 * {@link DashboardService} assembles the content from the caller's own
 * authorities (management, advisor, ally and member blocks), each gated by the
 * permissions that already protect that data.
 */
@RestController
@RequestMapping("/v1/me/dashboard")
@RequiredArgsConstructor
public class MyDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardDto> mine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(dashboardService.getMyDashboard(actor.getUuid(),
                actor.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()));
    }
}
