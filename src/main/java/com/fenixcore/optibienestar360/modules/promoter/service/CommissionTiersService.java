package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SettlementAxes;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.BasisType;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
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
 * Admin CRUD for {@link CommissionTier} (v2 PDF #5, V42) — the DB-driven config
 * the commission engine reads. Plural name mirrors {@code PlansService}.
 *
 * <p>Enforces the pct-XOR-flat invariant the DB CHECK also guards, surfacing it
 * as a clean 422 ({@code commission_tier.pct_xor_flat}) instead of a constraint
 * violation.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "planType", "thresholdCount", "commissionPct", "flatAmount",
            "accrualPeriodStrategy", "basis", "thresholdAmount", "appliesTo", "active", "status", "createdAt", "updatedAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CommissionTier.class, Map.of());

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final CommissionTierRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final CampaignRepository campaignRepository;
    private final com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository commissionRepository;
    private final DefaultSortResolver defaultSortResolver;

    public CommissionTierDto get(UUID uuid) {
        return CommissionTierDto.from(findManaged(uuid));
    }

    public Page<CommissionTierDto> list(Pageable pageable, String filter, String q,
                                          UUID promoterTypeUuid, UUID campaignUuid, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "commission_tier", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "commission_tier");
        Specification<CommissionTier> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "commission_tier.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (promoterTypeUuid != null) {
            // M:N (V137): a tier scopes to a promoter type either explicitly (JOIN match)
            // or implicitly by having no scope at all (applies to every promoter type).
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
        return repository.findAll(spec, resolvedPageable).map(CommissionTierDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("commission_tier", pageable);
    }

    @Transactional
    @Auditable(entity = "commission_tier", action = AuditAction.CREATE)
    public CommissionTierDto create(CommissionTierCreateRequest req) {
        requireExactlyOneReward(req.commissionPct(), req.flatAmount());
        if (req.flatAmount() != null) {
            requireFlatAmountCurrency(req.flatAmountCurrencyUuid());
        }

        CommissionTier tier = new CommissionTier();
        tier.setName(req.name());
        tier.setDescription(req.description());
        tier.setPlanType(req.planType());
        tier.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        tier.setThresholdCount(req.thresholdCount() != null ? req.thresholdCount() : 0);
        tier.setCommissionPct(req.commissionPct());
        tier.setFlatAmount(req.flatAmount());
        tier.setFlatAmountCurrency(resolveCurrency(req.flatAmountCurrencyUuid()));
        tier.setAccrualPeriodStrategy(req.accrualPeriodStrategy());
        if (req.partialSettlementPeriodStrategy() != null) tier.setPartialSettlementPeriodStrategy(req.partialSettlementPeriodStrategy());
        if (req.finalSettlementPeriodStrategy() != null)   tier.setFinalSettlementPeriodStrategy(req.finalSettlementPeriodStrategy());
        if (req.retroactiveSettlementPeriodStrategy() != null) tier.setRetroactiveSettlementPeriodStrategy(req.retroactiveSettlementPeriodStrategy());
        tier.setAccrualPeriodAnchor(req.accrualPeriodAnchor());
        tier.setPartialSettlementPeriodAnchor(req.partialSettlementPeriodAnchor());
        tier.setFinalSettlementPeriodAnchor(req.finalSettlementPeriodAnchor());
        tier.setRetroactiveSettlementPeriodAnchor(req.retroactiveSettlementPeriodAnchor());
        if (req.basis() != null) tier.setBasis(req.basis());
        if (tier.getBasis() == BasisType.AMOUNT) {
            requireThresholdAmountCurrency(req.thresholdAmountCurrencyUuid());
        }
        tier.setThresholdAmount(req.thresholdAmount());
        tier.setThresholdAmountCurrency(resolveCurrency(req.thresholdAmountCurrencyUuid()));
        tier.setAppliesTo(req.appliesTo());
        tier.setCampaign(resolveCampaign(req.campaignUuid()));
        tier.setStartsAt(req.startsAt());
        tier.setEndsAt(req.endsAt());
        applySettlementAxesResolution(tier);

        return CommissionTierDto.from(repository.save(tier));
    }

    @Transactional
    @Auditable(entity = "commission_tier", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CommissionTierDto update(UUID uuid, CommissionTierUpdateRequest req) {
        CommissionTier tier = findManaged(uuid);

        if (req.name() != null)             tier.setName(req.name());
        if (req.description() != null)      tier.setDescription(req.description());
        if (req.planType() != null)         tier.setPlanType(req.planType());
        if (req.promoterTypeUuids() != null) tier.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        if (req.thresholdCount() != null)   tier.setThresholdCount(req.thresholdCount());
        if (req.accrualPeriodStrategy() != null)             tier.setAccrualPeriodStrategy(req.accrualPeriodStrategy());
        if (req.partialSettlementPeriodStrategy() != null)   tier.setPartialSettlementPeriodStrategy(req.partialSettlementPeriodStrategy());
        if (req.finalSettlementPeriodStrategy() != null)     tier.setFinalSettlementPeriodStrategy(req.finalSettlementPeriodStrategy());
        if (req.retroactiveSettlementPeriodStrategy() != null) tier.setRetroactiveSettlementPeriodStrategy(req.retroactiveSettlementPeriodStrategy());
        if (req.accrualPeriodAnchor() != null)               tier.setAccrualPeriodAnchor(req.accrualPeriodAnchor());
        if (req.partialSettlementPeriodAnchor() != null)     tier.setPartialSettlementPeriodAnchor(req.partialSettlementPeriodAnchor());
        if (req.finalSettlementPeriodAnchor() != null)       tier.setFinalSettlementPeriodAnchor(req.finalSettlementPeriodAnchor());
        if (req.retroactiveSettlementPeriodAnchor() != null) tier.setRetroactiveSettlementPeriodAnchor(req.retroactiveSettlementPeriodAnchor());
        if (req.basis() != null) {
            tier.setBasis(req.basis());
            if (req.basis() == BasisType.AMOUNT) {
                requireThresholdAmountCurrency(req.thresholdAmountCurrencyUuid());
                tier.setThresholdAmount(req.thresholdAmount());
                tier.setThresholdAmountCurrency(resolveCurrency(req.thresholdAmountCurrencyUuid()));
            } else {
                tier.setThresholdAmount(null);
                tier.setThresholdAmountCurrency(null);
            }
        } else if (req.thresholdAmount() != null) {
            requireThresholdAmountCurrency(req.thresholdAmountCurrencyUuid());
            tier.setThresholdAmount(req.thresholdAmount());
            tier.setThresholdAmountCurrency(resolveCurrency(req.thresholdAmountCurrencyUuid()));
        }
        if (req.appliesTo() != null)        tier.setAppliesTo(req.appliesTo());
        if (req.active() != null)           tier.setActive(req.active());
        if (req.campaignUuid() != null)     tier.setCampaign(resolveCampaign(req.campaignUuid()));
        if (req.startsAt() != null)         tier.setStartsAt(req.startsAt());
        if (req.endsAt() != null)           tier.setEndsAt(req.endsAt());

        // Reward switch: supplying one clears the other (a tier is pct XOR flat).
        if (req.commissionPct() != null && req.flatAmount() != null) {
            throw new IllegalArgumentException("commission_tier.pct_xor_flat");
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
        applySettlementAxesResolution(tier);

        return CommissionTierDto.from(tier);   // managed → dirty-check on commit
    }

    /**
     * {@code Commission.commissionTierId} is mapped as a bare {@code Long} in
     * the entity (no {@code @ManyToOne}) — the Javadoc on {@link
     * com.fenixcore.optibienestar360.modules.promoter.entity.Commission}
     * claims there's no real FK yet, but that comment is stale: V42
     * ("Close the deferred FK reserved in V26") added
     * {@code fk_commissions_tier} at the DB level. So a hard delete really
     * can violate a live FK — this counts every {@code commissions} row
     * (any status) pointing at this tier via {@code commissionRepository
     * .countByCommissionTierId}.
     */
    public long countUsages(UUID uuid) {
        CommissionTier tier = findManaged(uuid);
        return commissionRepository.countByCommissionTierId(tier.getId());
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Smart delete: hard-deletes only when {@code physical=true} AND the
     * tier is genuinely unreferenced (re-checked here, not trusted from the
     * caller, to avoid a race between the usage check and the delete).
     * Otherwise falls back to the existing soft-delete. Omitting
     * {@code physical} (defaults to {@code false}) reproduces the prior
     * behavior exactly.
     */
    @Transactional
    @Auditable(entity = "commission_tier", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        CommissionTier tier = findManaged(uuid);
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
            throw new IllegalArgumentException("commission_tier.pct_xor_flat");
        }
    }

    private static void requireFlatAmountCurrency(UUID currencyUuid) {
        if (currencyUuid == null) {
            throw new IllegalArgumentException("commission_tier.flat_amount.currency_required");
        }
    }

    /** {@code basis=AMOUNT} requires {@code thresholdAmountCurrencyUuid}, same pattern as {@link #requireFlatAmountCurrency}. */
    private static void requireThresholdAmountCurrency(UUID currencyUuid) {
        if (currencyUuid == null) {
            throw new IllegalArgumentException("commission_tier.threshold_amount.currency_required");
        }
    }

    private Currency resolveCurrency(UUID uuid) {
        if (uuid == null) return null;
        return currencyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private CommissionTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("commission_tier.not_found"));
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

    private Campaign resolveCampaign(UUID uuid) {
        if (uuid == null) return null;
        return campaignRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private static Specification<CommissionTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /**
     * D15 (hub plan competitive-commission-rules, Fase A): validates the
     * partial axis against accrual and normalizes the retroactive axis —
     * enabled only when {@code partial} is strictly finer than {@code
     * accrual}; disabled otherwise, in which case it collapses to {@code
     * partial} with no anchor (a no-op: nothing separate to catch up on).
     * Called after every axis field is set, so it sees the tier's final
     * post-request state (create: full set; update: merged patch).
     */
    private static void applySettlementAxesResolution(CommissionTier tier) {
        SettlementAxes.Resolution resolution = SettlementAxes.resolve(
                tier.getAccrualPeriodStrategy().name(),
                tier.getPartialSettlementPeriodStrategy().name(),
                tier.getRetroactiveSettlementPeriodStrategy().name(),
                tier.getRetroactiveSettlementPeriodAnchor());
        tier.setRetroactiveSettlementPeriodStrategy(PeriodStrategy.valueOf(resolution.retroactiveStrategy()));
        tier.setRetroactiveSettlementPeriodAnchor(resolution.retroactiveAnchor());
    }
}
