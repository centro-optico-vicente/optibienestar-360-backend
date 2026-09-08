package com.fenixcore.optibienestar360.modules.catalog.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_types")
@AttributeOverride(name = "id", column = @Column(name = "promoter_types_id", nullable = false, updatable = false))
public class PromoterType extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;

    /**
     * V103 — whether a commission earned by a promoter of this type cascades
     * a hierarchy override up to their supervisor/coordinador chain (V102).
     * Default {@code true} (unchanged behavior); an escape hatch for the
     * still-unconfirmed "are Independientes exempt?" business question (hub
     * notes pregunta 7) without hardcoding a promoter-type check in the
     * cascade engine.
     */
    @Column(name = "generates_hierarchy_override", nullable = false)
    private boolean generatesHierarchyOverride = true;
}
