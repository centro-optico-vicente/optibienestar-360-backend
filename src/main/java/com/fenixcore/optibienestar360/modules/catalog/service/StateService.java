package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.StateUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import com.fenixcore.optibienestar360.modules.catalog.repository.CityRepository;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StateService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "country");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(State.class, Map.of(
            "country_Display", "country.name",
            // The catalogs table's "parent" column actually renders `country_Code` (the ISO code), not `country_Display` — alias both.
            "country_Code", "country.isoCode"
        ));

    private final StateRepository repository;
    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private StateService self;

    public Page<StateDto> list(Pageable pageable, String filter, String q, String countryIsoCode, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q, countryIsoCode)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "state", pageable, new SortOrder("name", "ASC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "state");
        Specification<State> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
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
        return repository.findAll(spec, resolvedPageable).map(StateService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("state", pageable, new SortOrder("name", "ASC"));
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues, UUID countryUuid) {
        Specification<State> spec = ((Specification<State>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        if (countryUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("country").get("uuid"), countryUuid));
        }
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                State::getUuid, State::getCode, StateService::labelOf, State::isActive);
    }

    private static String labelOf(State s) {
        return s.getCode() + " — " + s.getName();
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
    @Auditable(entity = "state", action = AuditAction.CREATE)
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
    @Auditable(entity = "state", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public StateDto update(UUID uuid, StateUpdateRequest req) {
        State s = find(uuid);
        s.setName(req.name());
        if (req.active() != null) {
            s.setActive(req.active());
        }
        return toDto(repository.save(s));
    }

    public long countUsages(UUID uuid) {
        return cityRepository.countByState_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "state", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        State s = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(s);
            return;
        }
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
                c == null ? null : DisplayRef.of(c.getUuid(), c.getIsoCode(), c.getName()),
                s.isActive());
    }
}
