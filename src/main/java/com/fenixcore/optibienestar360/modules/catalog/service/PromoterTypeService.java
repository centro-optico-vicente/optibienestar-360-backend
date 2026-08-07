package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.PromoterTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
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
public class PromoterTypeService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final PromoterTypeRepository repository;

    @Autowired @Lazy
    private PromoterTypeService self;

    public Page<PromoterTypeDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<PromoterType> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "promoter_type.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(PromoterTypeService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<PromoterType> spec = ((Specification<PromoterType>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                PromoterType::getUuid, PromoterType::getCode, PromoterTypeService::labelOf, PromoterType::isActive);
    }

    private static String labelOf(PromoterType t) {
        return t.getCode() + " — " + t.getName();
    }

    @Cacheable(value = "catalogs", key = "'promoter_type:all'")
    public List<PromoterTypeDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(PromoterTypeService::toDto)
                .toList();
    }

    public PromoterTypeDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public PromoterTypeDto create(PromoterTypeCreateRequest req) {
        PromoterType t = new PromoterType();
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        return toDto(repository.save(t));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public PromoterTypeDto update(UUID uuid, PromoterTypeUpdateRequest req) {
        PromoterType t = find(uuid);
        t.setName(req.name());
        t.setDescription(req.description());
        return toDto(repository.save(t));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        PromoterType t = find(uuid);
        t.setActive(false);
        repository.save(t);
    }

    PromoterType find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("PromoterType not found: " + uuid));
    }

    static PromoterTypeDto toDto(PromoterType t) {
        return new PromoterTypeDto(t.getUuid(), t.getCode(), t.getName(), t.getDescription(), t.isActive());
    }
}
