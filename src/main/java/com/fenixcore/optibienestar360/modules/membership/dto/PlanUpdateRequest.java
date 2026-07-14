package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for {@code PUT /v1/admin/plans/{uuid}}. PATCH semantics — only
 * non-null fields are applied; null fields keep the current value.
 *
 * <p>Renaming {@code code} is allowed but discouraged — services that
 * resolve plans by code (instead of UUID) will need to be updated. The
 * uniqueness check excludes self.</p>
 */
public record PlanUpdateRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$", message = "{validation.code.uppercase.long}")
        String code,

        @Size(max = 100) String name,
        String description,

        PlanType type,

        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal inscriptionFee,
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal monthlyFee,

        @PositiveOrZero Integer includedBeneficiaries,
        @PositiveOrZero Integer maxBeneficiaries,
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal extraBeneficiaryInscriptionFee,

        @Min(0) Integer gracePeriodDays,

        Boolean published,
        Instant publishedAt,

        Boolean active,
        String status
) {}
