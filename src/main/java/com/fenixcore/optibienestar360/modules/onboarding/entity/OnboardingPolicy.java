package com.fenixcore.optibienestar360.modules.onboarding.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single active onboarding policy (V231, hub ADR 0037 §1): which
 * self-registrations are open and how a submitted request is approved and
 * activated. The field defaults reproduce the behavior before the policy
 * existed, so a missing row changes nothing.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "onboarding_policies")
@AttributeOverride(name = "id", column = @Column(name = "onboarding_policies_id", nullable = false, updatable = false))
public class OnboardingPolicy extends BaseAuditEntity {

    /** When a submitted affiliation request becomes a member + membership. */
    public enum MemberApprovalMode {
        /** A staff member approves every request. */
        MANUAL,
        /** Approved automatically when it reports the inscription payment; otherwise manual. */
        AUTO_IF_PAYMENT_REPORTED,
        /** Approved automatically on submit. */
        AUTO_ON_SUBMIT
    }

    /** When the new membership leaves SUSPENDED. */
    public enum MemberActivationMode {
        /** When the inscription payment is approved (the payments flow activates it). */
        ON_PAYMENT_APPROVED,
        /** Provisionally active when a payment is reported; re-suspended if that payment is rejected. */
        ON_PAYMENT_REPORTED,
        /** Active as soon as the request is approved. */
        IMMEDIATE
    }

    /** How a self-registered ally is approved. */
    public enum AllyApprovalMode {
        /** An operator approves it with an ACTIVE agreement (hub ADR 0036). */
        MANUAL_WITH_AGREEMENT,
        /** Approved on submit with a default agreement for its segment. */
        AUTO_ON_SUBMIT
    }

    /** When an approved ally is published in the public directory. */
    public enum AllyPublishMode {
        MANUAL,
        ON_APPROVAL
    }

    @Column(name = "allow_member_self_registration", nullable = false)
    private boolean allowMemberSelfRegistration = true;

    @Column(name = "allow_ally_self_registration", nullable = false)
    private boolean allowAllySelfRegistration = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_approval_mode", nullable = false, length = 30)
    private MemberApprovalMode memberApprovalMode = MemberApprovalMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_activation_mode", nullable = false, length = 30)
    private MemberActivationMode memberActivationMode = MemberActivationMode.ON_PAYMENT_APPROVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ally_approval_mode", nullable = false, length = 30)
    private AllyApprovalMode allyApprovalMode = AllyApprovalMode.MANUAL_WITH_AGREEMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "ally_publish_mode", nullable = false, length = 30)
    private AllyPublishMode allyPublishMode = AllyPublishMode.MANUAL;
}
