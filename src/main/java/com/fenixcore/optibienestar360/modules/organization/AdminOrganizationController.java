package com.fenixcore.optibienestar360.modules.organization;

import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationUpdateRequest;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin read/update of the single {@code organizations} row (ADR 0015 §4,
 * plan "CRUD admin de Currency + ExchangeRate y país↔moneda oficial"). No
 * {@code /{uuid}} path — {@code /me} always resolves to the one tenant row
 * (see {@code OrganizationRepository#findSingleton}); no list/create/delete,
 * see {@code V98} for why.
 */
@RestController
@RequestMapping("/v1/admin/organizations")
@RequiredArgsConstructor
public class AdminOrganizationController {

    private static final String VIEW   = "hasAuthority('ORGANIZATION_VIEW')";
    private static final String UPDATE = "hasAuthority('ORGANIZATION_UPDATE')";

    private final OrganizationService service;

    @GetMapping("/me")
    @PreAuthorize(VIEW)
    public ResponseEntity<OrganizationDto> getMine() {
        return ResponseEntity.ok(service.getMine());
    }

    @PutMapping("/me")
    @PreAuthorize(UPDATE)
    public ResponseEntity<OrganizationDto> updateMine(@Valid @RequestBody OrganizationUpdateRequest req) {
        return ResponseEntity.ok(service.updateMine(req));
    }
}
