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
@Table(name = "permission_domains")
@AttributeOverride(name = "id", column = @Column(name = "permission_domains_id", nullable = false, updatable = false))
public class PermissionDomain extends BaseAuditEntity {

    @Column(length = 50, unique = true, nullable = false)
    private String code;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 60)
    private String icon;

    @Column(length = 255)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
