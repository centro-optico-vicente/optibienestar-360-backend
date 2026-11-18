package com.fenixcore.optibienestar360.modules.organization;

import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company identity and contact data for the public site and the login screen
 * (e.g. the "contact the company" WhatsApp button). Under {@code /v1/public/**},
 * already {@code permitAll()}; read-only.
 */
@RestController
@RequestMapping("/v1/public/organization")
@RequiredArgsConstructor
public class PublicOrganizationController {

    private final OrganizationService service;

    @GetMapping
    public ResponseEntity<PublicOrganizationDto> get() {
        return ResponseEntity.ok(service.getPublic());
    }
}
