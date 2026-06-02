package com.fenixcore.optisaludplus.modules.catalog.service;

import com.fenixcore.optisaludplus.core.util.ListQuery;
import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import com.fenixcore.optisaludplus.modules.catalog.repository.AllyTypeRepository;
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
public class AllyTypeService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final AllyTypeRepository repository;

    @Autowired @Lazy
    private AllyTypeService self;

    public Page<AllyTypeDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<AllyType> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "ally_type.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(AllyTypeService::toDto);
    }

    @Cacheable(value = "catalogs", key = "'ally_type:all'")
    public List<AllyTypeDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(AllyTypeService::toDto)
                .toList();
    }

    public AllyTypeDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public AllyTypeDto create(AllyTypeCreateRequest req) {
        AllyType a = new AllyType();
        a.setCode(req.code());
        a.setName(req.name());
        a.setDescription(req.description());
        return toDto(repository.save(a));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public AllyTypeDto update(UUID uuid, AllyTypeUpdateRequest req) {
        AllyType a = find(uuid);
        a.setName(req.name());
        a.setDescription(req.description());
        return toDto(repository.save(a));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        AllyType a = find(uuid);
        a.setActive(false);
        repository.save(a);
    }

    private AllyType find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("AllyType not found: " + uuid));
    }

    static AllyTypeDto toDto(AllyType a) {
        return new AllyTypeDto(a.getUuid(), a.getCode(), a.getName(), a.getDescription(), a.isActive());
    }
}
