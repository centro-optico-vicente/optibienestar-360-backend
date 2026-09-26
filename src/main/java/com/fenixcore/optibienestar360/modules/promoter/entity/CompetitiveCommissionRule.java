package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A competitive commission rule (hub plan {@code competitive-commission-rules},
 * Fase 1) — "first one to reach N" or "top N by metric" prizes, as opposed to
 * the 4 pre-existing threshold-based rule types ({@link CommissionTier},
 * {@link CommissionBonusRule}, {@link HierarchyOverrideTier},
 * {@link CollectionCommissionTier}), which this deliberately does NOT extend
 * (D1) — it has fundamentally different semantics (positions, not bands) and
 * a different ledger shape.
 *
 * <p>{@link CompetitionType#RANKING} covers "top N", "escalonado" and
 * "siguientes N" alike — they're just different {@link #positions} ranges on
 * the same rule (D2); there is no separate "TOP_N" type. {@code maxWinners}
 * is deliberately NOT a column: it's derived as {@code max(positionTo)} at
 * the DTO layer (D3), so it can never drift from the positions that define it.</p>
 *
 * <p>The 4 frequency axes ({@link #accrualPeriodStrategy} + the 3 settlement
 * axes) follow the same naming as {@code CommissionTier} et al. (V146-V149,
 * Fase A) and are validated with {@code core.util.SettlementAxes}, but
 * additionally accept {@code END_DATE} on every axis (D8/D14/D15) — new code,
 * so there's no legacy behavior to risk, unlike the 4 pre-existing tables.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_rules")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_rules_id", nullable = false, updatable = false))
public class CompetitiveCommissionRule extends BaseEntity {

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", length = 40, nullable = false)
    private CompetitiveMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "competition_type", length = 20, nullable = false)
    private CompetitionType competitionType;

    /** Count-based threshold — mutually exclusive with {@link #thresholdAmount} (XOR, DB CHECK). */
    @Column(name = "threshold_count")
    private Integer thresholdCount;

    /** Amount-based threshold — mutually exclusive with {@link #thresholdCount} (XOR, DB CHECK). */
    @Column(name = "threshold_amount", precision = 14, scale = 2)
    private BigDecimal thresholdAmount;

    /** Required alongside {@link #thresholdAmount}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_currency_id")
    private Currency thresholdCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_date_basis", length = 20, nullable = false)
    private AchievementDateBasis achievementDateBasis = AchievementDateBasis.APPROVED_AT;

    @Enumerated(EnumType.STRING)
    @Column(name = "tie_policy", length = 20, nullable = false)
    private TiePolicy tiePolicy = TiePolicy.MANUAL;

    /**
     * "Same type of bonus" grouping (D16) — {@code null} = an independent
     * rule. Rules sharing a group must share metric/competitionType/accrual
     * axis+anchor/window (validated in the service — a cross-row invariant
     * no CHECK can express); a promoter who wins the higher-{@link #groupPriority}
     * rule is excluded from the lower-priority ones for the same period.
     */
    @Column(name = "competition_group", length = 60)
    private String competitionGroup;

    /** 1 = the group's top prize. Required together with {@link #competitionGroup} (both or neither). */
    @Column(name = "group_priority")
    private Short groupPriority;

    // ─── Frequency axes (D8/D14/D15) ────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_period_strategy", length = 20, nullable = false)
    private PeriodAxisStrategy accrualPeriodStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "partial_settlement_period_strategy", length = 20, nullable = false)
    private PeriodAxisStrategy partialSettlementPeriodStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_settlement_period_strategy", length = 20, nullable = false)
    private PeriodAxisStrategy finalSettlementPeriodStrategy;

    /** Normalized to a no-op (Fase A, D15) — or forced/disabled per the D14 matrix (RANKING/FIRST_TO_REACH). */
    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_settlement_period_strategy", length = 20, nullable = false)
    private PeriodAxisStrategy retroactiveSettlementPeriodStrategy;

    @Column(name = "accrual_period_anchor")
    private Short accrualPeriodAnchor;

    @Column(name = "partial_settlement_period_anchor")
    private Short partialSettlementPeriodAnchor;

    @Column(name = "final_settlement_period_anchor")
    private Short finalSettlementPeriodAnchor;

    @Column(name = "retroactive_settlement_period_anchor")
    private Short retroactiveSettlementPeriodAnchor;

    /** Days after a period closes before a `PROVISIONAL` award freezes into `PENDING` (D7). */
    @Column(name = "confirmation_delay_days", nullable = false)
    private short confirmationDelayDays = 0;

    /** Optional campaign anchor — {@code null} = a standing (non-campaign) rule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** Own validity window, usually copied from {@link #campaign} when associated; required when any axis is {@code END_DATE}. */
    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "include_system_promoters", nullable = false)
    private boolean includeSystemPromoters = false;

    /** Full-replace child collection (D2's position ranges) — see {@code CompetitiveCommissionRulesService}. */
    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("positionFrom ASC")
    private List<CompetitiveCommissionRulePosition> positions = new ArrayList<>();

    /** Scope (D13) — empty = applies to every promoter type. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "competitive_commission_rule_promoter_types",
            joinColumns = @JoinColumn(name = "competitive_commission_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "promoter_type_id"))
    private Set<PromoterType> promoterTypes = new HashSet<>();

    /** Scope (D13) — empty = applies to every rank. First M:N by rank in the codebase (see class Javadoc). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "competitive_commission_rule_ranks",
            joinColumns = @JoinColumn(name = "competitive_commission_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "rank_id"))
    private Set<PromoterRank> ranks = new HashSet<>();

    /** Metrics the rule can be built on — D4. New metrics are 1 provider bean + this CHECK + i18n. */
    public enum CompetitiveMetric {
        NEW_SUBSCRIBERS, ACTIVE_SUBSCRIBERS, SALES_COUNT, SALES_AMOUNT,
        COLLECTION_COUNT, COLLECTION_AMOUNT, ADVANCE_COUNT, ADVANCE_AMOUNT,
        COMMISSION_EARNED
        // Fase 5 adds OVERDUE_SETTLED_COUNT/AMOUNT.
    }

    /** D2 — only 2 evaluation modes; "top N" and "escalonado" are position configurations, not types. */
    public enum CompetitionType {
        FIRST_TO_REACH, RANKING
    }

    /** D5 — which timestamp on the underlying transaction decides "who got there first" / the metric's as-of date. */
    public enum AchievementDateBasis {
        PAYMENT_DATE, REGISTERED_AT, APPROVED_AT
    }

    /** D6 — tie-break policy. {@code MANUAL} is the default for new rules (D16). */
    public enum TiePolicy {
        STRICT, SHARED_FULL, SHARED_SPLIT, MANUAL
    }

    /**
     * Shared Java type across all 4 frequency axes on this entity — unlike
     * the 4 legacy rule tables, {@code END_DATE} is valid on every one of
     * them here (D8/D14/D15), including accrual.
     */
    public enum PeriodAxisStrategy {
        DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL, END_DATE
    }
}
