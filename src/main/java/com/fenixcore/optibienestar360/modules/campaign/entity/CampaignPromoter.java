package com.fenixcore.optibienestar360.modules.campaign.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bridge row (V120) — a {@link Promoter} explicitly included/excluded by a
 * {@link Campaign} whose {@link Campaign#getScope()} is {@code INCLUDE} or
 * {@code EXCLUDE}. Meaningless (and unused) when {@code scope = ALL}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campaign_promoters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "promoter_id"}))
@AttributeOverride(name = "id", column = @Column(name = "campaign_promoters_id", nullable = false, updatable = false))
public class CampaignPromoter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;
}
