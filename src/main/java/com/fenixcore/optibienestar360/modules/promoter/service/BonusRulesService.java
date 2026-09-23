package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
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
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@link CommissionBonusRule} (v2 PDF #5). Plural-name
 * convention matches {@code PromotersService}/{@code PlansService}.
 *
 * <p>The bean-validation on {@code BonusRuleRequest} covers per-field shape;
 * the cross-field invariants live here so they surface as localized 422s:</p>
 * <ul>
 *   <li>exactly one of {@code flatAmount} / {@code rewardPct} per reward type;</li>
 *   <li>PER_BLOCK accrual pays a flat amount per block — percentage rewards are
 *       only meaningful with THRESHOLD (a % of window earnings, once);</li>
 *   <li>CAMPAIGN windows require an ordered start/end; other windows carry none.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BonusRulesService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "metric", "accrual", "accrualPeriodStrategy", "rewardType",
            "thresholdCount", "includeSystemPromoters",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CommissionBonusRule.class, Map.of());

    private static final String[] SEARCHABLE_FIELDS = {"name", "description"};

    private final CommissionBonusRuleRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final CampaignRepository campaignRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public BonusRuleDto get(UUID uuid) {
        return BonusRuleDto.from(findManaged(uuid));
    }

    public Page<BonusRuleDto> list(Pageable pageable, String filter, String q,
                                     UUID promoterTypeUuid, UUID campaignUuid, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "bonus_rule", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "bonus_rule");
        Specification<CommissionBonusRule> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "bonus_rule.filter.field_not_allowed");
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
        return repository.findAll(spec, resolvedPageable).map(BonusRuleDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("bonus_rule", pageable);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.CREATE)
    public BonusRuleDto create(BonusRuleRequest req) {
        validate(req);
        CommissionBonusRule rule = new CommissionBonusRule();
        apply(rule, req);
        rule.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        return BonusRuleDto.from(repository.save(rule));
    }

    // ─── Update (full replace) ──────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public BonusRuleDto update(UUID uuid, BonusRuleRequest req) {
        validate(req);
        CommissionBonusRule rule = findManaged(uuid);
        apply(rule, req);
        // Full replace: empty/null clears any previously set promoter-type scope.
        rule.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        return BonusRuleDto.from(rule);   // dirty-check flushes on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "bonus_rule", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CommissionBonusRule findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bonus_rule.not_found"));
    }

    /** Empty/null = applies to every promoter type (V137, hub plan Part F). */
    private Set<PromoterType> resolvePromoterTypes(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return new HashSet<>();
        Set<PromoterType> resolved = new HashSet<>();
        for (UUID uuid : uuids) {
            resolved.add(promoterTypeRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found")));
        }
        return resolved;
    }

    /** {@code null} (defaulted later) or any strategy other than LIFETIME/CAMPAIGN — matches the DB CHECK on the 3 settlement axes. */
    private static void requirePeriodicStrategy(WindowStrategy strategy, String errorCode) {
        if (strategy == WindowStrategy.LIFETIME || strategy == WindowStrategy.CAMPAIGN) {
            throw new IllegalArgumentException(errorCode);
        }
    }

    private static void validate(BonusRuleRequest req) {
        if (req.rewardType() == RewardType.FLAT) {
            if (req.flatAmount() == null || req.rewardPct() != null) {
                throw new IllegalArgumentException("bonus_rule.reward.flat_xor_pct");
            }
        } else { // PERCENTAGE
            if (req.rewardPct() == null || req.flatAmount() != null) {
                throw new IllegalArgumentException("bonus_rule.reward.flat_xor_pct");
            }
            if (req.accrual() == AccrualMode.PER_BLOCK) {
                throw new IllegalArgumentException("bonus_rule.per_block.flat_only");
            }
        }

        if (req.accrualPeriodStrategy() == WindowStrategy.CAMPAIGN) {
            if (req.campaignStart() == null || req.campaignEnd() == null) {
                throw new IllegalArgumentException("bonus_rule.campaign.dates_required");
            }
            if (req.campaignEnd().isBefore(req.campaignStart())) {
                throw new IllegalArgumentException("bonus_rule.campaign.dates_order");
            }
        }

        // LIFETIME/CAMPAIGN are only ever valid on accrualPeriodStrategy (DB CHECK on
        // the 3 settlement axes) — see CommissionBonusRule.partialSettlementPeriodStrategy Javadoc.
        requirePeriodicStrategy(req.partialSettlementPeriodStrategy(), "bonus_rule.partial_settlement_period_strategy.periodic_only");
        requirePeriodicStrategy(req.finalSettlementPeriodStrategy(), "bonus_rule.final_settlement_period_strategy.periodic_only");
        requirePeriodicStrategy(req.retroactiveSettlementPeriodStrategy(), "bonus_rule.retroactive_settlement_period_strategy.periodic_only");

        // Metric-scoped threshold XOR (I-BE, hub plan Part I) — mirrors the
        // requireExactlyOneReward/requireFlatAmountCurrency style already used
        // by CommissionTiersService/CollectionCommissionTiersService.
        if (req.metric() == BonusMetric.AMOUNT_COLLECTED) {
            if (req.thresholdAmount() == null || req.thresholdCurrencyUuid() == null) {
                throw new IllegalArgumentException("bonus_rule.threshold_amount.currency_required");
            }
            if (req.thresholdCount() != null) {
                throw new IllegalArgumentException("bonus_rule.threshold.metric_mismatch");
            }
        } else {
            if (req.thresholdCount() == null || req.thresholdCount() <= 0) {
                throw new IllegalArgumentException("bonus_rule.threshold_count.required");
            }
            if (req.thresholdAmount() != null || req.thresholdCurrencyUuid() != null) {
                throw new IllegalArgumentException("bonus_rule.threshold.metric_mismatch");
            }
        }
    }

    /** Default the 3 settlement axes reproduce when a full-replace request leaves them unset (V145 backfill). */
    private static final WindowStrategy DEFAULT_SETTLEMENT_STRATEGY = WindowStrategy.MONTHLY;

    private void apply(CommissionBonusRule rule, BonusRuleRequest req) {
        rule.setName(req.name());
        rule.setDescription(req.description());
        rule.setMetric(req.metric());
        rule.setAccrual(req.accrual());
        boolean amountCollected = req.metric() == BonusMetric.AMOUNT_COLLECTED;
        rule.setThresholdCount(amountCollected ? 0 : req.thresholdCount());
        rule.setThresholdAmount(amountCollected ? req.thresholdAmount() : null);
        rule.setThresholdCurrency(amountCollected ? resolveCurrencyByUuid(req.thresholdCurrencyUuid()) : null);

        rule.setAccrualPeriodStrategy(req.accrualPeriodStrategy());
        rule.setPartialSettlementPeriodStrategy(
                req.partialSettlementPeriodStrategy() != null ? req.partialSettlementPeriodStrategy() : DEFAULT_SETTLEMENT_STRATEGY);
        rule.setFinalSettlementPeriodStrategy(
                req.finalSettlementPeriodStrategy() != null ? req.finalSettlementPeriodStrategy() : DEFAULT_SETTLEMENT_STRATEGY);
        rule.setRetroactiveSettlementPeriodStrategy(
                req.retroactiveSettlementPeriodStrategy() != null ? req.retroactiveSettlementPeriodStrategy() : DEFAULT_SETTLEMENT_STRATEGY);
        rule.setAccrualPeriodAnchor(req.accrualPeriodAnchor());
        rule.setPartialSettlementPeriodAnchor(req.partialSettlementPeriodAnchor());
        rule.setFinalSettlementPeriodAnchor(req.finalSettlementPeriodAnchor());
        rule.setRetroactiveSettlementPeriodAnchor(req.retroactiveSettlementPeriodAnchor());

        boolean campaign = req.accrualPeriodStrategy() == WindowStrategy.CAMPAIGN;
        rule.setCampaignStart(campaign ? req.campaignStart() : null);
        rule.setCampaignEnd(campaign ? req.campaignEnd() : null);

        rule.setRewardType(req.rewardType());
        rule.setFlatAmount(req.rewardType() == RewardType.FLAT ? req.flatAmount() : null);
        rule.setRewardPct(req.rewardType() == RewardType.PERCENTAGE ? req.rewardPct() : null);
        rule.setRewardCurrency(req.rewardCurrencyUuid() != null
			? resolveCurrencyByUuid(req.rewardCurrencyUuid())
			: resolveCurrency(normalizeCurrency(req.rewardCurrency()))
		);
        rule.setIncludeSystemPromoters(Boolean.TRUE.equals(req.includeSystemPromoters()));
        rule.setCampaign(resolveCampaign(req.campaignUuid()));
        rule.setStartsAt(req.startsAt());
        rule.setEndsAt(req.endsAt());
    }

    private Currency resolveCurrency(String code) {
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    private Currency resolveCurrencyByUuid(UUID uuid) {
		return currencyRepository.findByUuid(uuid)
			.orElseThrow(() -> new NoSuchElementException("currency.not_found"))
		;
    }

    private Campaign resolveCampaign(UUID uuid) {
        if (uuid == null) return null;
        return campaignRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "USD";
        return currency.trim().toUpperCase();
    }

    private static Specification<CommissionBonusRule> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
