package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/allies/{allyUuid}/services}.
 *
 * <p>Admin-side create: the row lands at {@code review_status='PROPOSED'}
 * by default (DB schema default). The admin uses the dedicated workflow
 * endpoints under {@code /v1/admin/ally-services/{uuid}/approve} (v2
 * checklist bullet) to move it to APPROVED — keeps the audit log
 * consistent with proposals coming from the ally side.</p>
 */
public record AllyServiceCreateRequest(
        @NotNull UUID serviceCategoryUuid,
        @NotBlank @Size(max = 200) String name,
        String description,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal priceUsd,

        @Digits(integer = 3, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "100.0", inclusive = true)
        BigDecimal discountPct,

        Boolean requiresAppointment
) {}
