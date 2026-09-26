package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An open tie (D16, hub plan competitive-commission-rules, Fase 2c) — the automatic criteria
 * (D6: metric value → achieved_at → transaction count) couldn't break a group of candidates that
 * crosses a reward-tier boundary ({@link com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.OpenTie}),
 * so a coordinator must pick exactly {@link #slots} winners among {@link #candidates}. Only ever
 * created for a {@code tie_policy = MANUAL} rule.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_ties")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_ties_id", nullable = false, updatable = false))
public class CompetitiveCommissionTie extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_id", nullable = false)
    private CompetitiveCommissionRule rule;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** The first position contested by this tie — {@link com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.OpenTie#positionFrom()}. */
    @Column(name = "position_from", nullable = false)
    private int positionFrom;

    /** How many of {@link #candidates} can actually win. */
    @Column(name = "slots", nullable = false)
    private int slots;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @OneToMany(mappedBy = "tie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CompetitiveCommissionTieCandidate> candidates = new ArrayList<>();

    public enum TieStatus {
        OPEN, RESOLVED, STALE
    }
}
