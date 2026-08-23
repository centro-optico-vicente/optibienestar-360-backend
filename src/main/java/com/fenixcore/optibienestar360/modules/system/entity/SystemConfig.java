package com.fenixcore.optibienestar360.modules.system.entity;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * Global override above the per-entity {@code audit_entity_config} — see
     * {@link AuditMode} and spec 16-audit.md, Decisión 8.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_change_audit_mode", nullable = false, length = 20)
    private AuditMode dataChangeAuditMode = AuditMode.PER_ENTITY;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_audit_mode", nullable = false, length = 20)
    private AuditMode reportAuditMode = AuditMode.PER_ENTITY;

    /**
     * Plain on/off for {@code login_audit_log} — it has no per-entity equivalent
     * to override, so it does not need the 3-way {@link AuditMode}.
     */
    @Column(name = "login_audit_enabled", nullable = false)
    private boolean loginAuditEnabled = true;

	/**
	 * How long a {@code login_audit_log} session stays {@code ACTIVE} before
	 * {@code LoginSessionSweepJob} expires it — independent from
	 * {@code jwt.refresh-expiration-days} (the JWT's own TTL). Configurable
	 * without redeploy, unlike the JWT property.
	 */
	@Column(name = "login_session_expiration_days", nullable = false)
	private int loginSessionExpirationDays = 30;

}
