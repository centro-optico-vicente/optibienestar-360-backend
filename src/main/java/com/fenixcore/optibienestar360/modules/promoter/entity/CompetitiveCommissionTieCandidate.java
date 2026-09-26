package com.fenixcore.optibienestar360.modules.promoter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One promoter contesting a {@link CompetitiveCommissionTie} — a frozen snapshot of their standing. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_tie_candidates")
@IdClass(CompetitiveCommissionTieCandidateId.class)
public class CompetitiveCommissionTieCandidate {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_tie_id", nullable = false)
    private CompetitiveCommissionTie tie;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Column(name = "metric_value", precision = 14, scale = 2, nullable = false)
    private BigDecimal metricValue;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "metric_transaction_count", nullable = false)
    private int metricTransactionCount;

    @Column(name = "selected", nullable = false)
    private boolean selected;
}
