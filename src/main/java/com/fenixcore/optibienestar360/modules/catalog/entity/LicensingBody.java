package com.fenixcore.optibienestar360.modules.catalog.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body that issues a professional license (M.P.P.S., Colegio de Médicos,
 * Inpreabogado…), V213 / hub ADR 0031 §5. A person's licenses live in
 * {@code person_professional_licenses}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "licensing_bodies")
@AttributeOverride(name = "id", column = @Column(name = "licensing_bodies_id", nullable = false, updatable = false))
public class LicensingBody extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 150, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;

    /** Optional hint: which B2P area usually needs this license; {@code null} for any. */
    @Enumerated(EnumType.STRING)
    @Column(name = "professional_area", length = 20)
    private ProfessionalArea professionalArea;
}
