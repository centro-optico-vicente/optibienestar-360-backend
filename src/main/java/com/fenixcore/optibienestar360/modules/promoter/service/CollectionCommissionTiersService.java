package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.repository.CollectionCommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link CollectionCommissionTier} (ADR 0013 §3, V44) — the
 * DB-driven collection-commission buckets the engine will read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionCommissionTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "maxDays", "commissionPct", "active", "status", "createdAt", "updatedAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CollectionCommissionTier.class, Map.of(
                    "promoterType_Display", "promoterType.name"
            ));

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final CollectionCommissionTierRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final CommissionRepository commissionRepository;
    private final DefaultSortResolver defaultSortResolver;

    public CollectionCommissionTierDto get(UUID uuid) {
        return CollectionCommissionTierDto.from(findManaged(uuid));
    }

    public Page<CollectionCommissionTierDto> list(Pageable pageable, String filter, String q,
                                                    UUID promoterTypeUuid, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "collection_commission_tier", pageable, new SortOrder("createdAt", "DESC"));
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "collection_commission_tier");
        Specification<CollectionCommissionTier> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "collection_commission_tier.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (promoterTypeUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("promoterType").get("uuid"), promoterTypeUuid));
        }
        return repository.findAll(spec, resolvedPageable).map(CollectionCommissionTierDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("collection_commission_tier", pageable, new SortOrder("createdAt", "DESC"));
    }

    @Transactional
    public CollectionCommissionTierDto create(CollectionCommissionTierCreateRequest req) {
        CollectionCommissionTier tier = new CollectionCommissionTier();
        tier.setName(req.name());
        tier.setMaxDays(req.maxDays());
        tier.setCommissionPct(req.commissionPct());
        tier.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));

        return CollectionCommissionTierDto.from(repository.save(tier));
    }

    @Transactional
    public CollectionCommissionTierDto update(UUID uuid, CollectionCommissionTierUpdateRequest req) {
        CollectionCommissionTier tier = findManaged(uuid);

        if (req.name() != null)             tier.setName(req.name());
        if (req.maxDays() != null)          tier.setMaxDays(req.maxDays());
        if (req.commissionPct() != null)    tier.setCommissionPct(req.commissionPct());
        if (req.promoterTypeUuid() != null) tier.setPromoterType(resolvePromoterType(req.promoterTypeUuid()));
        if (req.active() != null)           tier.setActive(req.active());

        return CollectionCommissionTierDto.from(tier);   // managed → dirty-check on commit
    }

    /**
     * {@code Commission.collectionTierId} is a bare {@code Long} in the
     * entity, but V48 added a real DB FK ({@code REFERENCES
     * collection_commission_tiers}) directly (never deferred, unlike
     * {@code CommissionTier}'s). Counts every {@code commissions} row (any
     * status) pointing at this tier.
     */
    public long countUsages(UUID uuid) {
        CollectionCommissionTier tier = findManaged(uuid);
        return commissionRepository.countByCollectionTierId(tier.getId());
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Smart delete: hard-deletes only when {@code physical=true} AND the
     * tier is genuinely unreferenced (re-checked here to avoid a race with
     * the usage check). Otherwise soft-deletes, same as before —
     * {@code physical} defaults to {@code false} to stay backward compatible.
     */
    @Transactional
    public void delete(UUID uuid, boolean physical) {
        CollectionCommissionTier tier = findManaged(uuid);
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            repository.delete(tier);
            return;
        }
        tier.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CollectionCommissionTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("collection_commission_tier.not_found"));
    }

    private PromoterType resolvePromoterType(UUID uuid) {
        if (uuid == null) return null;
        return promoterTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found"));
    }

    private static Specification<CollectionCommissionTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
