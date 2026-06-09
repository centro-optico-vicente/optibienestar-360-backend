package com.fenixcore.optisaludplus.modules.membership.dto;

import com.fenixcore.optisaludplus.modules.membership.entity.Plan.PlanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for {@code POST /v1/admin/plans}.
 *
 * <p>{@code code} is validated for the same UPPER_SNAKE_CASE shape used by
 * the V14 seed (INDIVIDUAL / FAMILIAR / CORPORATIVO) so future codes follow
 * the established convention.</p>
 */
public record PlanCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$", message = "{validation.code.uppercase.long}")
        String code,

        @NotBlank @Size(max = 100) String name,
        String description,

        @NotNull PlanType type,

        @NotNull @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal inscriptionFee,
        @NotNull @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal monthlyFee,

        @PositiveOrZero Integer includedBeneficiaries,   // default 0 server-side
        @PositiveOrZero Integer maxBeneficiaries,         // null = no cap
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal extraBeneficiaryInscriptionFee,        // null = plan disallows extras

        @Min(0) Integer gracePeriodDays,                  // default 7 server-side

        Boolean published,
        Instant publishedAt
) {}
