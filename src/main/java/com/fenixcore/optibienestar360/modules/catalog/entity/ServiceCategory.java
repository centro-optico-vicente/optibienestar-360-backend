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
@Table(name = "service_categories")
@AttributeOverride(name = "id", column = @Column(name = "service_categories_id", nullable = false, updatable = false))
public class ServiceCategory extends BaseAuditEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;
}
