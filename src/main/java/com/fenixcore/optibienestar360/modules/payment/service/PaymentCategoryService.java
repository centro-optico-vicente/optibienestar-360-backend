package com.fenixcore.optibienestar360.modules.payment.service;

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
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCategoryUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
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

/**
 * Admin CRUD over {@code payment_categories} (V115, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Same shape as
 * {@code CurrencyService} — soft-delete only, {@code code} immutable after
 * creation; {@code direction} IS editable (unlike code), a category can be
 * re-scoped IN/OUT before anything references it in practice.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCategoryService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "direction");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(PaymentCategory.class, Map.of());

    private final PaymentCategoryRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired
    @Lazy
    private PaymentCategoryService self;

    public Page<PaymentCategoryDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("payment_category", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "payment_category");
        Specification<PaymentCategory> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "payment_category.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(PaymentCategoryService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("payment_category", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<PaymentCategory> spec = ((Specification<PaymentCategory>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                PaymentCategory::getUuid, PaymentCategory::getCode, PaymentCategoryService::labelOf, PaymentCategory::isActive);
    }

    private static String labelOf(PaymentCategory c) {
        return c.getName();
    }

    @Cacheable(value = "catalogs", key = "'payment_category:all'")
    public List<PaymentCategoryDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(PaymentCategoryService::toDto)
                .toList();
    }

    public PaymentCategoryDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_category", action = AuditAction.CREATE)
    public PaymentCategoryDto create(PaymentCategoryCreateRequest req) {
        PaymentCategory c = new PaymentCategory();
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setDirection(req.direction());
        return toDto(repository.save(c));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_category", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentCategoryDto update(UUID uuid, PaymentCategoryUpdateRequest req) {
        PaymentCategory c = find(uuid);
        c.setName(req.name());
        c.setDescription(req.description());
        c.setDirection(req.direction());
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return toDto(repository.save(c));
    }

    /** Soft-delete only — {@code payments.payment_type_id} references this catalog. */
    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_category", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        PaymentCategory c = find(uuid);
        c.setActive(false);
        repository.save(c);
    }

    private PaymentCategory find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("PaymentCategory not found: " + uuid));
    }

    static PaymentCategoryDto toDto(PaymentCategory c) {
        return new PaymentCategoryDto(c.getUuid(), c.getCode(), c.getName(), c.getDescription(), c.getDirection(), c.isActive());
    }
}
