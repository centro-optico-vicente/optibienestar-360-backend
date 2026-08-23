package com.fenixcore.optibienestar360.modules.system.dto;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Partial-update semantics: a {@code null} field leaves the current value
 * untouched (same convention as {@code reportFooter}), so callers only send
 * the fields they want to change.
 */
public record UpdateSystemConfigRequest(
        @Size(max = 500, message = "system_config.report_footer.max_size")
        String reportFooter,
        AuditMode dataChangeAuditMode,
        AuditMode reportAuditMode,
        Boolean loginAuditEnabled,
	@Positive(message = "system_config.login_session_expiration_days.positive")
	Integer loginSessionExpirationDays
) {}
