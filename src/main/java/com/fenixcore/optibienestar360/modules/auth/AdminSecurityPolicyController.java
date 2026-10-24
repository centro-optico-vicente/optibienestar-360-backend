package com.fenixcore.optibienestar360.modules.auth;

import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyDto;
import com.fenixcore.optibienestar360.modules.auth.dto.SecurityPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.auth.service.SecurityPolicyService;
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
 * Admin read/update of the single active auth security policy
 * ({@code security_policies}). Singleton resource — no {@code /{uuid}}, no
 * list/create/delete, same shape as {@code AdminOrganizationController}.
 * Permissions seeded by V191.
 */
@RestController
@RequestMapping("/v1/admin/security-policy")
@RequiredArgsConstructor
public class AdminSecurityPolicyController {

    private static final String VIEW   = "hasAuthority('SECURITY_POLICY_VIEW')";
    private static final String UPDATE = "hasAuthority('SECURITY_POLICY_UPDATE')";

    private final SecurityPolicyService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<SecurityPolicyDto> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    @PreAuthorize(UPDATE)
    public ResponseEntity<SecurityPolicyDto> update(@Valid @RequestBody SecurityPolicyUpdateRequest req) {
        return ResponseEntity.ok(service.update(req));
    }
}
