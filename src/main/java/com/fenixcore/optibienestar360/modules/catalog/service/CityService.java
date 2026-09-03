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
import com.fenixcore.optibienestar360.modules.catalog.dto.CityCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import com.fenixcore.optibienestar360.modules.catalog.repository.CityRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.StateRepository;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
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
public class CityService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("name", "state");
    private static final String[] SEARCHABLE_FIELDS = {"name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(City.class, Map.of(
            "state_Display", "state.name",
            // The catalogs table's "parent" column actually renders `state_Code`, not `state_Display` — alias both.
            "state_Code", "state.code"
        ));

    private final CityRepository repository;
    private final StateRepository stateRepository;
    private final AllyRepository allyRepository;
    private final PersonRepository personRepository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private CityService self;

    public Page<CityDto> list(Pageable pageable, String filter, String q,
                              UUID stateUuid, String stateCode, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q, stateUuid, stateCode)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "city", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "city");
        Specification<City> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "city.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (stateUuid != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("state").get("uuid"), stateUuid));
        } else if (stateCode != null && !stateCode.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("state").get("code"), stateCode));
        }
        return repository.findAll(spec, resolvedPageable).map(CityService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("city", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. {@code code} is always null (City has no own code). */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues, UUID stateUuid) {
        Specification<City> spec = ((Specification<City>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        if (stateUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("state").get("uuid"), stateUuid));
        }
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                City::getUuid, city -> null, City::getName, City::isActive);
    }

    @Cacheable(value = "catalogs", key = "'city:all'")
    public List<CityDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(CityService::toDto)
                .toList();
    }

    public CityDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "city", action = AuditAction.CREATE)
    public CityDto create(CityCreateRequest req) {
        State state = stateRepository.findByUuid(req.stateUuid())
                .orElseThrow(() -> new NoSuchElementException("State not found: " + req.stateUuid()));
        City c = new City();
        c.setState(state);
        c.setName(req.name());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "city", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CityDto update(UUID uuid, CityUpdateRequest req) {
        City c = find(uuid);
        c.setName(req.name());
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return toDto(repository.save(c));
    }

    /** Sums usage across every FK owner — both {@code Ally} and {@code Person} carry a direct {@code city} reference. */
    public long countUsages(UUID uuid) {
        return allyRepository.countByCity_Uuid(uuid) + personRepository.countByCity_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "city", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        City c = find(uuid);
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

    private City find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("City not found: " + uuid));
    }

    static CityDto toDto(City c) {
        State s = c.getState();
        return new CityDto(c.getUuid(), c.getName(),
                s == null ? null : DisplayRef.of(s.getUuid(), s.getCode(), s.getName()),
                c.isActive());
    }
}
