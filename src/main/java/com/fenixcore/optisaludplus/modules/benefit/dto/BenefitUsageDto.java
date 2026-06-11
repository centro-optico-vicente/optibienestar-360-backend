package com.fenixcore.optisaludplus.modules.benefit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Output DTO for {@code POST /v1/ally/benefit-usage} (and any future
 * read endpoints / usage history). Flat record — the relationships are
 * resolved into UUID + label pairs so the ally portal renders without a
 * second round-trip.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenefitUsageDto(
        UUID uuid,

        // Subject (flat refs)
        UUID membershipUuid,
        UUID memberUuid,
        UUID planUuid,
        String planCode,

        // Where + what
        UUID allyUuid,
        String allyName,
        UUID allyServiceUuid,
        UUID allyUserUuid,

        // When
        LocalDate usageDate,
        Instant usageDatetime,

        // Co-pay
        BigDecimal copayAmount,
        String copayCurrency,

        // Detail
        Map<String, Object> metadata,
        String notes,

        // Audit
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
