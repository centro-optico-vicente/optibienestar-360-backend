package com.fenixcore.optisaludplus.modules.ally.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.catalog.entity.ServiceCategory;
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
 * Servicio puntual ofrecido por un {@link Ally} (consulta, examen, descuento
 * en óptica, etc.). Pasa por el workflow de aprobación:
 * <pre>
 *   PROPOSED → IN_REVIEW → APPROVED → REMOVED
 *                      ↘   ↑
 *                        REJECTED (resubmittable)
 * </pre>
 *
 * <p>Solo aparece en el directorio público cuando {@link #isPublished()} +
 * {@link #getReviewStatus()} == {@link ReviewStatus#APPROVED} (CHECK
 * constraint en DB lo garantiza).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_services")
@AttributeOverride(name = "id", column = @Column(name = "ally_services_id", nullable = false, updatable = false))
public class AllyService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_category_id", nullable = false)
    private ServiceCategory serviceCategory;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_usd", precision = 10, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    @Column(name = "requires_appointment", nullable = false)
    private boolean requiresAppointment = false;

    // ─── Review workflow ───────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PROPOSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Texto contextual del último cambio a {@code REJECTED} o {@code REMOVED}. */
    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    // ─── Publicación en directorio ─────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    public enum ReviewStatus {
        PROPOSED, IN_REVIEW, APPROVED, REJECTED, REMOVED
    }
}
