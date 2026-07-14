package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Affiliate-to-affiliate referral relationship (V27). Distinct from the
 * promoter referral flow: this is the existing affiliate ({@link #referrer})
 * sharing their personal {@code members.referral_code} with a friend
 * ({@link #referred}, populated once the friend enrolls). When the
 * friend's first payment is approved, the referrer becomes eligible for
 * a REWARD (discount on next monthly fee).
 *
 * <p>Lifecycle (status, V27 CHECK):</p>
 * <pre>
 *   PENDING_ENROLLMENT  →  REGISTERED   (friend enrolls)
 *                       →  EXPIRED      (no enrollment within TTL)
 *   REGISTERED           →  REWARD_GRANTED  (discount applied to a payment)
 *                        →  VOIDED      (admin reversal)
 * </pre>
 *
 * <p>Reward: {@link #rewardPct} XOR {@link #rewardFlatAmount}, with the
 * both-NULL escape for {@code PENDING_ENROLLMENT} (reward not decided
 * until the friend enrolls). Mirrors the V27 CHECK
 * {@code chk_referrals_reward_pct_xor_flat}.</p>
 *
 * <p>{@code status} is inherited as a {@code String}; the V27 CHECK pins
 * it to {@link ReferralStatus} values.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "referrals")
@AttributeOverride(name = "id", column = @Column(name = "referrals_id", nullable = false, updatable = false))
public class Referral extends BaseEntity {

    // ─── Pair ──────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_member_id", nullable = false)
    private Member referrer;

    /** Populated only once the friend actually enrolls. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_member_id")
    private Member referred;

    /** Snapshot of the code used (immune to later renames). */
    @Column(name = "referral_code", length = 20, nullable = false)
    private String referralCode;

    // ─── Lifecycle dates ───────────────────────────────────────────────────

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    /** {@code null} means "never expires" (admin override). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    // ─── Reward config ─────────────────────────────────────────────────────

    @Column(name = "reward_pct", precision = 5, scale = 2)
    private BigDecimal rewardPct;

    @Column(name = "reward_flat_amount", precision = 10, scale = 2)
    private BigDecimal rewardFlatAmount;

    @Column(name = "reward_currency", length = 3)
    private String rewardCurrency;

    // ─── Reward grant ──────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_payment_id")
    private Payment rewardPayment;

    @Column(name = "reward_granted_at")
    private Instant rewardGrantedAt;

    // ─── Notes ─────────────────────────────────────────────────────────────

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    /** Workflow values pinned by the V27 CHECK on {@link BaseEntity#getStatus()}. */
    public enum ReferralStatus {
        PENDING_ENROLLMENT,
        REGISTERED,
        EXPIRED,
        REWARD_GRANTED,
        VOIDED
    }
}
