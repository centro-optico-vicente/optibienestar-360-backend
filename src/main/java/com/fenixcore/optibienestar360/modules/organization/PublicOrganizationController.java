package com.fenixcore.optibienestar360.modules.organization;

import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationContactDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
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
 * </ul>
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

    @GetMapping("/contact")
    public ResponseEntity<PublicOrganizationContactDto> contact() {
        return ResponseEntity.ok(service.getPublicContact());
    }
}
