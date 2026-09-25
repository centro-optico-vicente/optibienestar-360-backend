package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for {@code PUT /{uuid}/lines} — replaces the entire lines collection
 * of a {@code DRAFT} payment (V117 lines feature). {@code amount} is the
 * optional declared header total: when present, {@code sum(lines.amount)}
 * must not exceed it (a partial registration against an expected total is
 * fine, an overshoot is rejected); when absent, the header amount is
 * recomputed as {@code sum(lines.amount)}. See {@code PaymentsService.updateLines}.
 */
public record PaymentLinesUpdateRequest(
        @NotEmpty @Valid List<PaymentLineRequest> lines,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.01", message = "{payment.amount.positive}")
        BigDecimal amount
) {}
