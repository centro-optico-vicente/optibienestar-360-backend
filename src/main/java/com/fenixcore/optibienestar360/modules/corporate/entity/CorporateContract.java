package com.fenixcore.optibienestar360.modules.corporate.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corporate contract (v2 PDF — "Contratos Corporativos", V38). An institution
 * signs one contract against a {@code CORPORATIVO} {@link Plan} and enrolls its
 * people under it — each enrolled person is a full {@code Member} pointing back
 * at this contract, never a beneficiary.
 *
 * <p>Billing hinges on {@link #payerMode}:</p>
 * <ul>
 *   <li>{@link PayerMode#INSTITUTION_BULK} — the institution is billed. Payments
 *       carry {@code corporate_contract_id} and default their payer to
 *       {@link #contactUser} (see {@code CorporateBillingResolver}).</li>
 *   <li>{@link PayerMode#INDIVIDUAL_PAYER} — each member pays their own
 *       membership like any affiliate; the contract is only the grouping.</li>
 * </ul>
 *
 * <p>{@link #contactUser} is optional — the contract may be managed centrally
 * without a dedicated login. {@link #expectedMemberCount} is the planned
 * headcount (nullable); {@link #actualMemberCount} is the running enrolled
 * count, recomputed by the bulk-enroll service from the live member portfolio.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "corporate_contracts")
@AttributeOverride(name = "id", column = @Column(name = "corporate_contracts_id", nullable = false, updatable = false))
public class CorporateContract extends BaseEntity {

    /**
     * The CORPORATIVO plan this contract runs against. The CORPORATIVO type is
     * enforced service-side ({@code corporate_contract.plan.not_corporate}) —
     * the DB CHECK cannot reach across to {@code plans.type}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "institution_name", length = 200, nullable = false)
    private String institutionName;

    /** Institution's tax document (RIF). */
    @Column(name = "institution_tax_id", length = 20, nullable = false)
    private String institutionTaxId;

    /**
     * The institution's point of contact as a platform user (optional). When
     * set, INSTITUTION_BULK payments default their payer to this user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id")
    private User contactUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_mode", length = 20, nullable = false)
    private PayerMode payerMode;

    /** Planned headcount (nullable) vs the running enrolled count below. */
    @Column(name = "expected_member_count")
    private Integer expectedMemberCount;

    @Column(name = "actual_member_count", nullable = false)
    private int actualMemberCount = 0;

    /**
     * Who gets billed for the memberships enrolled under this contract. The V38
     * CHECK constraint pins {@code payer_mode} to exactly these two values.
     */
    public enum PayerMode {
        /** The institution is billed in bulk (payments carry the contract + contact user). */
        INSTITUTION_BULK,
        /** Each member pays their own membership like any individual affiliate. */
        INDIVIDUAL_PAYER
    }
}
