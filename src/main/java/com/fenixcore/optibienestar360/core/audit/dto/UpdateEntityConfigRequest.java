package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.util.SortOrder;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Full-replace semantics (unlike {@code UpdateSystemConfigRequest}'s
 * partial-update convention) — the admin screen always shows every flag, so
 * the client sends the complete state. {@code defaultSort} field names
 * aren't validated here against this specific entity's sortable-fields map
 * (this controller is entity-agnostic); an unsupported field is silently
 * dropped downstream by {@code SortFieldValidator.resolve}, same tolerant
 * handling as an explicit {@code ?sort=}.
 */
public record UpdateEntityConfigRequest(
	boolean enabled,
	boolean auditCreate,
	boolean auditUpdate,
	boolean auditDelete,
	boolean auditReport,
	boolean captureBeforeAfter,
	List<SortOrder> defaultSort,
	@Size(max = 2000, message = "entity_config.notes.max_size")
	String notes
) {}
