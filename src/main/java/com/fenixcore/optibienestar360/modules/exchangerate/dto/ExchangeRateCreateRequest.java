package com.fenixcore.optibienestar360.modules.exchangerate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Manual entry — {@code POST /v1/admin/exchange-rates}. {@code source} is
 * always {@code MANUAL} (BCV/EXCHANGE_RATES_API rows only ever come from the
 * not-yet-built {@code FetchExchangeRatesJob}, Tarea 2.13).
 *
 * <p>{@code operationDate} and {@code validFrom} answer different questions
 * and are deliberately not the same field: {@code operationDate} is the
 * calendar day the BCV/API value itself corresponds to (a plain date, per
 * BCV convention — no time-of-day); {@code validFrom} is the exact instant
 * this rate becomes the one {@code CurrencyConversionService} picks for a
 * conversion (an ingested BCV row's {@code validFrom} is typically the next
 * business day at 8 AM, per {@code BusinessDayCalculator} — NOT the same
 * moment as its {@code operationDate}). {@code validFrom} omitted (null)
 * defaults to {@code now()} — a manual entry is immediately usable unless
 * the admin explicitly backdates or schedules it.</p>
 */
public record ExchangeRateCreateRequest(
        @NotBlank String baseCurrencyCode,
        @NotBlank String quoteCurrencyCode,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
        @NotNull LocalDate operationDate,
        Instant validFrom
) {}
