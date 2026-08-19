package com.fenixcore.optibienestar360.modules.system.entity;

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
@Table(name = "system_configs")
@AttributeOverride(name = "id", column = @Column(name = "system_configs_id", nullable = false, updatable = false))
public class SystemConfig extends BaseAuditEntity {

    @Column(name = "report_footer", columnDefinition = "TEXT")
    private String reportFooter;
}
