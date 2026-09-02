package com.fenixcore.optibienestar360.modules.system.service;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.core.util.CommonSortFields;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.system.dto.UpdateSystemConfigRequest;
import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {

    public static final String DEFAULT_REPORT_FOOTER = "Creado desde compañía de ejemplo";

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /**
     * Gets the active SystemConfig singleton instance or creates a new empty instance.
     */
    @Transactional(readOnly = true)
    public SystemConfig getSystemConfig() {
        return systemConfigRepository.findFirstByActiveTrue()
                .orElseGet(SystemConfig::new);
    }

    /**
     * Retrieves the current report footer text, returning null/empty if not configured.
     */
    @Transactional(readOnly = true)
    public String getReportFooter() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getReportFooter)
                .orElse(null);
    }

    /**
     * Resolves the global override for CREATE data-change auditing (spec
     * 16-audit.md, Decisión 8; split per action in V83). Fail-safe: falls
     * back to {@link AuditMode#PER_ENTITY} if the singleton row is missing.
     */
    @Transactional(readOnly = true)
    public AuditMode getAuditCreateMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getAuditCreateMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Resolves the global override for UPDATE data-change auditing. Same
     * fail-safe fallback as {@link #getAuditCreateMode()}.
     */
    @Transactional(readOnly = true)
    public AuditMode getAuditUpdateMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getAuditUpdateMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Resolves the global override for DELETE data-change auditing. Same
     * fail-safe fallback as {@link #getAuditCreateMode()}.
     */
    @Transactional(readOnly = true)
    public AuditMode getAuditDeleteMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getAuditDeleteMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Resolves the global override for {@code entity_config.capture_before_after}
     * (V83) — previously only configurable per entity. Same fail-safe
     * fallback as {@link #getAuditCreateMode()}.
     */
    @Transactional(readOnly = true)
    public AuditMode getCaptureBeforeAfterMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getCaptureBeforeAfterMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Resolves the global override for report-generation auditing. Same
     * fail-safe fallback as {@link #getAuditCreateMode()}.
     */
    @Transactional(readOnly = true)
    public AuditMode getReportAuditMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getReportAuditMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Whether login attempts should be recorded in {@code login_audit_log}.
     * Fail-safe: defaults to {@code true} if the singleton row is missing.
     */
    @Transactional(readOnly = true)
    public boolean isLoginAuditEnabled() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::isLoginAuditEnabled)
                .orElse(true);
    }

	/**
	 * How long a {@code login_audit_log} session stays {@code ACTIVE} before
	 * {@code LoginSessionSweepJob} expires it. Fail-safe: defaults to 30 days
	 * if the singleton row is missing (same default as the column itself).
	 */
	@Transactional(readOnly = true)
	public int getLoginSessionExpirationDays() {
		return systemConfigRepository
			.findFirstByActiveTrue()
			.map(SystemConfig::getLoginSessionExpirationDays)
			.orElse(30)
		;
	}

	/**
	 * The global fallback default sort (V82), empty (never {@code null})
	 * when unconfigured — the caller (e.g. {@code AlliesService}) decides
	 * its own final hard fallback ({@code createdAt DESC}).
	 */
	@Transactional(readOnly = true)
	public List<SortOrder> getDefaultSort() {
		return systemConfigRepository
			.findFirstByActiveTrue()
			.map(SystemConfig::getDefaultSort)
			.filter(sort -> sort != null)
			.orElse(List.of())
		;
	}

    /**
     * Updates the singleton system configuration report footer.
     */
    @Transactional
    public SystemConfig updateReportFooter(String reportFooter) {
        SystemConfig config = loadOrCreate();
        config.setReportFooter(reportFooter != null && !reportFooter.isBlank() ? reportFooter.trim() : null);
        return systemConfigRepository.save(config);
    }

    /**
     * Partial update of the singleton system configuration: report footer and
     * the audit overrides. A {@code null} field in the request leaves the
     * current value untouched.
     */
    @Transactional
    public SystemConfig updateSystemConfig(UpdateSystemConfigRequest request) {
        SystemConfig config = loadOrCreate();

        if (request.reportFooter() != null) {
            String footer = request.reportFooter();
            config.setReportFooter(footer.isBlank() ? null : footer.trim());
        }
        if (request.auditCreateMode() != null) {
            config.setAuditCreateMode(request.auditCreateMode());
        }
        if (request.auditUpdateMode() != null) {
            config.setAuditUpdateMode(request.auditUpdateMode());
        }
        if (request.auditDeleteMode() != null) {
            config.setAuditDeleteMode(request.auditDeleteMode());
        }
        if (request.captureBeforeAfterMode() != null) {
            config.setCaptureBeforeAfterMode(request.captureBeforeAfterMode());
        }
        if (request.reportAuditMode() != null) {
            config.setReportAuditMode(request.reportAuditMode());
        }
        if (request.loginAuditEnabled() != null) {
            config.setLoginAuditEnabled(request.loginAuditEnabled());
        }
		if (request.loginSessionExpirationDays() != null) {
			config.setLoginSessionExpirationDays(request.loginSessionExpirationDays());
		}
		if (request.defaultSort() != null) {
			config.setDefaultSort(validateCommonSort(request.defaultSort()));
		}

        return systemConfigRepository.save(config);
    }

    private SystemConfig loadOrCreate() {
        return systemConfigRepository.findFirstByActiveTrue()
                .orElseGet(SystemConfig::new);
    }

	/**
	 * Restricts a global default-sort request to
	 * {@link CommonSortFields#COMMON_SORTABLE_FIELDS} — unlike
	 * {@code entity_config.default_sort}, this one isn't checked against any
	 * single entity's own sortable-fields map, so it needs its own
	 * whitelist. An empty list normalizes to {@code null} ("no global
	 * default configured").
	 */
	private List<SortOrder> validateCommonSort(List<SortOrder> defaultSort) {
		if (defaultSort.isEmpty()) {
			return null;
		}
		for (SortOrder order : defaultSort) {
			if (!CommonSortFields.COMMON_SORTABLE_FIELDS.contains(order.field())) {
				throw new IllegalArgumentException("system_config.default_sort.field_not_allowed");
			}
		}
		return defaultSort;
	}

}
