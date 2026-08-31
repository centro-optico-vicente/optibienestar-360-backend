package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

public record MedicalSpecialtyDto(
	UUID uuid,
	String code,
	String name,
	String description,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
