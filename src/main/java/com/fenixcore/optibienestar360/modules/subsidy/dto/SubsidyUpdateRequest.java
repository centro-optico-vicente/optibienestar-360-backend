package com.fenixcore.optibienestar360.modules.subsidy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * PATCH-style payload for {@code PUT /v1/admin/subsidies/{uuid}} — every field
 * optional; {@code null} means "leave unchanged". The two percentages are the
 * exception: because {@code null} is also a valid stored value ("fee not
 * covered"), they can only be adjusted, not cleared, through this endpoint —
 * revoke + re-create for a full reshape.
 *
 * <p>{@code beneficiaries} is null = leave the current list untouched; a
 * non-null list <b>replaces</b> the whole set (bounded by the cap). The
 * resulting subsidy must still cover at least one fee
 * ({@code subsidy.coverage.required}).</p>
 */
public record SubsidyUpdateRequest(
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal monthlyPercentage,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal inscriptionPercentage,
        @PositiveOrZero Integer maxExoneratedBeneficiaries,
        String reason,
        LocalDate validFrom,
        LocalDate validUntil,
        Boolean active,
        @Valid List<SubsidyBeneficiaryRequest> beneficiaries
) {}
