package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Prize configured for a leaderboard {@link #rank} under a given
 * {@link #periodStrategy} (v2 PDF #5, V42). E.g. 1st place monthly → $100. One
 * active prize per (rank, strategy). Consumed by {@code LeaderboardPrizeService}
 * at period close to grant awards to the top promoters.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leaderboard_prizes")
@AttributeOverride(name = "id", column = @Column(name = "leaderboard_prizes_id", nullable = false, updatable = false))
public class LeaderboardPrize extends BaseEntity {

    @Column(name = "rank", nullable = false)
    private int rank;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private PeriodStrategy periodStrategy;

    @Column(name = "prize_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal prizeAmount;

    @Column(name = "prize_currency", length = 3, nullable = false)
    private String prizeCurrency = "USD";
}
