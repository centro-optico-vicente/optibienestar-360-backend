package com.fenixcore.optisaludplus.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /v1/admin/payments/{uuid}/reject}. The reason is
 * REQUIRED — it's persisted to {@code review_reason} and the V23 CHECK
 * constraint {@code chk_payments_rejection_has_reason} would reject the
 * row at the DB level otherwise. More importantly, the affiliate needs to
 * know why so they can re-submit.
 */
public record PaymentRejectRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {}
