package com.fenixcore.optibienestar360.modules.onboarding;

import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
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
 * Admin read/update of the single active onboarding policy
 * ({@code onboarding_policies}, hub ADR 0037). Singleton resource, same shape
 * as {@code AdminSecurityPolicyController}. Permissions seeded by V231.
 */
@RestController
@RequestMapping("/v1/admin/onboarding-policy")
@RequiredArgsConstructor
public class AdminOnboardingPolicyController {

    private static final String VIEW   = "hasAuthority('ONBOARDING_POLICY_VIEW')";
    private static final String UPDATE = "hasAuthority('ONBOARDING_POLICY_UPDATE')";

    private final OnboardingPolicyService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<OnboardingPolicyDto> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    @PreAuthorize(UPDATE)
    public ResponseEntity<OnboardingPolicyDto> update(@Valid @RequestBody OnboardingPolicyUpdateRequest req) {
        return ResponseEntity.ok(service.update(req));
    }
}
