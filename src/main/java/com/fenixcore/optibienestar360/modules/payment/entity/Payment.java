package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Manual payment record (V23). Captures the registration + review chain of
 * a payment made outside the platform (bank transfer, Zelle, cash, etc.).
 * Money never flows through the platform — admins review the proof of
 * payment uploaded by the affiliate and either approve or reject the row.
 *
 * <p>Lifecycle (V23 CHECK constraint pins {@code status} to these three):</p>
 * <pre>
 *   PENDING → APPROVED   (admin accepts the proof)
 *           → REJECTED   (admin rejects; review_reason becomes mandatory)
 * </pre>
 *
 * <p>Allocation semantics:</p>
 * <ul>
 *   <li>{@link #inscription} {@code = true} — one-time inscription fee (the
 *       member's first enrollment, or a beneficiary "extra" per
 *       {@code beneficiaries.extra_inscription_paid} from V18). The V23
 *       deferred FK
 *       {@code beneficiaries.inscription_payment_id → payments(payments_id)}
 *       points back to such a row. {@link #appliedPeriod} stays
 *       {@code null} (enforced by V23 CHECK).</li>
 *   <li>{@link #inscription} {@code = false} — recurring monthly fee.
 *       {@link #appliedPeriod} is the first day of the covered month
 *       (e.g. {@code 2026-06-01} covers June 2026).</li>
 * </ul>
 *
 * <p>Currency stays USD by default per the program's pricing (ADR 0008
 * commercial). VES amounts paid locally are recorded as the actual money
 * received; the USD equivalence stays out-of-band until v2 introduces FX
 * rates.</p>
 *
 * <p>{@code status} is inherited from {@link BaseEntity} as a {@code String}
 * — same pattern {@code Membership} uses for its lifecycle status. Service
 * callers use the inner {@link PaymentStatus} enum for type safety:
 * {@code payment.setStatus(Payment.PaymentStatus.APPROVED.name())}. The V23
 * CHECK constraint guarantees only the three enum values land in the
 * column at the DB level.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
@AttributeOverride(name = "id", column = @Column(name = "payments_id", nullable = false, updatable = false))
public class Payment extends BaseEntity {

    // ─── Subject ───────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    /**
     * The user who actually paid. Typically the member's user account; can
     * differ for corporate-contract payments (v2) or when a relative pays
     * for the member. {@code null} when paid in cash by a non-user at the
     * counter.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_user_id")
    private User payerUser;

    /**
     * Set only for INSTITUTION_BULK corporate payments (V38) — the money is
     * billed to the contract rather than to the individual member.
     * {@code null} for ordinary affiliate payments and for INDIVIDUAL_PAYER
     * corporate members (who pay like any affiliate). Populated by
     * {@code CorporateBillingResolver} at registration time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_contract_id")
    private CorporateContract corporateContract;

    // ─── Money ─────────────────────────────────────────────────────────────

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    // ─── Method + reference ────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_number", length = 80)
    private String referenceNumber;

    // ─── Dates ─────────────────────────────────────────────────────────────

    /** Calendar date the customer's bank settled the payment. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** When the system recorded the registration (defaults to now() at the DB). */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    // ─── Allocation ────────────────────────────────────────────────────────

    @Column(nullable = false)
    private boolean inscription = false;

    /**
     * First day of the month covered by a recurring payment
     * ({@code 2026-06-01} = June 2026). {@code null} for inscription rows
     * (V23 CHECK enforces this).
     */
    @Column(name = "applied_period")
    private LocalDate appliedPeriod;

    // ─── Proof of payment (uploaded to R2) ─────────────────────────────────

    @Column(name = "support_file_url", length = 500)
    private String supportFileUrl;

    @Column(name = "support_file_name", length = 255)
    private String supportFileName;

    @Column(name = "support_file_content_type", length = 100)
    private String supportFileContentType;

    @Column(name = "support_file_size_bytes")
    private Long supportFileSizeBytes;

    // ─── Admin notes ───────────────────────────────────────────────────────

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    // ─── Review workflow ───────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Required when {@link #getStatus()} is {@code REJECTED} (V23 CHECK
     * {@code chk_payments_rejection_has_reason}) — the affiliate needs to
     * know why so they can re-submit. Also doubles as free-form approval
     * notes when the admin wants to record a justification.
     */
    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    // ─── One-off discount (V41, permission ALLOWS_DISCOUNT) ────────────────

    /**
     * One-time discount applied to this single PENDING payment (distinct from a
     * recurring {@code Subsidy}). {@code null} = no discount. Audited inline:
     * {@link #discountReason} / {@link #discountedBy} / {@link #discountedAt} are
     * all required together (V41 CHECK {@code chk_payments_discount_coherence});
     * the amount cannot exceed {@link #amount} (V41 CHECK).
     */
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "discount_reason", columnDefinition = "text")
    private String discountReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discounted_by")
    private User discountedBy;

    @Column(name = "discounted_at")
    private Instant discountedAt;

    // ─── Inner enums (V23 CHECK constraint values) ─────────────────────────

    /**
     * The 7 payment methods seeded in the V23 CHECK constraint. Covers the
     * VE market: bank transfer (local), cash at counter, Zelle (USD),
     * Pago Móvil, crypto (USDT / Binance), international transfer
     * (SWIFT / Wise), or other.
     */
    public enum PaymentMethod {
        BANK_TRANSFER,
        CASH,
        ZELLE,
        PAGO_MOVIL,
        CRYPTO,
        INTERNATIONAL_TRANSFER,
        OTHER
    }

    /**
     * Workflow status values that may land in {@link BaseEntity#getStatus()}.
     * Defined as an enum to keep service callers type-safe — the column
     * itself is a {@code VARCHAR(50)} with a CHECK constraint pinning it
     * to exactly these three (see V23).
     */
    public enum PaymentStatus {
        PENDING, APPROVED, REJECTED
    }
}
