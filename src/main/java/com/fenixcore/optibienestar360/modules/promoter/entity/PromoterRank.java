package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The "cargo" catalog (V101) — an axis independent of {@link
 * com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType}
 * (Independiente/Empleado/Aliado). Seed: PROMOTOR(1)/SUPERVISOR(2)/
 * COORDINADOR(3), but the engine is N-level-capable — every comparison
 * ("is this candidate supervisor's rank strictly above the subordinate's?")
 * reads {@link #hierarchyLevel}, never {@link #code}/{@link #name}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_ranks")
@AttributeOverride(name = "id", column = @Column(name = "promoter_ranks_id", nullable = false, updatable = false))
public class PromoterRank extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(name = "hierarchy_level", unique = true, nullable = false)
    private int hierarchyLevel;

    /** Configurable cap on direct subordinates for this rank; {@code null} = no cap. */
    @Column(name = "max_subordinates")
    private Integer maxSubordinates;

    @Column(length = 200)
    private String description;
}
