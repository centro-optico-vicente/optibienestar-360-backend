package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code code}/{@code hierarchyLevel} are the immutable natural/ordering key — not editable here. */
public record PromoterRankUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) Integer maxSubordinates,
        @Size(max = 200) String description
) {}
