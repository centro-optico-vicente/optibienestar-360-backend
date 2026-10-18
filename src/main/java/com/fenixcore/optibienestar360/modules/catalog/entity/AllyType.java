package com.fenixcore.optibienestar360.modules.catalog.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_types")
@AttributeOverride(name = "id", column = @Column(name = "ally_types_id", nullable = false, updatable = false))
public class AllyType extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;

    /** Commercial (B2B, legal entity) vs. professional (B2P, independent professional) ally — V179. */
    @Enumerated(EnumType.STRING)
    @Column(length = 3, nullable = false)
    private Segment segment = Segment.B2B;

    /** Only for {@link Segment#B2P}: medical vs. integral-wellness professionals; {@code null} for B2B — V179. */
    @Enumerated(EnumType.STRING)
    @Column(name = "professional_area", length = 20)
    private ProfessionalArea professionalArea;

    public enum Segment { B2B, B2P }

    public enum ProfessionalArea { MEDICAL, WELLNESS }
}
