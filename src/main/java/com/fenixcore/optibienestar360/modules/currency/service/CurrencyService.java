package com.fenixcore.optibienestar360.modules.currency.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyCreateRequest;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyDto;
import com.fenixcore.optibienestar360.modules.currency.dto.CurrencyUpdateRequest;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@code currencies} (ADR 0015, plan "CRUD admin de Currency +
 * ExchangeRate"). Same shape as every other catalog ({@code AdminCatalogsController}
 * pattern) except delete: {@code currencies} is referenced by ~15 tables
 * (plans, payments, commissions, memberships, exchange_rates x2, …) so, unlike
 * {@code Gender}/{@code Country}, there is no {@code physical}/{@code countUsages}
 * option here — soft-delete only, same simpler shape {@code PromoterTypeService}
 * already uses.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "symbol");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Currency.class, Map.of());

    private final CurrencyRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired
    @Lazy
    private CurrencyService self;

    public Page<CurrencyDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("currency", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "currency");
        Specification<Currency> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "currency.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(CurrencyService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("currency", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Currency> spec = ((Specification<Currency>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Currency::getUuid, Currency::getCode, CurrencyService::labelOf, Currency::isActive);
    }

    private static String labelOf(Currency c) {
        return c.getCode() + " — " + c.getName();
    }

    @Cacheable(value = "catalogs", key = "'currency:all'")
    public List<CurrencyDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(CurrencyService::toDto)
                .toList();
    }

    public CurrencyDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "currency", action = AuditAction.CREATE)
    public CurrencyDto create(CurrencyCreateRequest req) {
        Currency c = new Currency();
        c.setCode(req.code());
        c.setName(req.name());
        c.setSymbol(req.symbol());
        c.setDecimalPlaces(req.decimalPlaces());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "currency", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CurrencyDto update(UUID uuid, CurrencyUpdateRequest req) {
        Currency c = find(uuid);
        c.setName(req.name());
        c.setSymbol(req.symbol());
        if (req.decimalPlaces() != null) {
            c.setDecimalPlaces(req.decimalPlaces());
        }
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return toDto(repository.save(c));
    }

    /**
     * Soft-delete only — no {@code physical} option. {@code currencies} is
     * referenced by too many tables (plans, payments, commissions, memberships,
     * bonus/prize snapshots, exchange_rates x2, organizations x2, …) for a
     * usage-count guard to add anything a Postgres FK doesn't already enforce;
     * offering "physical delete" on a catalog this foundational is more risk
     * than benefit (see plan's explicit decision).
     */
    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "currency", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        Currency c = find(uuid);
        c.setActive(false);
        repository.save(c);
    }

    private Currency find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Currency not found: " + uuid));
    }

    static CurrencyDto toDto(Currency c) {
        return new CurrencyDto(c.getUuid(), c.getCode(), c.getName(), c.getSymbol(), c.getDecimalPlaces(), c.isActive());
    }
}
