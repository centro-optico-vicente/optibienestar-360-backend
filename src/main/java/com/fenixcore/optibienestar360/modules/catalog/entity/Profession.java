package com.fenixcore.optibienestar360.modules.catalog.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Discipline of a person (Traumatología, Psicología, Derecho). A sub-specialty
 * is a child profession ({@code parent_id}, V213 / hub ADR 0031 §6): at most
 * two levels, enforced by {@code ProfessionService}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "professions")
@AttributeOverride(name = "id", column = @Column(name = "professions_id", nullable = false, updatable = false))
public class Profession extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;

    /** General profession of a sub-specialty; {@code null} for a top-level profession. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Profession parent;
}
