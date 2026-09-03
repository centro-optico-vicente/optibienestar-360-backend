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
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.DocumentTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.DocumentType;
import com.fenixcore.optibienestar360.modules.catalog.repository.DocumentTypeRepository;
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
public class DocumentTypeService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(DocumentType.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final DocumentTypeRepository repository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private DocumentTypeService self;

    public Page<DocumentTypeDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
            "document_type", pageable, new SortOrder("createdAt", "DESC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "document_type");
        Specification<DocumentType> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "document_type.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(DocumentTypeService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("document_type", pageable, new SortOrder("createdAt", "DESC"));
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<DocumentType> spec = ((Specification<DocumentType>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                DocumentType::getUuid, DocumentType::getCode, DocumentTypeService::labelOf, DocumentType::isActive);
    }

    private static String labelOf(DocumentType d) {
        return d.getCode() + " — " + d.getName();
    }

    @Cacheable(value = "catalogs", key = "'document_type:all'")
    public List<DocumentTypeDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(DocumentTypeService::toDto)
                .toList();
    }

    public DocumentTypeDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "document_type", action = AuditAction.CREATE)
    public DocumentTypeDto create(DocumentTypeCreateRequest req) {
        DocumentType d = new DocumentType();
        d.setCode(req.code());
        d.setName(req.name());
        d.setDescription(req.description());
        return toDto(repository.save(d));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "document_type", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public DocumentTypeDto update(UUID uuid, DocumentTypeUpdateRequest req) {
        DocumentType d = find(uuid);
        d.setName(req.name());
        d.setDescription(req.description());
        if (req.active() != null) {
            d.setActive(req.active());
        }
        return toDto(repository.save(d));
    }

    /**
     * No entity currently holds a real FK to {@code DocumentType} — {@code Person.documentType}
     * is a raw 2-char code column, not a relation to this catalog. Always 0 until such a
     * relation exists.
     */
    public long countUsages(UUID uuid) {
        return 0L;
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "document_type", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        DocumentType d = find(uuid);
        long usages = countUsages(uuid);
        // physical=true is only honored when truly unused — never trust the client
        // flag blindly, to avoid violating the FK or losing referenced data on a
        // race condition between the "usage" ping and this call.
        if (physical && usages == 0) {
            repository.delete(d);
            return;
        }
        d.setActive(false);
        repository.save(d);
    }

    private DocumentType find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("DocumentType not found: " + uuid));
    }

    static DocumentTypeDto toDto(DocumentType d) {
        return new DocumentTypeDto(d.getUuid(), d.getCode(), d.getName(), d.getDescription(), d.isActive());
    }
}
