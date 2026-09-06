package com.fenixcore.optibienestar360.modules.exchangerate;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateCreateRequest;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateDto;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateUpdateRequest;
import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateIngestionService;
import com.fenixcore.optibienestar360.modules.exchangerate.service.ExchangeRateService;
import com.fenixcore.optibienestar360.modules.exchangerate.service.IngestionSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin surface over {@code exchange_rates} (ADR 0015 §2/§7). Stop-gap manual
 * entry point so the conversion service has something real to read in any
 * environment before {@code FetchExchangeRatesJob} ships (Tarea 2.13).
 * {@code PUT}/{@code DELETE} only ever apply to {@code source = MANUAL} rows
 * — see {@link ExchangeRateService} for why ingested rows stay immutable.
 */
@RestController
@RequestMapping("/v1/admin/exchange-rates")
@RequiredArgsConstructor
public class AdminExchangeRateController {

    private final ExchangeRateService service;
    private final ExchangeRateIngestionService ingestionService;

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

    /**
     * Quick action: trigger the same ingestion {@code FetchExchangeRatesJob}
     * runs daily, synchronously, on demand — "get me the latest rate right
     * now" (ADR 0015 §3). Delegates to
     * {@link ExchangeRateIngestionService#fetchAndStoreLatest()}, the exact
     * same call site the job runner uses, so there is no duplicated
     * ingestion logic between the scheduled path and this convenience
     * endpoint. The generic {@code POST
     * /v1/admin/scheduled-jobs/{uuid}/run-now} can also run this job once
     * seeded — this endpoint just saves an operator from finding it there.
     *
     * <p>Reuses {@code EXCHANGE_RATE_CREATE} (this endpoint results in new
     * {@code exchange_rates} rows, same as manual entry) rather than adding
     * a new permission. {@link com.fenixcore.optibienestar360.modules.exchangerate.client.ExchangeRatesApiClient}
     * keeps tight connect/read timeouts so a slow/unreachable upstream
     * cannot hang this request indefinitely.</p>
     */
    @PostMapping("/fetch-latest")
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_CREATE')")
    public ResponseEntity<IngestionSummary> fetchLatest() {
        return ResponseEntity.ok(ingestionService.fetchAndStoreLatest());
    }

    /** Correct a {@code MANUAL} row — rejects any other {@code source} (see {@link ExchangeRateService#update}). */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_UPDATE')")
    public ResponseEntity<ExchangeRateDto> update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody ExchangeRateUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    /** Soft-delete a {@code MANUAL} row — rejects any other {@code source} (see {@link ExchangeRateService#delete}). */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('EXCHANGE_RATE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
