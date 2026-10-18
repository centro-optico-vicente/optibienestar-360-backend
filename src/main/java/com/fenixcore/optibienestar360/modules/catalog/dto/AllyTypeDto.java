package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.util.UUID;

public record AllyTypeDto(
	UUID uuid,
	String code,
	String name,
	String description,
	/** B2B "Aliado Comercial" / B2P "Aliado Profesional" (V179); localized {@code segment_Display}. */
	@Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
	/** B2P only: MEDICAL / WELLNESS (V179); {@code null} for B2B. */
	@Display(value = Display.Kind.ENUM, enumScope = "ally_type.professional_area") ProfessionalArea professionalArea,
	@Display(Display.Kind.BOOLEAN) boolean active
) {}
