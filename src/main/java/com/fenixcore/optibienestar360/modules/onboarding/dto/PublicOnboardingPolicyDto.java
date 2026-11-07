package com.fenixcore.optibienestar360.modules.onboarding.dto;

/**
 * What the public pages need from the onboarding policy
 * ({@code GET /v1/public/onboarding-policy}): whether each self-registration
 * is open (hide the links, redirect the forms to login) and the modes that
 * decide what the "what happens next" texts say.
 */
public record PublicOnboardingPolicyDto(
        boolean memberSelfRegistrationEnabled,
        boolean allySelfRegistrationEnabled,
        String memberApprovalMode,
        String memberActivationMode,
        String allyApprovalMode,
        String allyPublishMode
) {}
