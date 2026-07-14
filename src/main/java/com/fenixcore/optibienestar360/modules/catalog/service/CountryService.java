package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CountryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.repository.CountryRepository;
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

    @Autowired
    @Lazy
    private CountryService self;

    public Page<CountryDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<Country> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "country.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(CountryService::toDto);
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
    public CountryDto create(CountryCreateRequest req) {
        Country c = new Country();
        c.setIsoCode(req.isoCode());
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public CountryDto update(UUID uuid, CountryUpdateRequest req) {
        Country c = find(uuid);
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        Country c = find(uuid);
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
