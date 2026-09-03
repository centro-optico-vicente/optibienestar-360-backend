package com.fenixcore.optibienestar360.modules.catalog.service;

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
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Gender;
import com.fenixcore.optibienestar360.modules.catalog.repository.GenderRepository;
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
public class GenderService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(Gender.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};

    private final GenderRepository repository;
    private final PersonRepository personRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private GenderService self;

    public Page<GenderDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "gender", pageable, new SortOrder("name", "ASC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "gender");
        Specification<Gender> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "gender.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(GenderService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("gender", pageable, new SortOrder("name", "ASC"));
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Gender> spec = ((Specification<Gender>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Gender::getUuid, Gender::getCode, GenderService::labelOf, Gender::isActive);
    }

    private static String labelOf(Gender g) {
        return g.getCode() + " — " + g.getName();
    }

    @Cacheable(value = "catalogs", key = "'gender:all'")
    public List<GenderDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(GenderService::toDto)
                .toList();
    }

    public GenderDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "gender", action = AuditAction.CREATE)
    public GenderDto create(GenderCreateRequest req) {
        Gender g = new Gender();
        g.setCode(req.code());
        g.setName(req.name());
        return toDto(repository.save(g));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "gender", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public GenderDto update(UUID uuid, GenderUpdateRequest req) {
        Gender g = find(uuid);
        g.setName(req.name());
        if (req.active() != null) {
            g.setActive(req.active());
        }
        return toDto(repository.save(g));
    }

    public long countUsages(UUID uuid) {
        return personRepository.countByGender_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "gender", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Gender g = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(g);
            return;
        }
        g.setActive(false);
        repository.save(g);
    }

    private Gender find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Gender not found: " + uuid));
    }

    static GenderDto toDto(Gender g) {
        return new GenderDto(g.getUuid(), g.getCode(), g.getName(), g.isActive());
    }
}
