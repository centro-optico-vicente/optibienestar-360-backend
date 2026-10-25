package com.fenixcore.optibienestar360.modules.currency;

import com.fenixcore.optibienestar360.core.config.PublicHttpCacheConfig;
import com.fenixcore.optibienestar360.modules.currency.dto.ConvertedAmountDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrentExchangeRateDto;
import com.fenixcore.optibienestar360.modules.currency.dto.PublicCurrencyDto;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyService;
import com.fenixcore.optibienestar360.modules.currency.service.ExchangeRateLookupService;
import com.fenixcore.optibienestar360.modules.organization.dto.OrganizationCurrenciesDto;
import com.fenixcore.optibienestar360.modules.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Anonymous twin of the authenticated currency-conversion lookups
 * ({@link ExchangeRateLookupController}, {@code OrganizationCurrenciesController},
 * {@code GET /v1/me/currencies}) so the public pages (affiliation form, public
 * ally detail) can offer the same converter popover to a visitor without a
 * session. Mounted under {@code /v1/public/**}, which
 * {@code SecurityConfig.PUBLIC_PATHS} already routes through {@code permitAll()}.
 *
 * <p>Everything here is already visible to anyone: the published BCV rate, the
 * list of active currencies and which two the organization works in. Same
 * services as the authenticated endpoints — never a second implementation of
 * the rate resolution — and the same "degrade, never block" contract
 * ({@code available = false} instead of an error).</p>
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicExchangeRateController {

    private final ExchangeRateLookupService lookupService;
    private final CurrencyService currencyService;
    private final OrganizationService organizationService;

    @GetMapping("/exchange-rates/current")
    public ResponseEntity<CurrentExchangeRateDto> current(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) String asOf) {
        return ResponseEntity.ok(lookupService.current(base, quote, asOf));
    }

    @GetMapping("/exchange-rates/convert")
    public ResponseEntity<ConvertedAmountDto> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String asOf) {
        return ResponseEntity.ok(lookupService.convert(amount, from, to, asOf));
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<PublicCurrencyDto>> currencies() {
        List<PublicCurrencyDto> body = currencyService.loadAllForDropdown().stream()
                .map(c -> new PublicCurrencyDto(c.code(), c.name(), c.symbol(), c.decimalPlaces(), true))
                .toList();
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog()).body(body);
    }

    @GetMapping("/organization/currencies")
    public ResponseEntity<OrganizationCurrenciesDto> organizationCurrencies() {
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(organizationService.getCurrencies());
    }
}
