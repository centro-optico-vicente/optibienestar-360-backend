package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
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
import java.math.RoundingMode;
import java.time.Instant;

/**
 * What an {@link Ally} offers of one catalog {@link Product} (hub ADR 0026):
 * the per-ally commercial terms — price + currency, preferential discount,
 * exclusions — plus the review workflow and publication. The product itself
 * (name, type, category, image) lives on the master; an exclusive product can
 * only be offered by its owner ally. Goes through the approval workflow:
 * <pre>
 *   PROPOSED → IN_REVIEW → APPROVED → REMOVED
 *                      ↘   ↑
 *                        REJECTED (resubmittable)
 * </pre>
 *
 * <p>Only appears in the public directory when {@link #isPublished()} +
 * {@link #getReviewStatus()} == {@link ReviewStatus#APPROVED} (the DB
 * CHECK constraint enforces this invariant).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_products")
@AttributeOverride(name = "id", column = @Column(name = "ally_products_id", nullable = false, updatable = false))
public class AllyProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "price_amount", precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_currency_id")
    private Currency priceCurrency;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    /** Operational exclusions of the preferential rate, free text (V179). */
    @Column(columnDefinition = "text")
    private String exclusions;

    // ─── Review workflow ────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PROPOSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Contextual text for the last transition into {@code REJECTED} or {@code REMOVED}. */
    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    // ─── Directory publishing ───────────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Price the affiliate actually pays: {@code priceAmount × (1 − discountPct/100)},
     * rounded to cents. {@code null} without a reference price; no discount means
     * the full price. Derived, never persisted — public lists and the product
     * detail all show this same number (hub ADR 0026).
     */
    public BigDecimal getFinalPrice() {
        if (priceAmount == null) {
            return null;
        }
        if (discountPct == null || discountPct.signum() == 0) {
            return priceAmount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.subtract(discountPct.movePointLeft(2));
        return priceAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public enum ReviewStatus {
        PROPOSED, IN_REVIEW, APPROVED, REJECTED, REMOVED
    }
}
