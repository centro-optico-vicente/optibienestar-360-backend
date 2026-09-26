package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
 * One position range + prize on a {@link CompetitiveCommissionRule} (D2/D3) —
 * e.g. "1st place: $100 flat" or "2nd-5th: 5% of the metric, min $10 max $50".
 * A rule with several non-overlapping ranges (validated in the service, not
 * a DB EXCLUDE — see {@code CompetitiveCommissionRulesService}) is how
 * "escalonado" and "siguientes N" are modeled — there is no separate type
 * for them (D2). {@code min_threshold_*} only applies to RANKING positions
 * (D12): if the occupant doesn't clear it, the position is left vacant.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_rule_positions")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_rule_positions_id", nullable = false, updatable = false))
public class CompetitiveCommissionRulePosition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_id", nullable = false)
    private CompetitiveCommissionRule rule;

    @Column(name = "position_from", nullable = false)
    private int positionFrom;

    @Column(name = "position_to", nullable = false)
    private int positionTo;

    @Column(name = "label", length = 80)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", length = 20, nullable = false)
    private RewardType rewardType;

    /** Only set alongside {@link RewardType#FLAT} (XOR with {@link #rewardPct}, DB CHECK). */
    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /** Only set alongside {@link RewardType#PERCENTAGE} (XOR with {@link #flatAmount}, DB CHECK). */
    @Column(name = "reward_pct", precision = 5, scale = 2)
    private BigDecimal rewardPct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_currency_id", nullable = false)
    private Currency rewardCurrency;

    /** PERCENTAGE only. */
    @Column(name = "reward_min_amount", precision = 10, scale = 2)
    private BigDecimal rewardMinAmount;

    /** PERCENTAGE only. */
    @Column(name = "reward_max_amount", precision = 10, scale = 2)
    private BigDecimal rewardMaxAmount;

    /** RANKING only (D12) — the occupant must clear this or the position is left vacant. */
    @Column(name = "min_threshold_count")
    private Integer minThresholdCount;

    /** RANKING only (D12); currency is the rule's own {@code thresholdCurrency}. */
    @Column(name = "min_threshold_amount", precision = 14, scale = 2)
    private BigDecimal minThresholdAmount;

    /** Money or percentage. Exactly one of {@code flatAmount} / {@code rewardPct} is set. */
    public enum RewardType {
        FLAT, PERCENTAGE
    }
}
