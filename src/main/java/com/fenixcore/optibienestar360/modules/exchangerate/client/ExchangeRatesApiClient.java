package com.fenixcore.optibienestar360.modules.exchangerate.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Thin HTTP client for {@code exchange-rates-api}
 * (https://rates-api.jeaninformatico.com) — the team's own service exposing
 * BCV reference rates (ADR 0015 §3). Only the currently-live routes are
 * called: {@code GET /v1/ve/bcv/{currency}} (lowercase code) for
 * {@code usd}/{@code eur}/{@code cny}/{@code rub}/{@code try}. No
 * {@code /history} endpoint exists yet.
 *
 * <p>Never lets a network failure propagate: connection refused, timeout,
 * or any 4xx/5xx response is caught and logged as a warning, returning
 * {@link Optional#empty()} — both the scheduled job and the admin
 * quick-action endpoint must degrade gracefully when this upstream service
 * is unreachable.</p>
 */
@Component
@Slf4j
public class ExchangeRatesApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    public ExchangeRatesApiClient(@Value("${app.exchange-rates-api.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Fetches the current BCV reference rate for {@code currencyCode}
     * (e.g. {@code "USD"}, {@code "EUR"}) against its implicit VES base.
     * Returns {@link Optional#empty()} on any failure — connection refused,
     * timeout, or a non-2xx HTTP status — never throws.
     */
    public Optional<RateResponse> fetchRate(String currencyCode) {
        String path = "/v1/ve/bcv/" + currencyCode.toLowerCase(Locale.ROOT);
        try {
            RateResponse response = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(RateResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException ex) {
            log.warn("exchange-rates-api call to {} failed: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }
}
