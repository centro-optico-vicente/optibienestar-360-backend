package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.util.SortOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EntityConfigDto(
	UUID uuid,
	String entityKey,
	String displayName,
	String tableName,
	boolean enabled,
	boolean auditCreate,
	boolean auditUpdate,
	boolean auditDelete,
	boolean auditReport,
	boolean captureBeforeAfter,
	List<SortOrder> defaultSort,
	String notes,
	Instant updatedAt
) {}
