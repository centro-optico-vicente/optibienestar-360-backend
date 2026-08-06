package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.ListQuery;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optibienestar360.modules.catalog.repository.MaritalStatusRepository;
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
public class MaritalStatusService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name"};

    private final MaritalStatusRepository repository;

    @Autowired @Lazy
    private MaritalStatusService self;

    public Page<MaritalStatusDto> list(Pageable pageable, String filter, String q) {
        if (ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Specification<MaritalStatus> spec = (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "marital_status.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(MaritalStatusService::toDto);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<MaritalStatus> spec = ((Specification<MaritalStatus>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                MaritalStatus::getUuid, MaritalStatus::getCode, MaritalStatusService::labelOf, MaritalStatus::isActive);
    }

    private static String labelOf(MaritalStatus m) {
        return m.getCode() + " — " + m.getName();
    }

    @Cacheable(value = "catalogs", key = "'marital_status:all'")
    public List<MaritalStatusDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(MaritalStatusService::toDto)
                .toList();
    }

    public MaritalStatusDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public MaritalStatusDto create(MaritalStatusCreateRequest req) {
        MaritalStatus m = new MaritalStatus();
        m.setCode(req.code());
        m.setName(req.name());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public MaritalStatusDto update(UUID uuid, MaritalStatusUpdateRequest req) {
        MaritalStatus m = find(uuid);
        m.setName(req.name());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    public void delete(UUID uuid) {
        MaritalStatus m = find(uuid);
        m.setActive(false);
        repository.save(m);
    }

    private MaritalStatus find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("MaritalStatus not found: " + uuid));
    }

    static MaritalStatusDto toDto(MaritalStatus m) {
        return new MaritalStatusDto(m.getUuid(), m.getCode(), m.getName(), m.isActive());
    }
}
