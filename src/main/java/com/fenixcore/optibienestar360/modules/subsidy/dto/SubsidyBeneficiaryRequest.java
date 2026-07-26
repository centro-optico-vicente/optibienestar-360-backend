package com.fenixcore.optibienestar360.modules.subsidy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One per-beneficiary exoneration line inside a subsidy create/update payload.
 * Both percentages are optional (0-100, {@code null} = that fee not covered) but
 * at least one must be present — enforced service-side
 * ({@code subsidy.coverage.required}).
 */
public record SubsidyBeneficiaryRequest(
        @NotNull UUID beneficiaryUuid,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal monthlyPercentage,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal inscriptionPercentage
) {}
