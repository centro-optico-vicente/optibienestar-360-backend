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

    /** How the preferential rate is expressed (V210, hub ADR 0031 §8). Set through {@link #applyDiscount}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    /** Percentage off, 0–100; only for {@link DiscountType#PERCENTAGE} ({@code null} = no discount). */
    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    /** Amount off in the price currency; only for {@link DiscountType#FLAT} (V210). */
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /** Operational exclusions of the preferential rate, free text (V179). */
    @Column(columnDefinition = "text")
    private String exclusions;

    /**
     * URL of the offer's own primary image (hub ADR 0027), mirrored from
     * {@code media_items}; {@code null} means "use the product's" — see
     * {@link #getEffectiveLogoUrl()}. Never set by hand.
     */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** The offer's image if it has one, else its product's. */
    public String getEffectiveLogoUrl() {
        return logoUrl != null ? logoUrl : (product == null ? null : product.getLogoUrl());
    }

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
     * Price the affiliate actually pays, rounded to cents: {@code priceAmount −
     * discountAmount} for {@link DiscountType#FLAT}, {@code priceAmount × (1 −
     * discountPct/100)} for {@link DiscountType#PERCENTAGE}. {@code null}
     * without a reference price; no discount means the full price. Derived,
     * never persisted — public lists and the product detail all show this
     * same number (hub ADR 0026).
     */
    public BigDecimal getFinalPrice() {
        if (priceAmount == null) {
            return null;
        }
        if (discountType == DiscountType.FLAT) {
            BigDecimal off = discountAmount == null ? BigDecimal.ZERO : discountAmount;
            return priceAmount.subtract(off).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        if (discountPct == null || discountPct.signum() == 0) {
            return priceAmount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.subtract(discountPct.movePointLeft(2));
        return priceAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The discount as a percentage, whatever its type — only for display and
     * ordering ("up to 20 % off"). PERCENTAGE returns {@code discountPct};
     * FLAT returns {@code discountAmount / priceAmount × 100}, two decimals.
     * {@code null} when there is no discount or (FLAT) no positive price.
     */
    public BigDecimal getEffectiveDiscountPct() {
        if (discountType != DiscountType.FLAT) {
            return discountPct;
        }
        if (discountAmount == null || priceAmount == null || priceAmount.signum() <= 0) {
            return null;
        }
        return discountAmount.movePointRight(2).divide(priceAmount, 2, RoundingMode.HALF_UP);
    }

    /**
     * Sets the preferential rate, mirroring {@code chk_ally_products_discount_coherence}
     * (V210) so a bad request gets a readable 422 instead of a 409. Call it
     * after the price is set: a FLAT amount needs a price and cannot exceed it.
     *
     * @param type   {@code null} = PERCENTAGE (the shape every caller had before V210)
     * @param pct    percentage off; ignored for FLAT
     * @param amount amount off in the price currency; ignored for PERCENTAGE
     * @throws IllegalArgumentException {@code ally_product.discount.flat_requires_amount},
     *         {@code ally_product.discount.flat_requires_price} or
     *         {@code ally_product.discount.exceeds_price}
     */
    public void applyDiscount(DiscountType type, BigDecimal pct, BigDecimal amount) {
        DiscountType resolved = type == null ? DiscountType.PERCENTAGE : type;
        if (resolved == DiscountType.FLAT) {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("ally_product.discount.flat_requires_amount");
            }
            if (priceAmount == null) {
                throw new IllegalArgumentException("ally_product.discount.flat_requires_price");
            }
            if (amount.compareTo(priceAmount) > 0) {
                throw new IllegalArgumentException("ally_product.discount.exceeds_price");
            }
            this.discountType = DiscountType.FLAT;
            this.discountAmount = amount;
            this.discountPct = null;
        } else {
            this.discountType = DiscountType.PERCENTAGE;
            this.discountPct = pct;
            this.discountAmount = null;
        }
    }

    /** How the preferential rate is expressed (V210). */
    public enum DiscountType {
        FLAT, PERCENTAGE
    }

    public enum ReviewStatus {
        PROPOSED, IN_REVIEW, APPROVED, REJECTED, REMOVED
    }
}
