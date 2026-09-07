package com.fenixcore.optibienestar360.modules.exchangerate.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateCreateRequest;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateDto;
import com.fenixcore.optibienestar360.modules.exchangerate.dto.ExchangeRateUpdateRequest;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate;
import com.fenixcore.optibienestar360.modules.exchangerate.repository.ExchangeRateRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin surface over {@code exchange_rates} (ADR 0015 §2/§7). Ingested rows
 * ({@code source = BCV}/{@code EXCHANGE_RATES_API}) are historical fact and
 * stay immutable — {@link #update}/{@link #delete} only ever touch
 * {@code source = MANUAL} rows, so a correction of an actual ingestion is
 * still a new row with a later {@code validFrom}, same as a correcting
 * journal entry; a mistaken manual entry can just be fixed or removed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "rate", "operationDate", "validFrom", "source", "fetchedAt",
            "createdAt", "active", "baseCurrency.code", "quoteCurrency.code"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(ExchangeRate.class, Map.of());

    private final ExchangeRateRepository repository;
    private final CurrencyRepository currencyRepository;
    private final DefaultSortResolver defaultSortResolver;

    public Page<ExchangeRateDto> list(Pageable pageable, String base, String quote, String filter) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("exchange_rate", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "exchange_rate");

        Specification<ExchangeRate> spec = (root, query, cb) -> cb.conjunction();
        if (base != null && !base.isBlank()) {
            Currency baseCurrency = resolveCurrency(base);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("baseCurrency"), baseCurrency));
        }
        if (quote != null && !quote.isBlank()) {
            Currency quoteCurrency = resolveCurrency(quote);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("quoteCurrency"), quoteCurrency));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "exchange_rate.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }

        return repository.findAll(spec, resolvedPageable).map(ExchangeRateService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("exchange_rate", pageable);
    }

    @Transactional
    public ExchangeRateDto create(ExchangeRateCreateRequest req) {
        Currency base = resolveCurrency(req.baseCurrencyCode());
        Currency quote = resolveCurrency(req.quoteCurrencyCode());
        if (base.getId().equals(quote.getId())) {
            throw new IllegalArgumentException("exchange_rate.currencies_distinct");
        }

        ExchangeRate rate = new ExchangeRate();
        rate.setBaseCurrency(base);
        rate.setQuoteCurrency(quote);
        rate.setRate(req.rate());
        rate.setOperationDate(req.operationDate());
        // Manual entry defaults to immediately usable when validFrom is
        // omitted; an admin can still explicitly backdate/schedule it
        // (see ExchangeRateCreateRequest javadoc for why this differs from
        // operationDate).
        rate.setValidFrom(req.validFrom() != null ? req.validFrom() : Instant.now());
        rate.setSource(ExchangeRate.Source.MANUAL);
        rate.setFetchedAt(Instant.now());
        rate.setStatus("ACTIVE");

        return toDto(repository.save(rate));
    }

    /**
     * Correct a {@code MANUAL} row's {@code rate}/{@code operationDate}/
     * {@code validFrom} — PATCH-style, only non-null fields are applied.
     * Rejects any row not {@code source = MANUAL}: ingested rows are
     * historical fact (see class javadoc).
     */
    @Transactional
    public ExchangeRateDto update(UUID uuid, ExchangeRateUpdateRequest req) {
        ExchangeRate rate = requireManual(find(uuid));
        if (req.rate() != null) {
            rate.setRate(req.rate());
        }
        if (req.operationDate() != null) {
            rate.setOperationDate(req.operationDate());
        }
        if (req.validFrom() != null) {
            rate.setValidFrom(req.validFrom());
        }
        return toDto(repository.save(rate));
    }

    /** Soft-delete a {@code MANUAL} row — same as the rest of the catalog pattern. */
    @Transactional
    public void delete(UUID uuid) {
        ExchangeRate rate = requireManual(find(uuid));
        rate.setActive(false);
        repository.save(rate);
    }

    private static ExchangeRate requireManual(ExchangeRate rate) {
        if (rate.getSource() != ExchangeRate.Source.MANUAL) {
            throw new IllegalArgumentException("exchange_rate.immutable_source");
        }
        return rate;
    }

    private ExchangeRate find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ExchangeRate not found: " + uuid));
    }

    private Currency resolveCurrency(String code) {
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private static ExchangeRateDto toDto(ExchangeRate r) {
        return new ExchangeRateDto(
                r.getUuid(),
                r.getBaseCurrency().getCode(),
                r.getQuoteCurrency().getCode(),
                r.getRate(),
                r.getOperationDate(),
                r.getValidFrom(),
                r.getSource(),
                r.getFetchedAt(),
                r.isActive(),
                r.getCreatedAt());
    }
}
