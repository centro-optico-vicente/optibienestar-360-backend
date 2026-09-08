package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link HierarchyOverrideTier} (V102, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2) — the
 * Supervisor/Coordinador override bands, previously configurable only via
 * the migration seed. Mirrors {@code CommissionTiersService}'s shape
 * (pct-XOR-flat invariant, soft-delete-with-usage-check).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HierarchyOverrideTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "category", "thresholdCount", "overridePct", "flatAmount",
            "periodStrategy", "active", "status", "createdAt", "updatedAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(HierarchyOverrideTier.class, Map.of(
                    "rank_Display", "rank.name"
            ));

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final HierarchyOverrideTierRepository repository;
    private final PromoterRankRepository rankRepository;
    private final CurrencyRepository currencyRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final DefaultSortResolver defaultSortResolver;

    public HierarchyOverrideTierDto get(UUID uuid) {
        return HierarchyOverrideTierDto.from(findManaged(uuid));
    }

    public Page<HierarchyOverrideTierDto> list(Pageable pageable, String filter, String q,
                                               UUID rankUuid, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "hierarchy_override_tier", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "hierarchy_override_tier");
        Specification<HierarchyOverrideTier> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "hierarchy_override_tier.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (rankUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rank").get("uuid"), rankUuid));
        }
        return repository.findAll(spec, resolvedPageable).map(HierarchyOverrideTierDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("hierarchy_override_tier", pageable);
    }

    @Transactional
    @Auditable(entity = "hierarchy_override_tier", action = AuditAction.CREATE)
    public HierarchyOverrideTierDto create(HierarchyOverrideTierCreateRequest req) {
        requireExactlyOneReward(req.overridePct(), req.flatAmount());
        if (req.flatAmount() != null) {
            requireFlatAmountCurrency(req.flatAmountCurrencyUuid());
        }

        HierarchyOverrideTier tier = new HierarchyOverrideTier();
        tier.setName(req.name());
        tier.setRank(resolveRank(req.rankUuid()));
        tier.setCategory(req.category());
        tier.setThresholdCount(req.thresholdCount() != null ? req.thresholdCount() : 0);
        tier.setOverridePct(req.overridePct());
        tier.setFlatAmount(req.flatAmount());
        tier.setFlatAmountCurrency(resolveCurrency(req.flatAmountCurrencyUuid()));
        tier.setPeriodStrategy(req.periodStrategy());

        return HierarchyOverrideTierDto.from(repository.save(tier));
    }

    @Transactional
    @Auditable(entity = "hierarchy_override_tier", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public HierarchyOverrideTierDto update(UUID uuid, HierarchyOverrideTierUpdateRequest req) {
        HierarchyOverrideTier tier = findManaged(uuid);

        if (req.name() != null)           tier.setName(req.name());
        if (req.rankUuid() != null)       tier.setRank(resolveRank(req.rankUuid()));
        if (req.category() != null)       tier.setCategory(req.category());
        if (req.thresholdCount() != null) tier.setThresholdCount(req.thresholdCount());
        if (req.periodStrategy() != null) tier.setPeriodStrategy(req.periodStrategy());
        if (req.active() != null)         tier.setActive(req.active());

        // Reward switch: supplying one clears the other (a tier is pct XOR flat).
        if (req.overridePct() != null && req.flatAmount() != null) {
            throw new IllegalArgumentException("hierarchy_override_tier.pct_xor_flat");
        }
        if (req.overridePct() != null) {
            tier.setOverridePct(req.overridePct());
            tier.setFlatAmount(null);
            tier.setFlatAmountCurrency(null);
        } else if (req.flatAmount() != null) {
            requireFlatAmountCurrency(req.flatAmountCurrencyUuid());
            tier.setFlatAmount(req.flatAmount());
            tier.setFlatAmountCurrency(resolveCurrency(req.flatAmountCurrencyUuid()));
            tier.setOverridePct(null);
        }
        requireExactlyOneReward(tier.getOverridePct(), tier.getFlatAmount());

        return HierarchyOverrideTierDto.from(tier);   // managed → dirty-check on commit
    }

    /** Every {@code promoter_hierarchy_overrides} row (any status) pointing at this tier — a real FK, so a hard delete could violate it. */
    public long countUsages(UUID uuid) {
        HierarchyOverrideTier tier = findManaged(uuid);
        return overrideRepository.countByTierId(tier.getId());
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /** Smart delete: hard-deletes only when {@code physical=true} AND unreferenced; otherwise soft-deletes. */
    @Transactional
    @Auditable(entity = "hierarchy_override_tier", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        HierarchyOverrideTier tier = findManaged(uuid);
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            repository.delete(tier);
            return;
        }
        tier.setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void requireExactlyOneReward(BigDecimal pct, BigDecimal flat) {
        if ((pct == null) == (flat == null)) {
            throw new IllegalArgumentException("hierarchy_override_tier.pct_xor_flat");
        }
    }

    private static void requireFlatAmountCurrency(UUID currencyUuid) {
        if (currencyUuid == null) {
            throw new IllegalArgumentException("hierarchy_override_tier.flat_amount.currency_required");
        }
    }

    private HierarchyOverrideTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("hierarchy_override_tier.not_found"));
    }

    private PromoterRank resolveRank(UUID uuid) {
        return rankRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"));
    }

    private Currency resolveCurrency(UUID uuid) {
        if (uuid == null) return null;
        return currencyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private static Specification<HierarchyOverrideTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
