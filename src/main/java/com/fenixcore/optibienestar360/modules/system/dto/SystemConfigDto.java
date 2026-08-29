package com.fenixcore.optibienestar360.modules.system.dto;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.core.util.SortOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SystemConfigDto(
	UUID uuid,
	String reportFooter,
	AuditMode dataChangeAuditMode,
	AuditMode reportAuditMode,
	boolean loginAuditEnabled,
	int loginSessionExpirationDays,
	List<SortOrder> defaultSort,
	Instant updatedAt
) {}
