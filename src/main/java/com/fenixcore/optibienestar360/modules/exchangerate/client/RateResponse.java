package com.fenixcore.optibienestar360.modules.exchangerate.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Mirrors {@code exchange-rates-api}'s Rust {@code RateResponse} struct
 * ({@code src/models.rs}) as actually shipped by the live
 * {@code GET /v1/ve/bcv/{currency}} routes today — NOT the richer shape
 * described by that project's {@code /history} spec doc, which is not a
 * live endpoint yet.
 *
 * <p>{@code rate}/{@code inverted_rate}/{@code previous_rate} are
 * serialized by the Rust side as JSON strings (e.g. {@code "36.4250"});
 * Jackson coerces a numeric-looking JSON string into a {@link BigDecimal}
 * target field without extra configuration, so no custom deserializer is
 * needed here.</p>
 *
 * <p>{@code timestamp}'s exact format is unconfirmed (the live server
 * wasn't reachable during investigation) — {@link ExchangeRatesApiClient}
 * callers must parse it defensively; see
 * {@code ExchangeRateIngestionService}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateResponse(
        String source,
        String currency,
        @JsonProperty("base_currency") String baseCurrency,
        String timestamp,
        BigDecimal rate,
        @JsonProperty("inverted_rate") BigDecimal invertedRate,
        @JsonProperty("previous_rate") BigDecimal previousRate,
        @JsonProperty("percent_difference") BigDecimal percentDifference,
        @JsonProperty("amount_difference") BigDecimal amountDifference,
        String status
) {}
