package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.util.UUID;

/**
 * Moves a rank to the position immediately after {@code afterRankUuid} in the
 * active-ranks list ordered by {@code hierarchyLevel} ascending (V111). A
 * {@code null} {@code afterRankUuid} moves the rank to the very beginning of
 * that list. Position is referenced by uuid, never a raw index/level, since
 * the actual {@code hierarchyLevel} values are an internal gap-based
 * implementation detail ({@code PromoterRankService.reorder}).
 */
public record PromoterRankReorderRequest(
        UUID afterRankUuid
) {}
