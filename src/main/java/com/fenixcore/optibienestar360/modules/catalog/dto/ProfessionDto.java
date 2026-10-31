package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/**
 * {@code parent} follows the FK convention (hub ADR 0014): {@code parent_Uuid} +
 * {@code parent_Display} + {@code parent_Code}; {@code null} for a top-level
 * profession, set for a sub-specialty (V213).
 */
public record ProfessionDto(
	UUID uuid,
	String code,
	String name,
	String description,
	@Display DisplayRef parent,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
