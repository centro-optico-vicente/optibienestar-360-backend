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
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProfessionRepository;
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
public class ProfessionService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(Profession.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final ProfessionRepository repository;
    private final AllyRepository allyRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private ProfessionService self;

    public Page<ProfessionDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "profession", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "profession");
        Specification<Profession> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "profession.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(ProfessionService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("profession", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Profession> spec = ((Specification<Profession>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Profession::getUuid, Profession::getCode, ProfessionService::labelOf, Profession::isActive);
    }

    private static String labelOf(Profession m) {
        return m.getCode() + " — " + m.getName();
    }

    @Cacheable(value = "catalogs", key = "'profession:all'")
    public List<ProfessionDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(ProfessionService::toDto)
                .toList();
    }

    public ProfessionDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "profession", action = AuditAction.CREATE)
    public ProfessionDto create(ProfessionCreateRequest req) {
        Profession m = new Profession();
        m.setCode(req.code());
        m.setName(req.name());
        m.setDescription(req.description());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "profession", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public ProfessionDto update(UUID uuid, ProfessionUpdateRequest req) {
        Profession m = find(uuid);
        m.setName(req.name());
        m.setDescription(req.description());
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return toDto(repository.save(m));
    }

    public long countUsages(UUID uuid) {
        return allyRepository.countByProfessions_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "profession", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Profession m = find(uuid);
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

    private Profession find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Profession not found: " + uuid));
    }

    static ProfessionDto toDto(Profession m) {
        return new ProfessionDto(m.getUuid(), m.getCode(), m.getName(), m.getDescription(), m.isActive());
    }
}
