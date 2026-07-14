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
@Table(name = "marital_statuses")
@AttributeOverride(name = "id", column = @Column(name = "marital_statuses_id", nullable = false, updatable = false))
public class MaritalStatus extends BaseAuditEntity {

    @Column(length = 20, unique = true, nullable = false)
    private String code;

    @Column(length = 50, unique = true, nullable = false)
    private String name;
}
