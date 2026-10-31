package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;

import java.util.UUID;

/** Catalog row of a license-issuing body (V213); {@code professionalArea} is optional. */
public record LicensingBodyDto(
	UUID uuid,
	String code,
	String name,
	String description,
	@Display(value = Display.Kind.ENUM, enumScope = "ally_type.professional_area") ProfessionalArea professionalArea,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
