package com.fenixcore.optibienestar360.modules.system.entity;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

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
     * Global overrides above the per-entity {@code entity_config} flags — see
     * {@link AuditMode} and spec 16-audit.md, Decisión 8. Split per action
     * (V83) so, e.g., deletes can be force-disabled globally without also
     * forcing creates/updates.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_create_mode", nullable = false, length = 20)
    private AuditMode auditCreateMode = AuditMode.PER_ENTITY;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_update_mode", nullable = false, length = 20)
    private AuditMode auditUpdateMode = AuditMode.PER_ENTITY;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_delete_mode", nullable = false, length = 20)
    private AuditMode auditDeleteMode = AuditMode.PER_ENTITY;

    /**
     * Global override for {@code entity_config.capture_before_after} (V83) —
     * previously only configurable per entity, with no way to force it on/off
     * system-wide.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "capture_before_after_mode", nullable = false, length = 20)
    private AuditMode captureBeforeAfterMode = AuditMode.PER_ENTITY;

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

	/**
	 * Global fallback default sort (V82) — applied by a list endpoint when
	 * the entity has no {@code entity_config} row (or none with its own
	 * {@code default_sort}) at all. Restricted at the service layer to
	 * {@link com.fenixcore.optibienestar360.core.util.CommonSortFields},
	 * since (unlike {@code entity_config.default_sort}) it isn't checked
	 * against any single entity's sortable-fields map. {@code null} means
	 * "no global default configured" — callers fall back to
	 * {@code createdAt DESC} in code.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "default_sort", columnDefinition = "jsonb")
	private List<SortOrder> defaultSort;

}
