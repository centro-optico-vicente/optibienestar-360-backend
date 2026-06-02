package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.core.util.ListQuery;
import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.DocumentTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.DocumentType;
import com.fenixcore.optisaludplus.modules.catalog.repository.DocumentTypeRepository;
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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentTypeService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final DocumentTypeRepository repository;

    @Autowired @Lazy
    private DocumentTypeService self;

    public Page<DocumentTypeDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<DocumentType> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "document_type.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(DocumentTypeService::toDto);
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
    public DocumentTypeDto create(DocumentTypeCreateRequest req) {
        DocumentType d = new DocumentType();
        d.setCode(req.code());
        d.setName(req.name());
        d.setDescription(req.description());
        return toDto(repository.save(d));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public DocumentTypeDto update(UUID uuid, DocumentTypeUpdateRequest req) {
        DocumentType d = find(uuid);
        d.setName(req.name());
        d.setDescription(req.description());
        return toDto(repository.save(d));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        DocumentType d = find(uuid);
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
