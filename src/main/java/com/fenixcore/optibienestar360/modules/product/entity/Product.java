package com.fenixcore.optibienestar360.modules.product.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
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

/**
 * Catalog master item — a service (consultation, exam, lodging…) or a
 * product (lenses, medicine, spare parts…) that allies offer (hub ADR 0026).
 *
 * <p>{@link #getOwnerAlly()} {@code null} makes it a <b>general</b> product
 * that any ally may offer through an {@code AllyProduct}; a non-null owner
 * makes it <b>exclusive</b> to that ally. The DB enforces the exclusivity
 * (trigger {@code trg_ally_products_exclusive_owner}, V197); services check it
 * first to answer with a localized error instead of a constraint violation.</p>
 *
 * <p>Per-ally commercial terms (price, discount, exclusions, review and
 * publication) live on the offer, not here.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
@AttributeOverride(name = "id", column = @Column(name = "products_id", nullable = false, updatable = false))
public class Product extends BaseEntity {

    @Column(length = 40, unique = true)
    private String code;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 20, nullable = false)
    private ProductType productType = ProductType.SERVICE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_category_id", nullable = false)
    private ProductCategory productCategory;

    /** {@code null} = general product; set = exclusive to this ally. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_ally_id")
    private Ally ownerAlly;

    @Column(name = "requires_appointment", nullable = false)
    private boolean requiresAppointment = false;

    /**
     * URL of the product's primary image (hub ADR 0027) — the icon / cover lists
     * show. Mirrored from {@code media_items} by the media service; never set by
     * hand.
     */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    // ─── Affiliate ratings (V201) — kept in sync by BenefitUsageRatingsService ─

    /** Average product stars across every ally's offer; {@code null} with no ratings. */
    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    /**
     * Listed in the public cross-ally catalog and product comparison (V199).
     * When {@code false} the product is still public through each ally that
     * publishes its offer (ally page), just not in the general catalog.
     */
    @Column(name = "is_published", nullable = false)
    private boolean published = true;

    @Column(name = "published_at")
    private Instant publishedAt;

    public boolean isExclusive() {
        return ownerAlly != null;
    }

    /** Whether {@code ally} may offer this product: always for a general one, only its owner for an exclusive one. */
    public boolean isOfferableBy(Ally ally) {
        return ownerAlly == null || (ally != null && ownerAlly.getId().equals(ally.getId()));
    }

    public enum ProductType {
        SERVICE, PRODUCT
    }
}
