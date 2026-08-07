package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
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

/**
 * Configurable commission tier (v2 PDF #5, V42) — the DB-driven replacement of
 * the hardcoded plan-type switch that {@code CommissionService} used in v1.
 *
 * <p>A tier qualifies for a promoter when their <b>new-subscriber count</b>
 * within the tier's {@link #periodStrategy} window reaches {@link #thresholdCount}
 * ({@code 0} = base tier, always qualifies). The engine applies the highest
 * qualifying tier among those matching the payment's plan and fee type.</p>
 *
 * <ul>
 *   <li>{@link #planType} — optional scope; {@code null} = applies to every plan
 *       type. The v1 per-plan rates ship as plan-scoped base tiers (threshold 0).</li>
 *   <li>{@link #commissionPct} XOR {@link #flatAmount} — exactly one (V42 CHECK),
 *       same shape as the {@link Commission} snapshot.</li>
 *   <li>{@link #appliesTo} — INSCRIPTION / MONTHLY / BOTH (the payment's fee type
 *       must match, or the tier must be BOTH).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_tiers")
@AttributeOverride(name = "id", column = @Column(name = "commission_tiers_id", nullable = false, updatable = false))
public class CommissionTier extends BaseEntity {

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    /** Optional plan scope. {@code null} = applies to every plan type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 20)
    private PlanType planType;

    /** Optional promoter-type scope. {@code null} = applies to every promoter type. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_type_id")
    private PromoterType promoterType;

    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount = 0;

    @Column(name = "commission_pct", precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private PeriodStrategy periodStrategy = PeriodStrategy.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 20, nullable = false)
    private AppliesTo appliesTo = AppliesTo.BOTH;

    /** Which fee type the tier applies to. {@code BOTH} matches inscription and monthly. */
    public enum AppliesTo {
        INSCRIPTION, MONTHLY, BOTH
    }
}
