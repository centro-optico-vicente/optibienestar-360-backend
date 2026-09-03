package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/**
 * {@code country} follows the FK convention (hub ADR 0014): it serializes as
 * {@code country_Uuid} + {@code country_Display} + {@code country_Code}
 * (the ISO code). {@code active} carries a localized {@code _Display} sibling.
 */
public record StateDto(
	UUID uuid,
	String code,
	String name,
	@Display DisplayRef country,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
