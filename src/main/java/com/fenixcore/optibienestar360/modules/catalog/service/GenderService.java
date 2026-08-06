package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.Gender;
import com.fenixcore.optibienestar360.modules.catalog.repository.GenderRepository;
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
public class GenderService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};

    private final GenderRepository repository;

    @Autowired @Lazy
    private GenderService self;

    public Page<GenderDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<Gender> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "gender.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(GenderService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Gender> spec = ((Specification<Gender>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Gender::getUuid, Gender::getCode, GenderService::labelOf, Gender::isActive);
    }

    private static String labelOf(Gender g) {
        return g.getCode() + " — " + g.getName();
    }

    @Cacheable(value = "catalogs", key = "'gender:all'")
    public List<GenderDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(GenderService::toDto)
                .toList();
    }

    public GenderDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public GenderDto create(GenderCreateRequest req) {
        Gender g = new Gender();
        g.setCode(req.code());
        g.setName(req.name());
        return toDto(repository.save(g));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public GenderDto update(UUID uuid, GenderUpdateRequest req) {
        Gender g = find(uuid);
        g.setName(req.name());
        return toDto(repository.save(g));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        Gender g = find(uuid);
        g.setActive(false);
        repository.save(g);
    }

    private Gender find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Gender not found: " + uuid));
    }

    static GenderDto toDto(Gender g) {
        return new GenderDto(g.getUuid(), g.getCode(), g.getName(), g.isActive());
    }
}
