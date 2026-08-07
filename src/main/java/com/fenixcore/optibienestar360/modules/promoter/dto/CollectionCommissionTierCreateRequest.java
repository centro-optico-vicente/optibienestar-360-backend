package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Payload for {@code POST /v1/admin/collection-commission-tiers}. */
public record CollectionCommissionTierCreateRequest(
        @NotBlank String name,
        @NotNull @Positive Integer maxDays,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct
) {}
