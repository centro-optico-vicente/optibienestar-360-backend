package com.fenixcore.optibienestar360.modules.exchangerate.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Correction of a {@code MANUAL} row — {@code PUT /v1/admin/exchange-rates/{uuid}}.
 * All fields are optional/PATCH-style (only non-null ones are applied); restricted
 * to {@code source = MANUAL} rows by {@code ExchangeRateService#update} — an
 * ingested (BCV/EXCHANGE_RATES_API) row is historical fact and stays immutable.
 */
public record ExchangeRateUpdateRequest(
        @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
        LocalDate operationDate,
        Instant validFrom
) {}
