package com.fenixcore.optibienestar360.modules.member.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Family member covered under a parent {@link Member}'s plan. N:1 with
 * Member, 1:1 with {@link Person} — same person can be a beneficiary of
 * multiple titulares in distinct plans (e.g. a child whose divorced parents
 * each maintain their own subscription).
 *
 * <p>The UNIQUE (member_id, person_id) constraint on V18 prevents a person
 * appearing twice under the same titular — readmission is handled by
 * reactivating the existing row, not inserting a new one.</p>
 *
 * <p>{@code extraInscriptionPaid} + {@code inscriptionPaymentId} (v2)
 * track the one-time inscription fee triggered when a member exceeds the
 * plan's {@code included_beneficiaries} cap. The columns ship since V18; the
 * FK constraint to {@code payments} was wired by V23 once that table existed.
 * The entity still exposes the reference as a plain Long (no {@code @ManyToOne})
 * to keep the member module from depending on the payment module.</p>
 *
 * <p>{@code slotType} (V178) records which family-nucleus slot the row
 * occupies: {@link SlotType#BASE} (within {@code plan.included_beneficiaries})
 * or {@link SlotType#EXTRA} (between included and
 * {@code plan.max_beneficiaries}, billed with the extra inscription fee). It is
 * assigned when the beneficiary is added or reactivated.</p>
 *
 * <p>{@code user} + {@code accessInvitedAt} (V195) back "beneficiary access":
 * the beneficiary's own AFILIADO login, granted by the holder or staff (see
 * {@code BeneficiaryAccessService}). The link survives a revoke (the user is
 * only deactivated), so granting again reactivates the same account.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "beneficiaries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "person_id"}))
@AttributeOverride(name = "id", column = @Column(name = "beneficiaries_id", nullable = false, updatable = false))
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Relationship relationship;

    @Column(name = "extra_inscription_paid", nullable = false)
    private boolean extraInscriptionPaid = false;

    /**
     * Reference to the payments table. Kept as a plain Long rather than a
     * {@code @ManyToOne} so the member module doesn't depend on the payment
     * module; the DB-level FK constraint was added by V23.
     */
    @Column(name = "inscription_payment_id")
    private Long inscriptionPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", length = 10, nullable = false)
    private SlotType slotType = SlotType.BASE;

    /** The beneficiary's own login (V195), or {@code null} if access was never granted. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /** Last time the activation email was sent (V195). */
    @Column(name = "access_invited_at")
    private Instant accessInvitedAt;

    public enum Relationship {
        SPOUSE, CHILD, PARENT, SIBLING, OTHER
    }

    /** Family-nucleus slot: BASE = included in the plan, EXTRA = paid extension. */
    public enum SlotType {
        BASE, EXTRA
    }
}
