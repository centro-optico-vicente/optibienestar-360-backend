package com.fenixcore.optibienestar360.modules.exchangerate.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of one {@link ExchangeRateIngestionService#fetchAndStoreLatest()}
 * run — per-currency detail, consumed both by
 * {@code FetchExchangeRatesJobRunner} (folded into {@code JobRunResult})
 * and by the admin quick-action endpoint (returned directly as JSON).
 */
public record IngestionSummary(List<CurrencyResult> currencies) {

    public enum Status {
        /** A new row was inserted for this pair/day. */
        FETCHED,
        /** The unique (pair, operation_date) index already had today's row — not a failure. */
        ALREADY_HAD_TODAY,
        /** {@code ExchangeRatesApiClient} returned empty (network error, timeout, non-2xx). */
        FETCH_FAILED,
        /** The response was fetched but {@code timestamp} could not be parsed into a date. */
        PARSE_FAILED;
    }

    public record CurrencyResult(String currencyCode, Status status, String detail) {}

    /** Flattened for {@code JobRunResult.summary} (a {@code Map<String, Object>} JSONB payload). */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> summary = new LinkedHashMap<>();
        int fetched = 0, alreadyHad = 0, failed = 0;
        Map<String, String> perCurrency = new LinkedHashMap<>();
        for (CurrencyResult r : currencies) {
            perCurrency.put(r.currencyCode(), r.status() + (r.detail() != null ? (": " + r.detail()) : ""));
            switch (r.status()) {
                case FETCHED -> fetched++;
                case ALREADY_HAD_TODAY -> alreadyHad++;
                case FETCH_FAILED, PARSE_FAILED -> failed++;
            }
        }
        summary.put("fetched", fetched);
        summary.put("alreadyHadToday", alreadyHad);
        summary.put("failed", failed);
        summary.put("currencies", perCurrency);
        return summary;
    }
}
