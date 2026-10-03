package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.UUID;

public record PromoterTypeDto(
        UUID uuid,
        String code,
        String name,
        String description,
        @Display(Display.Kind.BOOLEAN) boolean generatesHierarchyOverride,
        /** Discount-authority cap (%) for promoters of this type; {@code null} = no cap from this axis. */
        BigDecimal maxDiscountPct,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}
