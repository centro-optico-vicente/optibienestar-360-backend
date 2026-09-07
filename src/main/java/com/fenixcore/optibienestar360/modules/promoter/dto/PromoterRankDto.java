package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

public record PromoterRankDto(
        UUID uuid,
        String code,
        String name,
        int hierarchyLevel,
        Integer maxSubordinates,
        String description,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}
