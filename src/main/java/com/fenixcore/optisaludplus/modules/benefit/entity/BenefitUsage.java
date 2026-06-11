package com.fenixcore.optisaludplus.modules.benefit.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyService;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyUser;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * One audit row per benefit consumption at an ally counter (V24). The
 * record is created AFTER the ally has confirmed the affiliate is solvent
 * via the realtime validator endpoint — so existence of a row implies the
 * solvency check passed at the moment of registration.
 *
 * <p>Status workflow:</p>
 * <pre>
 *   REGISTERED → REVERSED  (admin or ally undoes a duplicate / mistake)
 *              → DISPUTED  (affiliate flags it; under review)
 * </pre>
 *
 * <p>{@code status} is inherited from {@link BaseEntity} as a {@code String}
 * — same pattern {@code Membership.LifecycleStatus} and {@code
 * Payment.PaymentStatus} use. Service callers reach for the inner
 * {@link UsageStatus} enum for type-safety:
 * <pre>
 *   usage.setStatus(BenefitUsage.UsageStatus.REVERSED.name());
 * </pre>
 * The V24 CHECK constraint pins the column to exactly the three values.</p>
 *
 * <p>{@code metadata} is the per-ally-type structured payload (clinic
 * doctor + diagnosis, optical prescription details, pharmacy boxes, etc.)
 * — JSONB on the DB side via {@code @JdbcTypeCode(SqlTypes.JSON)}.
 * Different ally types pick their own keys without a table-per-type
 * explosion.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "benefit_usages")
@AttributeOverride(name = "id", column = @Column(name = "benefit_usages_id", nullable = false, updatable = false))
public class BenefitUsage extends BaseEntity {

    // ─── Subject ───────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    // ─── Where + what ──────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    /**
     * Specific catalogued service the affiliate consumed. {@code null} when
     * the usage doesn't map to a row in {@code ally_services} (generic
     * pharmacy discount, unstructured consultation, etc.).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_service_id")
    private AllyService allyService;

    /**
     * The ally-side operator who registered the row. {@code null} for
     * admin-side or back-fill flows. Distinct from {@code created_by} (the
     * authenticated user that hit the endpoint) — usually the same, but
     * separable when an admin acts on the ally's behalf.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_user_id")
    private AllyUser allyUser;

    // ─── When ──────────────────────────────────────────────────────────────

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate = LocalDate.now();

    @Column(name = "usage_datetime", nullable = false)
    private Instant usageDatetime = Instant.now();

    // ─── Money (co-pay charged at the counter) ─────────────────────────────

    @Column(name = "copay_amount", precision = 10, scale = 2)
    private BigDecimal copayAmount;

    @Column(name = "copay_currency", length = 3)
    private String copayCurrency;

    // ─── Detail ────────────────────────────────────────────────────────────

    /**
     * Per-ally-type structured payload. Persisted as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(columnDefinition = "text")
    private String notes;

    /** Workflow values that may land in {@link BaseEntity#getStatus()}. */
    public enum UsageStatus {
        REGISTERED, REVERSED, DISPUTED
    }
}
