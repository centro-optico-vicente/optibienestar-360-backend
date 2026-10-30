package com.fenixcore.optibienestar360.modules.membership.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filters of {@code GET /v1/admin/memberships}; every field is optional and they combine with AND.
 *
 * <p>{@code status} is a lifecycle value ({@code ACTIVE}, {@code SUSPENDED}, {@code EXPIRED},
 * {@code CANCELED}) or one of the derived collection states {@code OVERDUE} (due date passed while ACTIVE or
 * SUSPENDED) and {@code DUE_SOON} (ACTIVE, due within {@code dueSoonDays}, default 7).
 * {@code planType} is the Spanish code ({@code INDIVIDUAL}, {@code FAMILIAR}, …).</p>
 */
public record MembershipSearchCriteria(
        String q,
        UUID memberUuid,
        UUID promoterUuid,
        UUID planUuid,
        String planType,
        String status,
        Integer dueSoonDays,
        LocalDate enrolledFrom,
        LocalDate enrolledTo,
        LocalDate nextDueFrom,
        LocalDate nextDueTo
) {}
