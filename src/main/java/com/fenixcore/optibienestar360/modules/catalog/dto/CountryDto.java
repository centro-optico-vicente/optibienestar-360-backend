package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

public record CountryDto(
	UUID uuid,
	String isoCode,
	String name,
	String locale,
	@Display DisplayRef officialCurrency,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
