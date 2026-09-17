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
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankReorderRequest;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@code promoter_ranks} (V101) — same shape as {@code
 * PromoterTypeService}. {@code hierarchyLevel} is never accepted verbatim
 * from an editor's PATCH — {@code code} and, once assigned, the numeric
 * {@code hierarchyLevel} are immutable via {@link #update}; the ordering
 * itself is only changed through {@link #reorder} (V111), which owns the
 * gap-based renumbering strategy so the comparison key the whole hierarchy
 * engine relies on is never left inconsistent mid-operation. {@link #create}
 * uses the same gap logic when the requested {@code hierarchyLevel} collides
 * with an existing active rank, instead of rejecting the request.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterRankService {

    /** Gap used when reseeding/renumbering all active ranks (V111 — matches the migration's reseed). */
    private static final int RENUMBER_GAP = 10;

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

    /**
     * @param excludeUuid optional — filters out this rank (e.g. the
     *                     promoter's current one) from the "pick the new
     *                     rank" step of the change-rank flow, since
     *                     changing to the same rank isn't a change.
     */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues, UUID excludeUuid) {
        Specification<PromoterRank> spec = ((Specification<PromoterRank>) (root, query, cb) -> cb.isTrue(root.get("active")))
                .and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        if (excludeUuid != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("uuid"), excludeUuid));
        }
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
        int level = repository.existsByHierarchyLevel(req.hierarchyLevel())
                ? insertLevelBelow(req.hierarchyLevel())
                : req.hierarchyLevel();

        PromoterRank r = new PromoterRank();
        r.setCode(req.code());
        r.setName(req.name());
        r.setHierarchyLevel(level);
        r.setMaxSubordinates(req.maxSubordinates());
        r.setDescription(req.description());

        if (req.parentRankUuid() != null) {
            PromoterRank parent = repository.findByUuid(req.parentRankUuid())
                    .orElseThrow(() -> new NoSuchElementException("promoter_rank.parent.not_found"));
            if (parent.getHierarchyLevel() <= level) {
                throw new IllegalArgumentException("promoter_rank.parent.not_higher");
            }
            r.setParentRank(parent);
        }
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
        r.setParentRank(resolveAndValidateParent(r, req.parentRankUuid()));
        return toDto(repository.save(r));
    }

    /**
     * Moves {@code rankUuid} to the position immediately after {@code
     * req.afterRankUuid()} (or the very beginning, if {@code null}) in the
     * active-ranks list ordered by {@code hierarchyLevel} (V111). Uses a
     * gap-based midpoint when there is room, renumbering all active ranks in
     * gaps of {@value #RENUMBER_GAP} first when there is not — both happen in
     * this single transaction, relying on the {@code hierarchy_level} unique
     * constraint being {@code DEFERRABLE INITIALLY DEFERRED} (V111). Rejects
     * the whole move — no cascading reassignment — if it would break the
     * parent/child invariant for the moved rank or any of its direct
     * children.
     */
    @Transactional
    @CacheEvict(value = "catalogs", allEntries = true)
    @Auditable(entity = "promoter_rank", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public List<PromoterRankDto> reorder(UUID rankUuid, PromoterRankReorderRequest req) {
        PromoterRank target = find(rankUuid);
        List<PromoterRank> ordered = repository.findAllByActiveTrueOrderByHierarchyLevel().stream()
                .filter(r -> !r.getId().equals(target.getId()))
                .toList();

        PromoterRank afterRank = null;
        if (req.afterRankUuid() != null) {
            afterRank = ordered.stream()
                    .filter(r -> r.getUuid().equals(req.afterRankUuid()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"));
        }
        int index = afterRank == null ? -1 : ordered.indexOf(afterRank);
        PromoterRank nextRank = (index + 1 < ordered.size()) ? ordered.get(index + 1) : null;

        OptionalInt firstAttempt = midpoint(
                afterRank == null ? null : afterRank.getHierarchyLevel(),
                nextRank == null ? null : nextRank.getHierarchyLevel());

        int newLevel;
        if (firstAttempt.isPresent()) {
            newLevel = firstAttempt.getAsInt();
        } else {
            renumberActiveRanksInGapsOfTen();
            Integer afterLevel = afterRank == null ? null : repository.findById(afterRank.getId())
                    .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"))
                    .getHierarchyLevel();
            Integer nextLevel = nextRank == null ? null : repository.findById(nextRank.getId())
                    .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"))
                    .getHierarchyLevel();
            newLevel = midpoint(afterLevel, nextLevel)
                    .orElseThrow(() -> new IllegalStateException("promoter_rank.reorder.no_room_after_renumber"));
        }

        if (target.getParentRank() != null && target.getParentRank().getHierarchyLevel() <= newLevel) {
            throw new IllegalArgumentException("promoter_rank.reorder.parent_conflict");
        }
        for (PromoterRank child : repository.findAllByParentRankAndActiveTrue(target)) {
            if (child.getHierarchyLevel() >= newLevel) {
                throw new IllegalArgumentException("promoter_rank.reorder.child_conflict");
            }
        }

        target.setHierarchyLevel(newLevel);
        repository.save(target);

        return repository.findAllByActiveTrueOrderByHierarchyLevel().stream()
                .map(PromoterRankService::toDto)
                .toList();
    }

    /**
     * Resolves the level for a new rank whose requested {@code
     * hierarchyLevel} collides with an existing active one — slots it
     * immediately below the colliding rank (gap midpoint), renumbering all
     * active ranks in gaps of {@value #RENUMBER_GAP} first if no gap remains.
     */
    private int insertLevelBelow(int requestedLevel) {
        List<PromoterRank> actives = repository.findAllByActiveTrueOrderByHierarchyLevel();
        PromoterRank collidingRank = actives.stream()
                .filter(r -> r.getHierarchyLevel() == requestedLevel)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("promoter_rank.hierarchy_level.taken"));
        int index = actives.indexOf(collidingRank);
        PromoterRank lowerRank = index > 0 ? actives.get(index - 1) : null;

        return midpoint(lowerRank == null ? null : lowerRank.getHierarchyLevel(), collidingRank.getHierarchyLevel())
                .orElseGet(() -> {
                    renumberActiveRanksInGapsOfTen();
                    return midpoint(lowerRank == null ? null : lowerRank.getHierarchyLevel(), collidingRank.getHierarchyLevel())
                            .orElseThrow(() -> new IllegalStateException("promoter_rank.reorder.no_room_after_renumber"));
                });
    }

    /**
     * A {@code hierarchy_level} strictly between {@code lowerExclusive} and
     * {@code upperExclusive} (either {@code null} = unbounded on that side),
     * or empty when the gap is exhausted (adjacent integers) and the caller
     * must renumber first.
     */
    private static OptionalInt midpoint(Integer lowerExclusive, Integer upperExclusive) {
        if (lowerExclusive == null && upperExclusive == null) {
            return OptionalInt.of(RENUMBER_GAP);
        }
        if (lowerExclusive == null) {
            return upperExclusive > 1 ? OptionalInt.of(upperExclusive / 2) : OptionalInt.empty();
        }
        if (upperExclusive == null) {
            return OptionalInt.of(lowerExclusive + RENUMBER_GAP);
        }
        return (upperExclusive - lowerExclusive > 1)
                ? OptionalInt.of(lowerExclusive + (upperExclusive - lowerExclusive) / 2)
                : OptionalInt.empty();
    }

    /**
     * Renumbers every active rank into gaps of {@value #RENUMBER_GAP}
     * (10/20/30/...), preserving current order. Safe to call mid-transaction
     * because the {@code hierarchy_level} unique constraint is {@code
     * DEFERRABLE INITIALLY DEFERRED} (V111) — only checked at commit, not
     * after each intermediate update.
     */
    private void renumberActiveRanksInGapsOfTen() {
        List<PromoterRank> ranks = repository.findAllByActiveTrueOrderByHierarchyLevel();
        int level = RENUMBER_GAP;
        for (PromoterRank r : ranks) {
            r.setHierarchyLevel(level);
            level += RENUMBER_GAP;
        }
        repository.saveAll(ranks);
    }

    /**
     * Validates an editable parent candidate for {@code rank} (V111):
     * {@code null} is always allowed (top of chain); otherwise the candidate
     * must have a strictly higher {@code hierarchyLevel} and must not be
     * {@code rank} itself or create a cycle by walking up the candidate's own
     * parent chain back to {@code rank}.
     */
    private PromoterRank resolveAndValidateParent(PromoterRank rank, UUID parentRankUuid) {
        if (parentRankUuid == null) {
            return null;
        }
        if (parentRankUuid.equals(rank.getUuid())) {
            throw new IllegalArgumentException("promoter_rank.parent.self_reference");
        }
        PromoterRank candidate = repository.findByUuid(parentRankUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_rank.parent.not_found"));
        if (candidate.getHierarchyLevel() <= rank.getHierarchyLevel()) {
            throw new IllegalArgumentException("promoter_rank.parent.not_higher");
        }
        for (PromoterRank cursor = candidate.getParentRank(); cursor != null; cursor = cursor.getParentRank()) {
            if (cursor.getId().equals(rank.getId())) {
                throw new IllegalArgumentException("promoter_rank.parent.cycle_detected");
            }
        }
        return candidate;
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
                r.getMaxSubordinates(), r.getDescription(),
                r.getParentRank() != null ? r.getParentRank().getUuid() : null,
                r.isActive());
    }
}
