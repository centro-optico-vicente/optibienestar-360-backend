package com.fenixcore.optibienestar360.modules.promoter.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite key for the read-only {@link CommissionPeriodSummary} view — one row
 * per (promoter, period). The view has no natural single-column PK, so JPA maps
 * it via this {@code @IdClass}.
 */
public class CommissionPeriodSummaryId implements Serializable {

    private Long promoterId;
    private String periodStrategy;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    public CommissionPeriodSummaryId() {}

    public CommissionPeriodSummaryId(Long promoterId, String periodStrategy,
                                     LocalDate periodStart, LocalDate periodEnd) {
        this.promoterId = promoterId;
        this.periodStrategy = periodStrategy;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommissionPeriodSummaryId that)) return false;
        return Objects.equals(promoterId, that.promoterId)
                && Objects.equals(periodStrategy, that.periodStrategy)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promoterId, periodStrategy, periodStart, periodEnd);
    }
}
