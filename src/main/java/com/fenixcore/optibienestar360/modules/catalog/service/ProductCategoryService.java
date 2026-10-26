package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProductCategoryCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProductCategoryDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProductCategoryUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProductCategoryRepository;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
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
public class ProductCategoryService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(ProductCategory.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final ProductCategoryRepository repository;
    private final ProductRepository productRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private ProductCategoryService self;

    public Page<ProductCategoryDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "product_category", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "product_category");
        Specification<ProductCategory> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "product_category.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(ProductCategoryService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("product_category", pageable);
    }

    @Cacheable(value = "catalogs", key = "'product_category:all'")
    public List<ProductCategoryDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(ProductCategoryService::toDto)
                .toList();
    }

    public ProductCategoryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "product_category", action = AuditAction.CREATE)
    public ProductCategoryDto create(ProductCategoryCreateRequest req) {
        ProductCategory s = new ProductCategory();
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description());
        return toDto(repository.save(s));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "product_category", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public ProductCategoryDto update(UUID uuid, ProductCategoryUpdateRequest req) {
        ProductCategory s = find(uuid);
        s.setName(req.name());
        s.setDescription(req.description());
        if (req.active() != null) {
            s.setActive(req.active());
        }
        return toDto(repository.save(s));
    }

    /** Products of the master classified under this category (ADR 0026 — offers reach it through them). */
    public long countUsages(UUID uuid) {
        return productRepository.countByProductCategory_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "product_category", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        ProductCategory s = find(uuid);
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

    private ProductCategory find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ProductCategory not found: " + uuid));
    }

    static ProductCategoryDto toDto(ProductCategory s) {
        return new ProductCategoryDto(s.getUuid(), s.getCode(), s.getName(), s.getDescription(), s.isActive());
    }
}
