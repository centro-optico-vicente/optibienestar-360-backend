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
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optibienestar360.modules.catalog.repository.MaritalStatusRepository;
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
public class MaritalStatusService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(MaritalStatus.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};

    private final MaritalStatusRepository repository;
    private final PersonRepository personRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private MaritalStatusService self;

    public Page<MaritalStatusDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "marital_status", pageable, new SortOrder("createdAt", "DESC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "marital_status");
        Specification<MaritalStatus> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "marital_status.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(MaritalStatusService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("marital_status", pageable, new SortOrder("createdAt", "DESC"));
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<MaritalStatus> spec = ((Specification<MaritalStatus>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                MaritalStatus::getUuid, MaritalStatus::getCode, MaritalStatusService::labelOf, MaritalStatus::isActive);
    }

    private static String labelOf(MaritalStatus m) {
        return m.getCode() + " — " + m.getName();
    }

    @Cacheable(value = "catalogs", key = "'marital_status:all'")
    public List<MaritalStatusDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(MaritalStatusService::toDto)
                .toList();
    }

    public MaritalStatusDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "marital_status", action = AuditAction.CREATE)
    public MaritalStatusDto create(MaritalStatusCreateRequest req) {
        MaritalStatus m = new MaritalStatus();
        m.setCode(req.code());
        m.setName(req.name());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "marital_status", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public MaritalStatusDto update(UUID uuid, MaritalStatusUpdateRequest req) {
        MaritalStatus m = find(uuid);
        m.setName(req.name());
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return toDto(repository.save(m));
    }

    public long countUsages(UUID uuid) {
        return personRepository.countByMaritalStatus_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "marital_status", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        MaritalStatus m = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(m);
            return;
        }
        m.setActive(false);
        repository.save(m);
    }

    private MaritalStatus find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("MaritalStatus not found: " + uuid));
    }

    static MaritalStatusDto toDto(MaritalStatus m) {
        return new MaritalStatusDto(m.getUuid(), m.getCode(), m.getName(), m.isActive());
    }
}
