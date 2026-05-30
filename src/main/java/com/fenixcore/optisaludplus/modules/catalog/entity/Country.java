package com.fenixcore.optisaludplus.modules.catalog.entity;

import com.fenixcore.optisaludplus.core.entity.BaseAuditEntity;
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
@Table(name = "countries")
@AttributeOverride(name = "id", column = @Column(name = "countries_id", nullable = false, updatable = false))
public class Country extends BaseAuditEntity {

    @Column(name = "iso_code", length = 2, unique = true, nullable = false)
    private String isoCode;

    @Column(length = 100, unique = true, nullable = false)
    private String name;
}
