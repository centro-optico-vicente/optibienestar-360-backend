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
import com.fenixcore.optibienestar360.modules.catalog.dto.LicensingBodyCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.LicensingBodyDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.LicensingBodyUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.entity.LicensingBody;
import com.fenixcore.optibienestar360.modules.catalog.repository.LicensingBodyRepository;
import com.fenixcore.optibienestar360.modules.person.repository.PersonProfessionalLicenseRepository;
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
 * Catalog of license-issuing bodies (V213 / hub ADR 0031 §5). Same shape as
 * the other admin catalogs ({@link ProfessionService}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LicensingBodyService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "description", "professionalArea");
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
        SortFieldValidator.sortableFieldsOf(LicensingBody.class, Map.of());
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final LicensingBodyRepository repository;
    private final PersonProfessionalLicenseRepository licenseRepository;

    private final DefaultSortResolver defaultSortResolver;

    @Autowired @Lazy
    private LicensingBodyService self;

    public Page<LicensingBodyDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("licensing_body", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "licensing_body");
        Specification<LicensingBody> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "licensing_body.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(LicensingBodyService::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("licensing_body", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<LicensingBody> spec = ((Specification<LicensingBody>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                LicensingBody::getUuid, LicensingBody::getCode, LicensingBody::getName, LicensingBody::isActive);
    }

    @Cacheable(value = "catalogs", key = "'licensing_body:all'")
    public List<LicensingBodyDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByName().stream()
                .map(LicensingBodyService::toDto)
                .toList();
    }

    public LicensingBodyDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "licensing_body", action = AuditAction.CREATE)
    public LicensingBodyDto create(LicensingBodyCreateRequest req) {
        LicensingBody m = new LicensingBody();
        m.setCode(req.code());
        m.setName(req.name());
        m.setDescription(req.description());
        m.setProfessionalArea(req.professionalArea());
        return toDto(repository.save(m));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "licensing_body", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public LicensingBodyDto update(UUID uuid, LicensingBodyUpdateRequest req) {
        LicensingBody m = find(uuid);
        m.setName(req.name());
        m.setDescription(req.description());
        m.setProfessionalArea(req.professionalArea());
        if (req.active() != null) {
            m.setActive(req.active());
        }
        return toDto(repository.save(m));
    }

    public long countUsages(UUID uuid) {
        return licenseRepository.countByLicensingBody_Uuid(uuid);
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "licensing_body", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        LicensingBody m = find(uuid);
        // physical=true is only honored when truly unused (never trust the client flag).
        if (physical && countUsages(uuid) == 0) {
            repository.delete(m);
            return;
        }
        m.setActive(false);
        repository.save(m);
    }

    private LicensingBody find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("licensing_body.not_found"));
    }

    static LicensingBodyDto toDto(LicensingBody m) {
        return new LicensingBodyDto(m.getUuid(), m.getCode(), m.getName(), m.getDescription(),
                m.getProfessionalArea(), m.isActive());
    }
}
