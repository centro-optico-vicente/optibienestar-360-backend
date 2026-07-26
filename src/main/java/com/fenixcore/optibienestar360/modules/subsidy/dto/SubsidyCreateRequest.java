package com.fenixcore.optibienestar360.modules.subsidy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/subsidies}.
 *
 * <p>Coverage is two independent percentages ({@code null} = fee not covered,
 * 100 = full exoneration, 0<X<100 = partial). At least one must be present —
 * enforced service-side ({@code subsidy.coverage.required}). {@code validUntil}
 * is optional ({@code null} = indefinite). {@code beneficiaries} is optional and
 * bounded by {@code maxExoneratedBeneficiaries} (or the plan's own cap).</p>
 */
public record SubsidyCreateRequest(
        @NotNull UUID memberUuid,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal monthlyPercentage,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal inscriptionPercentage,
        @PositiveOrZero Integer maxExoneratedBeneficiaries,
        @NotBlank String reason,
        @NotNull LocalDate validFrom,
        LocalDate validUntil,
        @Valid List<SubsidyBeneficiaryRequest> beneficiaries
) {}
