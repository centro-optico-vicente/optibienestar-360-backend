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
@Table(name = "occupations")
@AttributeOverride(name = "id", column = @Column(name = "occupations_id", nullable = false, updatable = false))
public class Occupation extends BaseAuditEntity {

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;
}
