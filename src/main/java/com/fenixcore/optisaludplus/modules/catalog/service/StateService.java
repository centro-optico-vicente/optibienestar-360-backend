package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.core.util.ListQuery;
import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.Country;
import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import com.fenixcore.optisaludplus.modules.catalog.repository.CountryRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.StateRepository;
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
public class StateService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "country");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};

    private final StateRepository repository;
    private final CountryRepository countryRepository;

    @Autowired @Lazy
    private StateService self;

    public Page<StateDto> list(Pageable pageable, String filter, String q, String countryIsoCode) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q, countryIsoCode)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<State> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "state.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (countryIsoCode != null && !countryIsoCode.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("country").get("isoCode"), countryIsoCode));
        }
        return repository.findAll(spec, pageable).map(StateService::toDto);
    }

    @Cacheable(value = "catalogs", key = "'state:all'")
    public List<StateDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(StateService::toDto)
                .toList();
    }

    public StateDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public StateDto create(StateCreateRequest req) {
        Country country = countryRepository.findByUuid(req.countryUuid())
                .orElseThrow(() -> new NoSuchElementException("Country not found: " + req.countryUuid()));
        State s = new State();
        s.setCountry(country);
        s.setCode(req.code());
        s.setName(req.name());
        return toDto(repository.save(s));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public StateDto update(UUID uuid, StateUpdateRequest req) {
        State s = find(uuid);
        s.setName(req.name());
        return toDto(repository.save(s));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        State s = find(uuid);
        s.setActive(false);
        repository.save(s);
    }

    private State find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("State not found: " + uuid));
    }

    static StateDto toDto(State s) {
        Country c = s.getCountry();
        return new StateDto(s.getUuid(), s.getCode(), s.getName(),
                c.getUuid(), c.getIsoCode(), s.isActive());
    }
}
