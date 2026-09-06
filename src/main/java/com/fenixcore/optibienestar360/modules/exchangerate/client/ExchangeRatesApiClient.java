package com.fenixcore.optibienestar360.modules.exchangerate.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Thin HTTP client for {@code exchange-rates-api} (ADR 0015 §3) — the
 * team's own service exposing BCV reference rates. Only the currently-live
 * routes are called: {@code GET /v1/ve/bcv/{currency}} (lowercase code) for
 * {@code usd}/{@code eur}/{@code cny}/{@code rub}/{@code try}. No
 * {@code /history} endpoint exists yet.
 *
 * <p>{@code baseUrl} is a per-call parameter, not constructor-injected from
 * a Spring property — the caller ({@code ExchangeRateIngestionService})
 * resolves it from {@code scheduled_jobs.parameters} (V94) so this
 * integration's endpoint lives with its job's config, not in a
 * one-off-per-integration {@code app.*} property.</p>
 *
 * <p>Never lets a network failure propagate: connection refused, timeout,
 * a malformed {@code baseUrl}, or any 4xx/5xx response is caught and logged
 * as a warning, returning {@link Optional#empty()} — both the scheduled job
 * and the admin quick-action endpoint must degrade gracefully when this
 * upstream service is unreachable.</p>
 */
@Component
@Slf4j
public class ExchangeRatesApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    public ExchangeRatesApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        // No baseUrl here — each call supplies its own absolute URI, since
        // baseUrl is resolved per job/run, not fixed at application startup.
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Fetches the current BCV reference rate for {@code currencyCode}
     * (e.g. {@code "USD"}, {@code "EUR"}) against its implicit VES base,
     * from {@code baseUrl} (e.g. {@code https://rates-api.jeaninformatico.com},
     * resolved by the caller from the owning job's parameters). Returns
     * {@link Optional#empty()} on any failure — connection refused, timeout,
     * a non-2xx HTTP status, or a malformed {@code baseUrl} — never throws.
     */
    public Optional<RateResponse> fetchRate(String currencyCode, String baseUrl) {
        String uri = baseUrl + "/v1/ve/bcv/" + currencyCode.toLowerCase(Locale.ROOT);
        try {
            RateResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(RateResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException | IllegalArgumentException ex) {
            // IllegalArgumentException covers a malformed baseUrl (invalid URI syntax).
            log.warn("exchange-rates-api call to {} failed: {}", uri, ex.getMessage());
            return Optional.empty();
        }
    }
}
