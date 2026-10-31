package com.fenixcore.optibienestar360.modules.benefit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Output DTO for {@code POST /v1/me/allies/benefit-usages} (and any future
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

        // Who consumed: the holder (beneficiaryUuid null) or one of their beneficiaries (V195)
        UUID beneficiaryUuid,
        String consumedByName,
        /** Beneficiary's relationship to the holder; {@code null} when the holder consumed. */
        String beneficiaryRelationship,

        // Where + what
        UUID allyUuid,
        String allyName,
        UUID allyProductUuid,
        /** Name of the consumed product; {@code null} for uncatalogued usages. */
        String allyProductName,
        UUID allyUserUuid,
        UUID productCategoryUuid,
        String productCategoryName,

        // When
        @Display(Display.Kind.DATE) LocalDate usageDate,
        @Display(Display.Kind.DATETIME) Instant usageDatetime,

        // Co-pay
        @Display(Display.Kind.MONEY) BigDecimal copayAmount,
        String copayCurrency,

        // Valor consumido
        @Display(Display.Kind.MONEY) BigDecimal consumptionAmount,
        String consumptionCurrency,

        // Detail
        Map<String, Object> metadata,
        String notes,

        // Affiliate's rating (V201) — all null until rated
        Integer allyRating,
        Integer productRating,
        String ratingComment,
        @Display(Display.Kind.DATETIME) Instant ratedAt,

        // Audit
        @Display(value = Display.Kind.ENUM, enumScope = "benefit_usage.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
