package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One row of {@code CompetitiveRuleCreateRequest.positions}/{@code
 * CompetitiveRuleUpdateRequest.positions} — full-replace semantics, same as
 * {@code PaymentLinesUpdateRequest.lines}: the whole list is validated and
 * swapped in as a unit, never patched row-by-row.
 */
public record CompetitiveRulePositionRequest(
        @NotNull @Min(1) Integer positionFrom,
        @NotNull @Min(1) Integer positionTo,
        String label,
        @NotNull RewardType rewardType,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal flatAmount,
        @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal rewardPct,
        java.util.UUID rewardCurrencyUuid,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal rewardMinAmount,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal rewardMaxAmount,
        @Min(1) Integer minThresholdCount,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal minThresholdAmount
) {
}
