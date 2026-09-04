package com.fenixcore.optibienestar360.modules.promoter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection over the {@code commission_period_summary} DB view (V42) —
 * the per (promoter, period) commission aggregate that powers the leaderboard.
 * Mapped {@link Immutable}; never written by the app.
 */
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "commission_period_summary")
@IdClass(CommissionPeriodSummaryId.class)
public class CommissionPeriodSummary {

    @Id
    @Column(name = "promoter_id")
    private Long promoterId;

    @Id
    @Column(name = "period_strategy", length = 20)
    private String periodStrategy;

    @Id
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Id
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "commission_count")
    private long commissionCount;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency_id")
    private Long currencyId;
}
