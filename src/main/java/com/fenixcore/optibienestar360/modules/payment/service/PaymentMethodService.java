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
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
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
 * Admin CRUD over {@code payment_methods} (V115, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Same shape as
 * {@code CurrencyService} — soft-delete only, {@code code} immutable after
 * creation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentMethodService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(PaymentMethod.class, Map.of());

    private final PaymentMethodRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired
    @Lazy
    private PaymentMethodService self;

    public Page<PaymentMethodDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("payment_method", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "payment_method");
        Specification<PaymentMethod> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "payment_method.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(PaymentMethodService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("payment_method", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<PaymentMethod> spec = ((Specification<PaymentMethod>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                PaymentMethod::getUuid, PaymentMethod::getCode, PaymentMethodService::labelOf, PaymentMethod::isActive);
    }

    private static String labelOf(PaymentMethod m) {
        return m.getName();
    }

    @Cacheable(value = "catalogs", key = "'payment_method:all'")
    public List<PaymentMethodDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(PaymentMethodService::toDto)
                .toList();
    }

    public PaymentMethodDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_method", action = AuditAction.CREATE)
    public PaymentMethodDto create(PaymentMethodCreateRequest req) {
        PaymentMethod m = new PaymentMethod();
        m.setCode(req.code());
        m.setName(req.name());
		m.setDescription(req.description());
		m.setMandatoryIdentification(req.mandatoryIdentification());
		m.setMandatoryBank(req.mandatoryBank());
        m.setMandatoryBankAccount(req.mandatoryBankAccount());
		m.setMandatoryAccountType(req.mandatoryAccountType());
		m.setMandatoryAccountCode(req.mandatoryAccountCode());
        m.setMandatoryPhone(req.mandatoryPhone());
        m.setMandatoryEmail(req.mandatoryEmail());
        m.setMandatoryReferenceNumber(req.mandatoryReferenceNumber());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_method", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentMethodDto update(UUID uuid, PaymentMethodUpdateRequest req) {
        PaymentMethod m = find(uuid);
        m.setName(req.name());
		m.setDescription(req.description());
		m.setMandatoryIdentification(req.mandatoryIdentification());
		m.setMandatoryBank(req.mandatoryBank());
        m.setMandatoryBankAccount(req.mandatoryBankAccount());
		m.setMandatoryAccountType(req.mandatoryAccountType());
		m.setMandatoryAccountCode(req.mandatoryAccountCode());
        m.setMandatoryPhone(req.mandatoryPhone());
        m.setMandatoryEmail(req.mandatoryEmail());
        m.setMandatoryReferenceNumber(req.mandatoryReferenceNumber());
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return toDto(repository.save(m));
    }

    /** Soft-delete only — {@code payment_lines.payment_type_id} references this catalog. */
    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "payment_method", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        PaymentMethod m = find(uuid);
        m.setActive(false);
        repository.save(m);
    }

    private PaymentMethod find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("PaymentMethod not found: " + uuid));
    }

    static PaymentMethodDto toDto(PaymentMethod m) {
        return new PaymentMethodDto(
			m.getUuid(), m.getCode(), m.getName(), m.getDescription(),
			m.isMandatoryIdentification(), m.isMandatoryBank(), m.isMandatoryBankAccount(), m.isMandatoryAccountType(),
			m.isMandatoryAccountCode(), m.isMandatoryPhone(), m.isMandatoryEmail(), m.isMandatoryReferenceNumber(),
			m.isActive()
		);
    }
}
