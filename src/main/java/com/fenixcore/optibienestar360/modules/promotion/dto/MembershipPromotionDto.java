package com.fenixcore.optibienestar360.modules.promotion.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A promotion applied to a membership (V175). {@code codeOwnerType} is PROMOTER/MEMBER/ALLY or null when no code was used. */
public record MembershipPromotionDto(
        UUID uuid,
        @Display DisplayRef promotion,
        @Display DisplayRef campaign,
        @Display(Display.Kind.ENUM) Kind kind,
        @Display(Display.Kind.PERCENT) BigDecimal discountPct,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        Integer cyclesRemaining,
        boolean inscriptionApplied,
        String codeUsed,
        String codeOwnerType,
        @Display DisplayRef codeOwner,
        String origin,
        @Display(Display.Kind.ENUM) String status,
        @Display(Display.Kind.DATETIME) Instant assignedAt,
        @Display(Display.Kind.DATETIME) Instant endedAt,
        String endedReason
) {}
