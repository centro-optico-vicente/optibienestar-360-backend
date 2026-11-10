package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Row of the advisor's read-only list of the allies they brought in
 * ({@code GET /v1/me/advisor/allies}): only what an advisor needs to follow the ally's
 * registration, nothing fiscal or contact-related.
 */
public record AdvisorAllyListItemDto(
        UUID uuid,
        Integer allyNumber,
        String name,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        List<String> allyTypeNames,
        @Display DisplayRef city,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_status") Ally.RegistrationStatus registrationStatus,
        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
