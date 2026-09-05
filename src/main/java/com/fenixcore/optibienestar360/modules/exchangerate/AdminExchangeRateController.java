package com.fenixcore.optibienestar360.modules.exchangerate;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateCreateRequest;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateDto;
import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Admin surface over {@code exchange_rates} (ADR 0015 §2/§7). Stop-gap manual
 * entry point so the conversion service has something real to read in any
 * environment before {@code FetchExchangeRatesJob} ships (Tarea 2.13) — see
 * {@link ExchangeRateService} for why there's no update/delete.
 */
@RestController
@RequestMapping("/v1/admin/exchange-rates")
@RequiredArgsConstructor
public class AdminExchangeRateController {

    private final ExchangeRateService service;

    /** History for a pair, newest {@code validFrom} first by default — e.g. {@code ?base=USD&quote=VES}. */
    @GetMapping
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<ExchangeRateDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String base,
            @RequestParam(required = false) String quote,
            @RequestParam(required = false) String filter) {
        Page<ExchangeRateDto> page = service.list(pageable, base, quote, filter);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    /** Manual entry — {@code source = MANUAL}, {@code validFrom = now()}. */
    @PostMapping
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_CREATE')")
    public ResponseEntity<ExchangeRateDto> create(@Valid @RequestBody ExchangeRateCreateRequest request) {
        ExchangeRateDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/v1/admin/exchange-rates")
                .build().toUri();
        return ResponseEntity.created(location).body(created);
    }
}
