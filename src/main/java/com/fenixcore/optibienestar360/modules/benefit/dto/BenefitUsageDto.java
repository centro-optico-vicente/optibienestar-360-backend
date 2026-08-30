package com.fenixcore.optibienestar360.modules.benefit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Output DTO for {@code POST /v1/ally/benefit-usage} (and any future
 * read endpoints / usage history). Flat record — the relationships are
 * resolved into UUID + label pairs so the ally portal renders without a
 * second round-trip. Scalars carry a localized {@code _Display} sibling (hub ADR 0014).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenefitUsageDto(
        UUID uuid,

        // Subject (flat refs)
        UUID membershipUuid,
        UUID memberUuid,
        String memberFullName,
        String memberDocumentType,
        String memberDocumentNumber,
        UUID planUuid,
        String planCode,

        // Where + what
        UUID allyUuid,
        String allyName,
        UUID allyServiceUuid,
        UUID allyUserUuid,

        // When
        @Display(Display.Kind.DATE) LocalDate usageDate,
        @Display(Display.Kind.DATETIME) Instant usageDatetime,

        // Co-pay
        @Display(Display.Kind.MONEY) BigDecimal copayAmount,
        String copayCurrency,

        // Detail
        Map<String, Object> metadata,
        String notes,

        // Audit
        @Display(value = Display.Kind.ENUM, enumScope = "benefit_usage.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
