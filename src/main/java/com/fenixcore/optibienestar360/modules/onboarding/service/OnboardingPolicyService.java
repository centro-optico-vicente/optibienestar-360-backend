package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.dto.OnboardingPolicyUpdateRequest;
import com.fenixcore.optibienestar360.modules.onboarding.dto.PublicOnboardingPolicyDto;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyApprovalMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.AllyPublishMode;
import com.fenixcore.optibienestar360.modules.onboarding.entity.OnboardingPolicy.MemberActivationMode;
import com.fenixcore.optibienestar360.modules.onboarding.repository.OnboardingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Read/update of the single active {@code onboarding_policies} row (V231,
 * hub ADR 0037 §1), plus the decisions the registration flows ask it for.
 * Same singleton shape as {@code SecurityPolicyService}: with no active row
 * the entity defaults apply, which reproduce the behavior before the policy
 * existed (both self-registrations open, manual approval, membership active
 * once the payment is approved, manual ally approval and publishing).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingPolicyService {

    static final int MIN_RETENTION_DAYS = 1;
    static final int MAX_RETENTION_DAYS = 365;

    private final OnboardingPolicyRepository repository;

    public OnboardingPolicyDto get() {
        return toDto(current());
    }

    public PublicOnboardingPolicyDto publicView() {
        OnboardingPolicy p = current();
        return new PublicOnboardingPolicyDto(
                p.isAllowMemberSelfRegistration(),
                p.isAllowAllySelfRegistration(),
                p.getMemberApprovalMode().name(),
                p.getMemberActivationMode().name(),
                p.getAllyApprovalMode().name(),
                p.getAllyPublishMode().name());
    }

    /**
     * Replaces the active values. While a self-registration is off (after
     * this update), its modes are read-only and must arrive unchanged.
     */
    @Transactional
    @Auditable(entity = "onboarding_policy", action = AuditAction.UPDATE)
    public OnboardingPolicyDto update(OnboardingPolicyUpdateRequest req) {
        OnboardingPolicy policy = current();
        boolean memberOpen = req.allowMemberSelfRegistration();
        boolean allyOpen = req.allowAllySelfRegistration();

        if (!memberOpen && (req.memberApprovalMode() != policy.getMemberApprovalMode()
                || req.memberActivationMode() != policy.getMemberActivationMode())) {
            throw new IllegalArgumentException("onboarding_policy.member_modes.read_only");
        }
        if (!allyOpen && (req.allyApprovalMode() != policy.getAllyApprovalMode()
                || req.allyPublishMode() != policy.getAllyPublishMode())) {
            throw new IllegalArgumentException("onboarding_policy.ally_modes.read_only");
        }

        requireRetention(req.memberDraftRetentionDays());
        requireRetention(req.allyDraftRetentionDays());

        policy.setAllowMemberSelfRegistration(memberOpen);
        policy.setAllowAllySelfRegistration(allyOpen);
        policy.setMemberApprovalMode(Objects.requireNonNull(req.memberApprovalMode()));
        policy.setMemberActivationMode(Objects.requireNonNull(req.memberActivationMode()));
        policy.setAllyApprovalMode(Objects.requireNonNull(req.allyApprovalMode()));
        policy.setAllyPublishMode(Objects.requireNonNull(req.allyPublishMode()));
        policy.setMemberDraftRetentionDays(req.memberDraftRetentionDays());
        policy.setAllyDraftRetentionDays(req.allyDraftRetentionDays());
        return toDto(repository.save(policy));
    }

    // ─── Decisions for the registration flows ───────────────────────────────

    /** 403 {@code SELF_REGISTRATION_DISABLED} when the public affiliation form is switched off. */
    public void requireMemberSelfRegistration() {
        if (!current().isAllowMemberSelfRegistration()) {
            throw new SelfRegistrationDisabledException("onboarding.member_self_registration.disabled");
        }
    }

    /** 403 {@code SELF_REGISTRATION_DISABLED} when the public ally form is switched off. */
    public void requireAllySelfRegistration() {
        if (!current().isAllowAllySelfRegistration()) {
            throw new SelfRegistrationDisabledException("onboarding.ally_self_registration.disabled");
        }
    }

    /** Whether a just-submitted affiliation request is approved without a reviewer. */
    public boolean autoApprovesMember(boolean paymentReported) {
        return switch (current().getMemberApprovalMode()) {
            case MANUAL -> false;
            case AUTO_IF_PAYMENT_REPORTED -> paymentReported;
            case AUTO_ON_SUBMIT -> true;
        };
    }

    /**
     * Whether the membership created on approval starts ACTIVE instead of
     * SUSPENDED. With {@code ON_PAYMENT_APPROVED} it stays SUSPENDED and the
     * payments flow activates it, as before the policy existed.
     */
    public boolean activatesMembershipOnApproval(boolean paymentReported) {
        return switch (current().getMemberActivationMode()) {
            case ON_PAYMENT_APPROVED -> false;
            case ON_PAYMENT_REPORTED -> paymentReported;
            case IMMEDIATE -> true;
        };
    }

    /** Whether a rejected payment takes back a provisional activation ({@code ON_PAYMENT_REPORTED}). */
    public boolean suspendsOnRejectedPayment() {
        return current().getMemberActivationMode() == MemberActivationMode.ON_PAYMENT_REPORTED;
    }

    /** Whether a just-submitted ally request is approved without an operator. */
    public boolean autoApprovesAlly() {
        return current().getAllyApprovalMode() == AllyApprovalMode.AUTO_ON_SUBMIT;
    }

    /** Whether approving an ally also publishes it. */
    public boolean publishesAllyOnApproval() {
        return current().getAllyPublishMode() == AllyPublishMode.ON_APPROVAL;
    }

    /** Days an abandoned affiliation draft is kept (counted from its last change). */
    public int memberDraftRetentionDays() {
        return current().getMemberDraftRetentionDays();
    }

    /** Days an abandoned ally draft is kept (counted from its last change). */
    public int allyDraftRetentionDays() {
        return current().getAllyDraftRetentionDays();
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private static void requireRetention(Integer days) {
        if (days == null || days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException("onboarding_policy.draft_retention.invalid");
        }
    }

    private OnboardingPolicy current() {
        return repository.findFirstByActiveTrue().orElseGet(OnboardingPolicy::new);
    }

    private static OnboardingPolicyDto toDto(OnboardingPolicy p) {
        return new OnboardingPolicyDto(
                p.getUuid(),
                p.isAllowMemberSelfRegistration(),
                p.isAllowAllySelfRegistration(),
                p.getMemberApprovalMode(),
                p.getMemberActivationMode(),
                p.getAllyApprovalMode(),
                p.getAllyPublishMode(),
                p.getMemberDraftRetentionDays(),
                p.getAllyDraftRetentionDays());
    }
}
