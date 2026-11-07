package com.fenixcore.optibienestar360.modules.onboarding;

import com.fenixcore.optibienestar360.modules.onboarding.dto.PublicOnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public view of the onboarding policy ({@code GET /v1/public/onboarding-policy}):
 * the portal and the landing use it to hide the self-registration links and
 * to redirect the forms to login while a registration is switched off.
 * Under {@code /v1/public/**}, already {@code permitAll()}.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicOnboardingPolicyController {

    private final OnboardingPolicyService service;

    @GetMapping("/onboarding-policy")
    public PublicOnboardingPolicyDto get() {
        return service.publicView();
    }
}
