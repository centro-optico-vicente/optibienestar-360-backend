package com.fenixcore.optibienestar360.modules.promoter.dto;

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
 * of {@code maxDays} (basis=DAYS) / {@code maxAmount} (basis=AMOUNT) must be
 * present, matching {@code basis} — enforced service-side ({@code
 * collection_commission_tier.basis_field_mismatch}). Exactly one of {@code
 * commissionPct} / {@code flatAmount} must also be present — enforced
 * service-side ({@code collection_commission_tier.pct_xor_flat}); {@code
 * flatAmountCurrencyUuid} is required alongside {@code flatAmount}. {@code
 * campaignUuid} is an optional anchor (V126) — {@code null} = a standing
 * tier; {@code startsAt}/{@code endsAt} are the tier's own validity window.
 */
public record CollectionCommissionTierCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull Basis basis,
        @Positive Integer maxDays,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal maxAmount,
        @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        UUID flatAmountCurrencyUuid,
        List<UUID> promoterTypeUuids,
        UUID campaignUuid,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {}
