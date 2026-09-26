package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One human decision (D16, hub plan competitive-commission-rules, Fase 2c): resolving an open
 * tie, redirecting a position to another classified promoter, or disqualifying a winner. The
 * evaluation engine reads every non-reverted row as a pin ({@link #promoterId} at {@link
 * #awardPosition}) or exclusion (a {@code DISQUALIFY} row's {@link #promoterId}) on each run, so
 * the job is idempotent and never overrides a coordinator's call.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_manual_decisions")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_manual_decisions_id", nullable = false, updatable = false))
public class CompetitiveCommissionManualDecision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_id", nullable = false)
    private CompetitiveCommissionRule rule;

    /** Set only for {@link Kind#TIE_RESOLUTION}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_tie_id")
    private CompetitiveCommissionTie tie;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private Kind kind;

    /** Required for {@link Kind#REDIRECT}; the position {@link #promoterId} is pinned to. */
    @Column(name = "award_position")
    private Integer awardPosition;

    /** The pinned winner (TIE_RESOLUTION/REDIRECT), or the disqualified promoter (DISQUALIFY). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    /** REDIRECT only — who loses the position. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_promoter_id")
    private Promoter replacedPromoter;

    /** DISQUALIFY only — also excluded from lower-priority rules of the same {@code competition_group}. */
    @Column(name = "exclude_from_group", nullable = false)
    private boolean excludeFromGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", length = 30, nullable = false)
    private ReasonCategory reasonCategory = ReasonCategory.OTHER;

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    @Column(name = "decided_by", nullable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @Column(name = "reverted_by")
    private UUID revertedBy;

    @Column(name = "revert_reason", columnDefinition = "text")
    private String revertReason;

    public enum Kind {
        TIE_RESOLUTION, REDIRECT, DISQUALIFY
    }

    public enum ReasonCategory {
        TIE_BREAK, UNSPORTSMANLIKE_CONDUCT, DATA_ERROR, POLICY, OTHER
    }

    public enum DecisionStatus {
        ACTIVE, STALE, REVERTED
    }
}
