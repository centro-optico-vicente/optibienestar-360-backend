package com.fenixcore.optibienestar360.modules.currency;

import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.service.ExchangeRateLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Generic "rate vigente right now" lookup (ADR 0015 §7) — deliberately NOT
 * under {@code /v1/admin} and carries no {@code @PreAuthorize}: the
 * published BCV/API rate itself is not sensitive (unlike the full
 * history/CRUD over {@code exchange_rates}, gated by {@code EXCHANGE_RATE_*}
 * on {@code AdminExchangeRateController}) — any authenticated user may need
 * it to preview a conversion before saving something (a payment, a
 * commission, an ally service price, ...), regardless of role. Spring
 * Security's default {@code anyRequest().authenticated()} rule already
 * covers "must be logged in"; no further gate is needed here.
 */
@RestController
@RequestMapping("/v1/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateLookupController {

    private final ExchangeRateLookupService service;

    /**
     * {@code date} (ISO {@code yyyy-MM-dd}) previews the rate vigente as of
     * that operation date instead of right now — e.g. registering a payment
     * dated earlier than today should preview the rate that date's
     * settlement will actually snapshot, not today's.
     */
    @GetMapping("/current")
    public ResponseEntity<CurrentExchangeRateDto> current(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.current(base, quote, date));
    }
}
