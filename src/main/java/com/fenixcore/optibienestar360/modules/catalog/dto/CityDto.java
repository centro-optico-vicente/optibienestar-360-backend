package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/**
 * {@code state} follows the FK convention (hub ADR 0014): it serializes as
 * {@code state_Uuid} + {@code state_Display} + {@code state_Code}.
 * {@code active} carries a localized {@code _Display} sibling.
 */
public record CityDto(
	UUID uuid,
	String name,
	@Display DisplayRef state,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
