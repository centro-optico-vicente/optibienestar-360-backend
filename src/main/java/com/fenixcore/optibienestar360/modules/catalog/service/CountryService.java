package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.StateRepository;
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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("isoCode", "name");
    private static final String[] SEARCHABLE_FIELDS = {"isoCode", "name"};

    private final CountryRepository repository;
    private final StateRepository stateRepository;

    @Autowired
    @Lazy
    private CountryService self;

    public Page<CountryDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<Country> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "country.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(CountryService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Country> spec = activeOnly().and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Country::getUuid, Country::getIsoCode, CountryService::labelOf, Country::isActive);
    }

    private static String labelOf(Country c) {
        return c.getIsoCode() + " — " + c.getName();
    }

    @Cacheable(value = "catalogs", key = "'country:all'")
    public List<CountryDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(CountryService::toDto)
                .toList();
    }

    public CountryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "country", action = AuditAction.CREATE)
    public CountryDto create(CountryCreateRequest req) {
        Country c = new Country();
        c.setIsoCode(req.isoCode());
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "country", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CountryDto update(UUID uuid, CountryUpdateRequest req) {
        Country c = find(uuid);
        c.setName(req.name());
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return toDto(repository.save(c));
    }

    public long countUsages(UUID uuid) {
        return stateRepository.countByCountry_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "country", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Country c = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(c);
            return;
        }
        c.setActive(false);
        repository.save(c);
    }

    private Country find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Country not found: " + uuid));
    }

    private static Specification<Country> activeOnly() {
        return (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
    }

    static CountryDto toDto(Country c) {
        return new CountryDto(c.getUuid(), c.getIsoCode(), c.getName(), c.getLocale(), c.isActive());
    }
}
