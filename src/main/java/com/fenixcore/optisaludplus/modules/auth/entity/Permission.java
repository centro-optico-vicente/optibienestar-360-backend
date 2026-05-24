package com.fenixcore.optisaludplus.modules.auth.entity;

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
@Table(name = "permissions")
@AttributeOverride(name = "id", column = @Column(name = "permissions_id", nullable = false, updatable = false))
public class Permission extends BaseAuditEntity {

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 50, nullable = false)
    private String domain;

    @Column(length = 200)
    private String description;
}
