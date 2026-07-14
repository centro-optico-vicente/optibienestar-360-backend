package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/allies/{allyUuid}/services/{uuid}}.
 * PATCH semantics — non-null fields applied; null fields ignored.
 *
 * <p>{@code reviewStatus} is intentionally NOT here — workflow transitions
 * go through the dedicated approve / reject / remove endpoints so the
 * audit log captures the actor + reason properly.</p>
 */
public record AllyServiceUpdateRequest(
        UUID serviceCategoryUuid,
        @Size(max = 200) String name,
        String description,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal priceUsd,

        @Digits(integer = 3, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "100.0", inclusive = true)
        BigDecimal discountPct,

        Boolean requiresAppointment,
        Boolean published,
        Boolean active
) {}
