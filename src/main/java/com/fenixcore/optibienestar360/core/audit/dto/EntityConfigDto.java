package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.util.SortOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin read model for one {@code audit_entity_config} row. Boolean flags and {@code updatedAt} carry a localized {@code _Display} sibling (ADR 0014). */
public record EntityConfigDto(
	UUID uuid,
	String entityKey,
	String displayName,
	String tableName,
	@Display(Display.Kind.BOOLEAN) boolean enabled,
	@Display(Display.Kind.BOOLEAN) boolean auditCreate,
	@Display(Display.Kind.BOOLEAN) boolean auditUpdate,
	@Display(Display.Kind.BOOLEAN) boolean auditDelete,
	@Display(Display.Kind.BOOLEAN) boolean auditReport,
	@Display(Display.Kind.BOOLEAN) boolean captureBeforeAfter,
	List<SortOrder> defaultSort,
	String notes,
	@Display(Display.Kind.DATETIME) Instant updatedAt
) {}
