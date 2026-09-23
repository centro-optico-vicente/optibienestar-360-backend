package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/collection-commission-tiers}. Exactly one
 * of {@code maxDays} (basis=DAYS) / {@code minAmount} (basis=AMOUNT) must be
 * present, matching {@code basis} — enforced service-side ({@code
 * collection_commission_tier.basis_field_mismatch}). {@code minAmount} is a
 * minimum threshold (V144, {@code >=}, highest-qualifying-bucket) — {@code
 * minAmountCurrencyUuid} is required alongside it, same as {@code flatAmount}/
 * {@code flatAmountCurrencyUuid}. Exactly one of {@code commissionPct} /
 * {@code flatAmount} must also be present — enforced service-side ({@code
 * collection_commission_tier.pct_xor_flat}). {@code campaignUuid} is an
 * optional anchor (V126) — {@code null} = a standing tier; {@code startsAt}/
 * {@code endsAt} are the tier's own validity window.
 */
public record CollectionCommissionTierCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull Basis basis,
        @Positive Integer maxDays,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal minAmount,
        UUID minAmountCurrencyUuid,
        @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        UUID flatAmountCurrencyUuid,
        List<UUID> promoterTypeUuids,
        /** Settlement-frequency axes (Fase A, V148) — null = leave the entity default (MONTHLY, no-op vs. today's hardcoded behavior). */
        PeriodStrategy accrualPeriodStrategy,
        PeriodStrategy partialSettlementPeriodStrategy,
        PeriodStrategy finalSettlementPeriodStrategy,
        PeriodStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        UUID campaignUuid,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {}
