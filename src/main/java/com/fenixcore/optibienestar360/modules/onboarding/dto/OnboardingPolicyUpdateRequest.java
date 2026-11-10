package com.fenixcore.optibienestar360.modules.onboarding.dto;

import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyPublishMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberActivationMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberApprovalMode;
import jakarta.validation.constraints.NotNull;

/**
 * Full replacement of the active onboarding policy. While a self-registration
 * is switched off its modes are read-only: they must arrive unchanged
 * (422 {@code onboarding_policy.member_modes.read_only} /
 * {@code onboarding_policy.ally_modes.read_only}). Switching it back on in the
 * same request makes them editable again. The draft retention days must be within 0..365 (422
 * {@code onboarding_policy.draft_retention.invalid}); 0 = never clean up that kind.
 */
public record OnboardingPolicyUpdateRequest(
        @NotNull Boolean allowMemberSelfRegistration,
        @NotNull Boolean allowAllySelfRegistration,
        @NotNull MemberApprovalMode memberApprovalMode,
        @NotNull MemberActivationMode memberActivationMode,
        @NotNull AllyApprovalMode allyApprovalMode,
        @NotNull AllyPublishMode allyPublishMode,
        @NotNull Integer memberDraftRetentionDays,
        @NotNull Integer allyDraftRetentionDays
) {}
