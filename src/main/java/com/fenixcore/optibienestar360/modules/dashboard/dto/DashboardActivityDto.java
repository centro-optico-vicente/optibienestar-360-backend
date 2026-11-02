package com.fenixcore.optibienestar360.modules.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One real event of the "recent activity" feed. The description is resolved by
 * the frontend from {@code type}; {@code title} (a person) and {@code subtitle}
 * (ally, service or plan) are literal data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardActivityDto(
        Type type,
        DashboardSection section,
        String title,
        String subtitle,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        Status status
) {

    public enum Type {
        PAYMENT_REGISTERED, PAYMENT_APPROVED, PAYMENT_REJECTED,
        USAGE_REGISTERED, USAGE_REVERSED,
        AFFILIATION_REQUEST_RECEIVED, AFFILIATION_REQUEST_APPROVED, AFFILIATION_REQUEST_REJECTED,
        MEMBER_ENROLLED
    }

    public enum Status { SUCCESS, PENDING, ERROR }
}
