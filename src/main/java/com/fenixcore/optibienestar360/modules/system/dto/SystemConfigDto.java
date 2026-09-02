package com.fenixcore.optibienestar360.modules.system.dto;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.util.SortOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record SystemConfigDto(
	UUID uuid,
	String reportFooter,
	@Display(Display.Kind.ENUM) AuditMode auditCreateMode,
	@Display(Display.Kind.ENUM) AuditMode auditUpdateMode,
	@Display(Display.Kind.ENUM) AuditMode auditDeleteMode,
	@Display(Display.Kind.ENUM) AuditMode captureBeforeAfterMode,
	@Display(Display.Kind.ENUM) AuditMode reportAuditMode,
	@Display(Display.Kind.BOOLEAN) boolean loginAuditEnabled,
	int loginSessionExpirationDays,
	List<SortOrder> defaultSort,
	@Display(Display.Kind.DATETIME) Instant updatedAt
) {}
