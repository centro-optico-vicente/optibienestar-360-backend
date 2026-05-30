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
@Table(name = "genders")
@AttributeOverride(name = "id", column = @Column(name = "genders_id", nullable = false, updatable = false))
public class Gender extends BaseAuditEntity {

    @Column(length = 1, unique = true, nullable = false)
    private String code;

    @Column(length = 20, unique = true, nullable = false)
    private String name;
}
