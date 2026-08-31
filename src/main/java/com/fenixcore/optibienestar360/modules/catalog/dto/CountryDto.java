package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

public record CountryDto(
	UUID uuid,
	String isoCode,
	String name,
	String locale,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
