package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ServiceCategoryRepository;
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
public class ServiceCategoryService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(ServiceCategory.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final ServiceCategoryRepository repository;
    private final AllyServiceRepository allyServiceRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private ServiceCategoryService self;

    public Page<ServiceCategoryDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "service_category", pageable, new SortOrder("createdAt", "DESC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "service_category");
        Specification<ServiceCategory> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "service_category.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(ServiceCategoryService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("service_category", pageable, new SortOrder("createdAt", "DESC"));
    }

    @Cacheable(value = "catalogs", key = "'service_category:all'")
    public List<ServiceCategoryDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(ServiceCategoryService::toDto)
                .toList();
    }

    public ServiceCategoryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "service_category", action = AuditAction.CREATE)
    public ServiceCategoryDto create(ServiceCategoryCreateRequest req) {
        ServiceCategory s = new ServiceCategory();
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description());
        return toDto(repository.save(s));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "service_category", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public ServiceCategoryDto update(UUID uuid, ServiceCategoryUpdateRequest req) {
        ServiceCategory s = find(uuid);
        s.setName(req.name());
        s.setDescription(req.description());
        if (req.active() != null) {
            s.setActive(req.active());
        }
        return toDto(repository.save(s));
    }

    public long countUsages(UUID uuid) {
        return allyServiceRepository.countByServiceCategory_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "service_category", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        ServiceCategory s = find(uuid);
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

    private ServiceCategory find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ServiceCategory not found: " + uuid));
    }

    static ServiceCategoryDto toDto(ServiceCategory s) {
        return new ServiceCategoryDto(s.getUuid(), s.getCode(), s.getName(), s.getDescription(), s.isActive());
    }
}
