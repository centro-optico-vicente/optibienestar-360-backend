package com.fenixcore.optibienestar360.modules.promoter.service;

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
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
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
 * Admin CRUD over {@code promoter_ranks} (V101) — same shape as {@code
 * PromoterTypeService}. {@code hierarchyLevel} (like {@code code}) is
 * immutable after creation: it is the comparison key the whole hierarchy
 * engine relies on, so silently reordering it under an existing supervisor
 * chain would be a correctness hazard, not a cosmetic edit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterRankService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("code", "name", "hierarchyLevel", "description");
    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(PromoterRank.class, Map.of());

    private final PromoterRankRepository repository;
    private final DefaultSortResolver defaultSortResolver;

    @Autowired
    @Lazy
    private PromoterRankService self;

    public Page<PromoterRankDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        if (!includeInactive && ListQuery.isUnfilteredUnpaged(pageable, filter, q)) {
            return new PageImpl<>(self.loadAllForDropdown());
        }
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("promoter_rank", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "promoter_rank");
        Specification<PromoterRank> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "promoter_rank.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(PromoterRankService::toDto);
    }

    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("promoter_rank", pageable);
    }

    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<PromoterRank> spec = ((Specification<PromoterRank>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                PromoterRank::getUuid, PromoterRank::getCode, PromoterRankService::labelOf, PromoterRank::isActive);
    }

    private static String labelOf(PromoterRank r) {
        return r.getCode() + " — " + r.getName();
    }

    @Cacheable(value = "catalogs", key = "'promoter_rank:all'")
    public List<PromoterRankDto> loadAllForDropdown() {
        return repository.findAllByActiveTrueOrderByHierarchyLevel().stream()
                .map(PromoterRankService::toDto)
                .toList();
    }

    public PromoterRankDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "promoter_rank", action = AuditAction.CREATE)
    public PromoterRankDto create(PromoterRankCreateRequest req) {
        if (repository.existsByHierarchyLevel(req.hierarchyLevel())) {
            throw new IllegalArgumentException("promoter_rank.hierarchy_level.taken");
        }
        PromoterRank r = new PromoterRank();
        r.setCode(req.code());
        r.setName(req.name());
        r.setHierarchyLevel(req.hierarchyLevel());
        r.setMaxSubordinates(req.maxSubordinates());
        r.setDescription(req.description());
        return toDto(repository.save(r));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "promoter_rank", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PromoterRankDto update(UUID uuid, PromoterRankUpdateRequest req) {
        PromoterRank r = find(uuid);
        r.setName(req.name());
        r.setMaxSubordinates(req.maxSubordinates());
        r.setDescription(req.description());
        return toDto(repository.save(r));
    }

    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "promoter_rank", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        PromoterRank r = find(uuid);
        r.setActive(false);
        repository.save(r);
    }

    PromoterRank find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("PromoterRank not found: " + uuid));
    }

    static PromoterRankDto toDto(PromoterRank r) {
        return new PromoterRankDto(r.getUuid(), r.getCode(), r.getName(), r.getHierarchyLevel(),
                r.getMaxSubordinates(), r.getDescription(), r.isActive());
    }
}
