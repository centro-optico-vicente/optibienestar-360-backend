package com.fenixcore.optibienestar360.modules.organization;

import com.fenixcore.optibienestar360.core.config.PublicHttpCacheConfig;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationContactDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import com.fenixcore.optibienestar360.modules.organization.service.PublicOrganizationStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company data for the public site and the login screen. Under
 * {@code /v1/public/**}, already {@code permitAll()}; read-only.
 * <ul>
 *   <li>{@code GET /v1/public/organization} — identity (only when the admin
 *       ticked "public identity", otherwise empty) plus the contact block.</li>
 *   <li>{@code GET /v1/public/organization/contact} — contact data only (e.g.
 *       the "contact the company" WhatsApp button).</li>
 *   <li>{@code GET /v1/public/organization/stats} — allies / members counters
 *       of the login screen (hub ADR 0042); short public HTTP cache, like the
 *       public catalog, so the members count does not hit the DB per visit.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/public/organization")
@RequiredArgsConstructor
public class PublicOrganizationController {

    private final OrganizationService service;
    private final PublicOrganizationStatsService statsService;

    @GetMapping
    public ResponseEntity<PublicOrganizationDto> get() {
        return ResponseEntity.ok(service.getPublic());
    }

    @GetMapping("/contact")
    public ResponseEntity<PublicOrganizationContactDto> contact() {
        return ResponseEntity.ok(service.getPublicContact());
    }

    @GetMapping("/stats")
    public ResponseEntity<PublicOrganizationStatsDto> stats() {
        return ResponseEntity.ok()
                .cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(statsService.getStats());
    }
}
