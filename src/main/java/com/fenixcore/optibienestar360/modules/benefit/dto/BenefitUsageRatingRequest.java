package com.fenixcore.optibienestar360.modules.benefit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /v1/me/usage-history/{uuid}/rating} (V201). The ally is
 * always rated; the product only when the usage was of a catalogued offer.
 * Sending it again replaces the previous rating.
 */
public record BenefitUsageRatingRequest(
        @NotNull @Min(1) @Max(5) Integer allyRating,
        @Min(1) @Max(5) Integer productRating,
        @Size(max = 500) String comment
) {}
