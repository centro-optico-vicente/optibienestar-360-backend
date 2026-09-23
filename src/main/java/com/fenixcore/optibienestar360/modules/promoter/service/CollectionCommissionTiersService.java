package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;
import com.fenixcore.optibienestar360.modules.promoter.repository.CollectionCommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link CollectionCommissionTier} (ADR 0013 §3, V44, V126) —
 * the DB-driven collection-commission buckets the engine will read. Mirrors
 * {@code HierarchyOverrideTiersService}'s shape (pct-XOR-flat invariant,
 * campaign anchor, soft-delete-with-usage-check) plus its own basis-field
 * invariant (DAYS↔maxDays, AMOUNT↔minAmount — a minimum threshold, V144).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionCommissionTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "basis", "maxDays", "minAmount", "commissionPct", "flatAmount", "active", "status", "createdAt", "updatedAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CollectionCommissionTier.class, Map.of());

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final CollectionCommissionTierRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final CampaignRepository campaignRepository;
    private final CommissionRepository commissionRepository;
    private final DefaultSortResolver defaultSortResolver;

    public CollectionCommissionTierDto get(UUID uuid) {
        return CollectionCommissionTierDto.from(findManaged(uuid));
    }

    public Page<CollectionCommissionTierDto> list(Pageable pageable, String filter, String q,
                                                    UUID promoterTypeUuid, UUID campaignUuid, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "collection_commission_tier", pageable);
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
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                jakarta.persistence.criteria.Join<Object, Object> join =
                        root.join("promoterTypes", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.or(
                        cb.equal(join.get("uuid"), promoterTypeUuid),
                        cb.isEmpty(root.get("promoterTypes")));
            });
        }
        if (campaignUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("campaign").get("uuid"), campaignUuid));
        }
        return repository.findAll(spec, resolvedPageable).map(CollectionCommissionTierDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("collection_commission_tier", pageable);
    }

    @Transactional
    public CollectionCommissionTierDto create(CollectionCommissionTierCreateRequest req) {
        requireExactlyOneReward(req.commissionPct(), req.flatAmount());
        if (req.flatAmount() != null) {
            requireFlatAmountCurrency(req.flatAmountCurrencyUuid());
        }
        requireBasisFieldMatch(req.basis(), req.maxDays(), req.minAmount());
        if (req.basis() == Basis.AMOUNT) {
            requireMinAmountCurrency(req.minAmountCurrencyUuid());
        }

        CollectionCommissionTier tier = new CollectionCommissionTier();
        tier.setName(req.name());
        tier.setDescription(req.description());
        tier.setBasis(req.basis());
        tier.setMaxDays(req.basis() == Basis.DAYS ? req.maxDays() : null);
        tier.setMinAmount(req.basis() == Basis.AMOUNT ? req.minAmount() : null);
        tier.setMinAmountCurrency(req.basis() == Basis.AMOUNT ? resolveCurrency(req.minAmountCurrencyUuid()) : null);
        tier.setCommissionPct(req.commissionPct());
        tier.setFlatAmount(req.flatAmount());
        tier.setFlatAmountCurrency(resolveCurrency(req.flatAmountCurrencyUuid()));
        tier.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        tier.setCampaign(resolveCampaign(req.campaignUuid()));
        tier.setStartsAt(req.startsAt());
        tier.setEndsAt(req.endsAt());

        return CollectionCommissionTierDto.from(repository.save(tier));
    }

    @Transactional
    public CollectionCommissionTierDto update(UUID uuid, CollectionCommissionTierUpdateRequest req) {
        CollectionCommissionTier tier = findManaged(uuid);

        if (req.name() != null)             tier.setName(req.name());
        if (req.description() != null)      tier.setDescription(req.description());
        if (req.promoterTypeUuids() != null) tier.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        if (req.active() != null)           tier.setActive(req.active());
        if (req.campaignUuid() != null)     tier.setCampaign(resolveCampaign(req.campaignUuid()));
        if (req.startsAt() != null)         tier.setStartsAt(req.startsAt());
        if (req.endsAt() != null)           tier.setEndsAt(req.endsAt());

        // Basis switch: supplying `basis` swaps which bucket field is live; the matching
        // bucket field must arrive in the same request (a tier is DAYS xor AMOUNT).
        if (req.basis() != null) {
            requireBasisFieldMatch(req.basis(), req.maxDays(), req.minAmount());
            if (req.basis() == Basis.AMOUNT) {
                requireMinAmountCurrency(req.minAmountCurrencyUuid());
            }
            tier.setBasis(req.basis());
            tier.setMaxDays(req.basis() == Basis.DAYS ? req.maxDays() : null);
            tier.setMinAmount(req.basis() == Basis.AMOUNT ? req.minAmount() : null);
            tier.setMinAmountCurrency(req.basis() == Basis.AMOUNT ? resolveCurrency(req.minAmountCurrencyUuid()) : null);
        } else {
            if (req.maxDays() != null)   tier.setMaxDays(req.maxDays());
            if (req.minAmount() != null) {
                tier.setMinAmount(req.minAmount());
                if (tier.getBasis() == Basis.AMOUNT) {
                    UUID currencyUuid = req.minAmountCurrencyUuid() != null
                            ? req.minAmountCurrencyUuid()
                            : (tier.getMinAmountCurrency() != null ? tier.getMinAmountCurrency().getUuid() : null);
                    requireMinAmountCurrency(currencyUuid);
                    tier.setMinAmountCurrency(resolveCurrency(currencyUuid));
                }
            } else if (req.minAmountCurrencyUuid() != null && tier.getBasis() == Basis.AMOUNT) {
                tier.setMinAmountCurrency(resolveCurrency(req.minAmountCurrencyUuid()));
            }
        }

        // Reward switch: supplying one clears the other (a tier is pct XOR flat).
        if (req.commissionPct() != null && req.flatAmount() != null) {
            throw new IllegalArgumentException("collection_commission_tier.pct_xor_flat");
        }
        if (req.commissionPct() != null) {
            tier.setCommissionPct(req.commissionPct());
            tier.setFlatAmount(null);
            tier.setFlatAmountCurrency(null);
        } else if (req.flatAmount() != null) {
            requireFlatAmountCurrency(req.flatAmountCurrencyUuid());
            tier.setFlatAmount(req.flatAmount());
            tier.setFlatAmountCurrency(resolveCurrency(req.flatAmountCurrencyUuid()));
            tier.setCommissionPct(null);
        }
        requireExactlyOneReward(tier.getCommissionPct(), tier.getFlatAmount());
        requireBasisFieldMatch(tier.getBasis(), tier.getMaxDays(), tier.getMinAmount());

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

    private static void requireExactlyOneReward(BigDecimal pct, BigDecimal flat) {
        if ((pct == null) == (flat == null)) {
            throw new IllegalArgumentException("collection_commission_tier.pct_xor_flat");
        }
    }

    private static void requireFlatAmountCurrency(UUID currencyUuid) {
        if (currencyUuid == null) {
            throw new IllegalArgumentException("collection_commission_tier.flat_amount.currency_required");
        }
    }

    /** {@code minAmount} (V144, a minimum threshold) requires its reference currency, same as {@code flatAmount}. */
    private static void requireMinAmountCurrency(UUID currencyUuid) {
        if (currencyUuid == null) {
            throw new IllegalArgumentException("collection_commission_tier.min_amount.currency_required");
        }
    }

    /** {@code DAYS} requires {@code maxDays} (and rejects {@code minAmount}), and vice versa for {@code AMOUNT}. */
    private static void requireBasisFieldMatch(Basis basis, Integer maxDays, BigDecimal minAmount) {
        if (basis == Basis.DAYS && (maxDays == null || minAmount != null)) {
            throw new IllegalArgumentException("collection_commission_tier.basis_field_mismatch");
        }
        if (basis == Basis.AMOUNT && (minAmount == null || maxDays != null)) {
            throw new IllegalArgumentException("collection_commission_tier.basis_field_mismatch");
        }
    }

    private CollectionCommissionTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("collection_commission_tier.not_found"));
    }

    /** Empty/null = applies to every promoter type (V137, hub plan Part F). */
    private Set<PromoterType> resolvePromoterTypes(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return new java.util.HashSet<>();
        Set<PromoterType> resolved = new java.util.HashSet<>();
        for (UUID uuid : uuids) {
            resolved.add(promoterTypeRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found")));
        }
        return resolved;
    }

    private Currency resolveCurrency(UUID uuid) {
        if (uuid == null) return null;
        return currencyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private Campaign resolveCampaign(UUID uuid) {
        if (uuid == null) return null;
        return campaignRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private static Specification<CollectionCommissionTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
