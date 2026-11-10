package com.fenixcore.optibienestar360.modules.onboarding.dto;

import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyPublishMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberActivationMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberApprovalMode;

import java.util.UUID;

/**
 * The active onboarding policy ({@code GET/PUT /v1/admin/onboarding-policy}).
 * {@code uuid} is {@code null} only when no row exists and these are the
 * in-code defaults (today's behavior). The enum labels are translated in the
 * frontend, so there is no {@code _Display} pair (hub ADR 0014). The draft retention days (V237; 0 = never
 * clean up that kind) are admin-only: the public policy does not carry them.
 */
public record OnboardingPolicyDto(
        UUID uuid,
        boolean allowMemberSelfRegistration,
        boolean allowAllySelfRegistration,
        MemberApprovalMode memberApprovalMode,
        MemberActivationMode memberActivationMode,
        AllyApprovalMode allyApprovalMode,
        AllyPublishMode allyPublishMode,
        int memberDraftRetentionDays,
        int allyDraftRetentionDays
) {}
