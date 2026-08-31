package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

public record CityDto(
	UUID uuid,
	String name,
	UUID stateUuid,
	String stateCode,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
