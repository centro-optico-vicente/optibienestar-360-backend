package com.fenixcore.optibienestar360.core.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Audit flags aren't part of create — every new row starts with them all
 * {@code true} and no default sort (same defaults V60's seed rows use);
 * tune them afterward via {@code PUT /v1/admin/entity-config/{entityKey}}.
 */
public record CreateEntityConfigRequest(
	@NotBlank(message = "entity_config.entity_key.required")
	@Size(max = 80, message = "entity_config.entity_key.max_size")
	String entityKey,
	@NotBlank(message = "entity_config.display_name.required")
	@Size(max = 120, message = "entity_config.display_name.max_size")
	String displayName,
	@Size(max = 120, message = "entity_config.table_name.max_size")
	String tableName,
	@Size(max = 2000, message = "entity_config.notes.max_size")
	String notes
) {}
