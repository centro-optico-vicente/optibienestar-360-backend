package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Assigns a membership-less payment (or its remaining credit) to a membership
 * ({@code POST /v1/admin/payments/{uuid}/assign-membership}, V201). {@code appliedPeriod} /
 * {@code coverageThroughPeriod} follow the same rules as a regular collection.
 */
public record AssignMembershipRequest(
        @NotNull UUID membershipUuid,
        LocalDate appliedPeriod,
        LocalDate coverageThroughPeriod
) {}
