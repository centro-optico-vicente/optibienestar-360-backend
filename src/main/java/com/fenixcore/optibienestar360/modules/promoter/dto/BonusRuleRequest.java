package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Create/replace payload for a bonus rule ({@code POST/PUT /v1/admin/bonus-rules}).
 * Bean validation covers the per-field shape; the cross-field rules (exactly one
 * of {@code flatAmount}/{@code rewardPct}, CAMPAIGN date coherence, PER_BLOCK ⇒
 * FLAT) are enforced in {@code BonusRulesService} for localized 422s.
 */
public record BonusRuleRequest(
        @NotBlank @Size(max = 150) String name,

        @Size(max = 2000) String description,

        UUID promoterTypeUuid,

        @NotNull BonusMetric metric,

        @NotNull AccrualMode accrual,

        @NotNull @Positive Integer thresholdCount,

        @NotNull WindowStrategy windowStrategy,

        OffsetDateTime campaignStart,

        OffsetDateTime campaignEnd,

        @NotNull RewardType rewardType,

        @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal flatAmount,

        @DecimalMin(value = "0.01") @DecimalMax(value = "100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal rewardPct,

        @Size(min = 3, max = 3) String rewardCurrency,

        Boolean includeSystemPromoters,

        /** Optional formal campaign anchor (V120) — {@code null} = a standing rule. */
        UUID campaignUuid,

        OffsetDateTime startsAt,

        OffsetDateTime endsAt
) {}
