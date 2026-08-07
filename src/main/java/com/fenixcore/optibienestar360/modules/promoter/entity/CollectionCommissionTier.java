package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Collection commission tier (ADR 0013 §3, V44) — decreasing % by how many
 * days it took to collect the recurring (MONTHLY) payment.
 *
 * <p>A tier applies when the payment's days-to-collect is {@code <= maxDays};
 * the engine picks the smallest qualifying {@link #maxDays} bucket. Orthogonal
 * to {@link CommissionTier}, which scopes by monthly new-subscriber volume.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "collection_commission_tiers")
@AttributeOverride(name = "id", column = @Column(name = "collection_commission_tiers_id", nullable = false, updatable = false))
public class CollectionCommissionTier extends BaseEntity {

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "max_days", nullable = false)
    private int maxDays;

    @Column(name = "commission_pct", precision = 5, scale = 2, nullable = false)
    private BigDecimal commissionPct;
}
