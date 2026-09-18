package com.fenixcore.optibienestar360.modules.bank.service;

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
import com.fenixcore.optibienestar360.modules.bank.dto.BankCreateRequest;
import com.fenixcore.optibienestar360.modules.bank.dto.BankDto;
import com.fenixcore.optibienestar360.modules.bank.dto.BankUpdateRequest;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
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
 * Admin CRUD over {@code banks} (V116, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Same shape as
 * {@code CurrencyService} — soft-delete only, {@code code} immutable after
 * creation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "shortName");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "shortName"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Bank.class, Map.of());

    private final BankRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired
    @Lazy
    private BankService self;

    public Page<BankDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("bank", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "bank");
        Specification<Bank> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "bank.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(BankService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("bank", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Bank> spec = ((Specification<Bank>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Bank::getUuid, Bank::getCode, BankService::labelOf, Bank::isActive);
    }

    private static String labelOf(Bank b) {
        return b.getCode() + " — " + b.getShortName();
    }

    @Cacheable(value = "catalogs", key = "'bank:all'")
    public List<BankDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(BankService::toDto)
                .toList();
    }

    public BankDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "bank", action = AuditAction.CREATE)
    public BankDto create(BankCreateRequest req) {
        Bank b = new Bank();
        b.setCode(req.code());
        b.setName(req.name());
        b.setShortName(req.shortName());
        b.setTaxDocumentType(req.taxDocumentType());
        b.setTaxDocumentNumber(req.taxDocumentNumber());
        return toDto(repository.save(b));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "bank", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public BankDto update(UUID uuid, BankUpdateRequest req) {
        Bank b = find(uuid);
        b.setName(req.name());
        b.setShortName(req.shortName());
        b.setTaxDocumentType(req.taxDocumentType());
        b.setTaxDocumentNumber(req.taxDocumentNumber());
        if (req.active() != null) {
            b.setActive(req.active());
        }
        return toDto(repository.save(b));
    }

    /** Soft-delete only — {@code payment_lines.bank_id} references this catalog. */
    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "bank", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        Bank b = find(uuid);
        b.setActive(false);
        repository.save(b);
    }

    private Bank find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Bank not found: " + uuid));
    }

    static BankDto toDto(Bank b) {
        return new BankDto(b.getUuid(), b.getCode(), b.getName(), b.getShortName(),
                b.getTaxDocumentType(), b.getTaxDocumentNumber(), b.isActive());
    }
}
