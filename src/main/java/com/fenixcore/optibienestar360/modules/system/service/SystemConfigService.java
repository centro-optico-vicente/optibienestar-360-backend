package com.fenixcore.optibienestar360.modules.system.service;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.modules.system.dto.UpdateSystemConfigRequest;
import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Resolves the global override for data-change auditing (spec 16-audit.md,
     * Decisión 8). Fail-safe: falls back to {@link AuditMode#PER_ENTITY} if the
     * singleton row is missing.
     */
    @Transactional(readOnly = true)
    public AuditMode getDataChangeAuditMode() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getDataChangeAuditMode)
                .orElse(AuditMode.PER_ENTITY);
    }

    /**
     * Resolves the global override for report-generation auditing. Same
     * fail-safe fallback as {@link #getDataChangeAuditMode()}.
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
        if (request.dataChangeAuditMode() != null) {
            config.setDataChangeAuditMode(request.dataChangeAuditMode());
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

        return systemConfigRepository.save(config);
    }

    private SystemConfig loadOrCreate() {
        return systemConfigRepository.findFirstByActiveTrue()
                .orElseGet(SystemConfig::new);
    }

}
