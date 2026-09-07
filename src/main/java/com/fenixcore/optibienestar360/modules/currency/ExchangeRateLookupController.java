package com.fenixcore.optibienestar360.modules.currency;

import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.service.ExchangeRateLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic "rate vigente" lookup (ADR 0015 §7) — deliberately NOT under
 * {@code /v1/admin} and carries no {@code @PreAuthorize}: the published
 * BCV/API rate itself is not sensitive (unlike the full history/CRUD over
 * {@code exchange_rates}, gated by {@code EXCHANGE_RATE_*} on {@code
 * AdminExchangeRateController}) — any authenticated user may need it to
 * preview a conversion before saving something (a payment, a commission, an
 * ally service price, ...), regardless of role. Spring Security's default
 * {@code anyRequest().authenticated()} rule already covers "must be logged
 * in"; no further gate is needed here.
 */
@RestController
@RequestMapping("/v1/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateLookupController {

    private final ExchangeRateLookupService service;

    /**
     * {@code asOf} previews the rate vigente as of that point in time
     * instead of right now. Accepts either an exact ISO-8601
     * instant/offset timestamp (when the caller knows one — e.g. a
     * document's own recorded time) or a bare {@code yyyy-MM-dd} (when only
     * a calendar date is known, resolving to the start of that day in
     * {@code AppTimeZone.ZONE}) — see {@link ExchangeRateLookupService#current}
     * for the exact fallback chain. Omitted, blank, or unparseable all mean
     * "right now".
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentExchangeRateDto> current(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) String asOf) {
        return ResponseEntity.ok(service.current(base, quote, asOf));
    }
}
