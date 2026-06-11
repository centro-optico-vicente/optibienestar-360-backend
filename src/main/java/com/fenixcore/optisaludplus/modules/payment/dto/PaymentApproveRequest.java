package com.fenixcore.optisaludplus.modules.payment.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for {@code PUT /v1/admin/payments/{uuid}/approve}.
 * {@code reason} doubles as free-form approval notes when the admin wants
 * to record a justification (e.g. "matched against bank statement
 * 2026-06-12"). Body may be omitted entirely; the service does not require
 * a reason for approval, only for rejection.
 */
public record PaymentApproveRequest(
        @Size(max = 500) String reason
) {}
