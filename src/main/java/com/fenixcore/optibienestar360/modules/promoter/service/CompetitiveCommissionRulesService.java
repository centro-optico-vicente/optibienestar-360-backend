package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SettlementAxes;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleListItemDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRulePositionRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.TiePolicy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link CompetitiveCommissionRule} (hub plan
 * competitive-commission-rules, Fase 1). Mirrors the shape of {@code
 * CommissionTiersService}/{@code BonusRulesService}, plus the rule-level
 * pieces of D11 (no overlapping positions), D12 (min-per-position, RANKING
 * only), D13 (scope), D14 (the RANKING/FIRST_TO_REACH settlement matrix) and
 * D16's group-consistency check.
 *
 * <p><b>Not yet implemented (Fase 2, once awards exist):</b> the D14
 * "congelamiento" 409 when a rule has {@code PENDING}/{@code PAID} awards —
 * {@link #countUsages} always returns {@code 0} for now, so every rule stays
 * fully editable. {@code clone(uuid)} and the leaderboard/recalculate
 * endpoints also belong to the evaluation engine (Fase 2) and aren't exposed
 * yet.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitiveCommissionRulesService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "metric", "competitionType", "competitionGroup", "active", "status", "createdAt", "updatedAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CompetitiveCommissionRule.class, Map.of());

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    /** Snapshot-only metrics (D4) — never emit events, so FIRST_TO_REACH (which needs an achieved-at moment) can't use them. */
    private static final Set<CompetitiveMetric> SNAPSHOT_ONLY_METRICS = Set.of(CompetitiveMetric.ACTIVE_SUBSCRIBERS);

    /** Metrics measured by count vs. by amount — decides which of thresholdCount/thresholdAmount is required. */
    private static final Set<CompetitiveMetric> COUNT_METRICS = Set.of(
            CompetitiveMetric.NEW_SUBSCRIBERS, CompetitiveMetric.ACTIVE_SUBSCRIBERS,
            CompetitiveMetric.SALES_COUNT, CompetitiveMetric.COLLECTION_COUNT, CompetitiveMetric.ADVANCE_COUNT);

    private final CompetitiveCommissionRuleRepository repository;
    private final PromoterTypeRepository promoterTypeRepository;
    private final PromoterRankRepository rankRepository;
    private final CurrencyRepository currencyRepository;
    private final CampaignRepository campaignRepository;
    private final DefaultSortResolver defaultSortResolver;

    public CompetitiveRuleDto get(UUID uuid) {
        return CompetitiveRuleDto.from(findManaged(uuid));
    }

    public Page<CompetitiveRuleListItemDto> list(Pageable pageable, String filter, String q,
                                                   UUID campaignUuid, boolean campaignLinked,
                                                   CompetitiveMetric metric, CompetitionType competitionType,
                                                   boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "competitive_commission_rule", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "competitive_commission_rule");
        Specification<CompetitiveCommissionRule> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "competitive_commission_rule.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (campaignUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("campaign").get("uuid"), campaignUuid));
        } else if (campaignLinked) {
            // Server-side "solo reglas de campaña" (hub plan Parte D) — replaces the client-side
            // filter the other rule tabs apply, per this plan's §5.
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("campaign")));
        }
        if (metric != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("metric"), metric));
        }
        if (competitionType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("competitionType"), competitionType));
        }
        return repository.findAll(spec, resolvedPageable).map(CompetitiveRuleListItemDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("competitive_commission_rule", pageable);
    }

    @Transactional
    @Auditable(entity = "competitive_commission_rule", action = AuditAction.CREATE)
    public CompetitiveRuleDto create(CompetitiveRuleCreateRequest req) {
        CompetitiveCommissionRule rule = new CompetitiveCommissionRule();
        rule.setName(req.name());
        rule.setDescription(req.description());
        rule.setMetric(req.metric());
        rule.setCompetitionType(req.competitionType());
        rule.setThresholdCount(req.thresholdCount());
        rule.setThresholdAmount(req.thresholdAmount());
        rule.setThresholdCurrency(resolveCurrency(req.thresholdCurrencyUuid()));
        rule.setAchievementDateBasis(req.achievementDateBasis() != null ? req.achievementDateBasis() : AchievementDateBasis.APPROVED_AT);
        rule.setTiePolicy(req.tiePolicy() != null ? req.tiePolicy() : TiePolicy.MANUAL);
        rule.setCompetitionGroup(req.competitionGroup());
        rule.setGroupPriority(req.groupPriority());
        rule.setAccrualPeriodStrategy(req.accrualPeriodStrategy());
        rule.setPartialSettlementPeriodStrategy(req.partialSettlementPeriodStrategy() != null
                ? req.partialSettlementPeriodStrategy() : req.accrualPeriodStrategy());
        rule.setFinalSettlementPeriodStrategy(req.finalSettlementPeriodStrategy() != null
                ? req.finalSettlementPeriodStrategy() : req.accrualPeriodStrategy());
        rule.setRetroactiveSettlementPeriodStrategy(req.retroactiveSettlementPeriodStrategy() != null
                ? req.retroactiveSettlementPeriodStrategy() : req.accrualPeriodStrategy());
        rule.setAccrualPeriodAnchor(req.accrualPeriodAnchor());
        rule.setPartialSettlementPeriodAnchor(req.partialSettlementPeriodAnchor());
        rule.setFinalSettlementPeriodAnchor(req.finalSettlementPeriodAnchor());
        rule.setRetroactiveSettlementPeriodAnchor(req.retroactiveSettlementPeriodAnchor());
        rule.setConfirmationDelayDays(req.confirmationDelayDays() != null ? req.confirmationDelayDays() : 0);
        rule.setCampaign(resolveCampaign(req.campaignUuid()));
        rule.setStartsAt(req.startsAt());
        rule.setEndsAt(req.endsAt());
        inheritCampaignDates(rule);
        rule.setIncludeSystemPromoters(Boolean.TRUE.equals(req.includeSystemPromoters()));
        rule.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        rule.setRanks(resolveRanks(req.rankUuids()));

        applyPositions(rule, req.positions());
        validate(rule);
        applySettlementAxesResolution(rule);

        return CompetitiveRuleDto.from(repository.save(rule));
    }

    @Transactional
    @Auditable(entity = "competitive_commission_rule", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CompetitiveRuleDto update(UUID uuid, CompetitiveRuleUpdateRequest req) {
        CompetitiveCommissionRule rule = findManaged(uuid);

        if (req.name() != null) rule.setName(req.name());
        if (req.description() != null) rule.setDescription(req.description());
        if (req.metric() != null) rule.setMetric(req.metric());
        if (req.competitionType() != null) rule.setCompetitionType(req.competitionType());
        if (req.thresholdCount() != null) rule.setThresholdCount(req.thresholdCount());
        if (req.thresholdAmount() != null) rule.setThresholdAmount(req.thresholdAmount());
        if (req.thresholdCurrencyUuid() != null) rule.setThresholdCurrency(resolveCurrency(req.thresholdCurrencyUuid()));
        if (req.achievementDateBasis() != null) rule.setAchievementDateBasis(req.achievementDateBasis());
        if (req.tiePolicy() != null) rule.setTiePolicy(req.tiePolicy());
        if (req.competitionGroup() != null) rule.setCompetitionGroup(req.competitionGroup());
        if (req.groupPriority() != null) rule.setGroupPriority(req.groupPriority());
        if (req.accrualPeriodStrategy() != null) rule.setAccrualPeriodStrategy(req.accrualPeriodStrategy());
        if (req.partialSettlementPeriodStrategy() != null) rule.setPartialSettlementPeriodStrategy(req.partialSettlementPeriodStrategy());
        if (req.finalSettlementPeriodStrategy() != null) rule.setFinalSettlementPeriodStrategy(req.finalSettlementPeriodStrategy());
        if (req.retroactiveSettlementPeriodStrategy() != null) rule.setRetroactiveSettlementPeriodStrategy(req.retroactiveSettlementPeriodStrategy());
        if (req.accrualPeriodAnchor() != null) rule.setAccrualPeriodAnchor(req.accrualPeriodAnchor());
        if (req.partialSettlementPeriodAnchor() != null) rule.setPartialSettlementPeriodAnchor(req.partialSettlementPeriodAnchor());
        if (req.finalSettlementPeriodAnchor() != null) rule.setFinalSettlementPeriodAnchor(req.finalSettlementPeriodAnchor());
        if (req.retroactiveSettlementPeriodAnchor() != null) rule.setRetroactiveSettlementPeriodAnchor(req.retroactiveSettlementPeriodAnchor());
        if (req.confirmationDelayDays() != null) rule.setConfirmationDelayDays(req.confirmationDelayDays());
        if (req.campaignUuid() != null) rule.setCampaign(resolveCampaign(req.campaignUuid()));
        if (req.startsAt() != null) rule.setStartsAt(req.startsAt());
        if (req.endsAt() != null) rule.setEndsAt(req.endsAt());
        if (req.includeSystemPromoters() != null) rule.setIncludeSystemPromoters(req.includeSystemPromoters());
        if (req.active() != null) rule.setActive(req.active());
        if (req.promoterTypeUuids() != null) rule.setPromoterTypes(resolvePromoterTypes(req.promoterTypeUuids()));
        if (req.rankUuids() != null) rule.setRanks(resolveRanks(req.rankUuids()));
        if (req.positions() != null) applyPositions(rule, req.positions());

        validate(rule);
        applySettlementAxesResolution(rule);

        return CompetitiveRuleDto.from(rule);   // managed → dirty-check on commit
    }

    /**
     * Always {@code 0} in Fase 1 — no other table references a competitive
     * rule yet. Fase 2 wires this to {@code CompetitiveCommissionAwardRepository
     * .countByRuleId}, which also gates the D14 "congelamiento" 409.
     */
    public long countUsages(UUID uuid) {
        findManaged(uuid);
        return 0;
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /** Smart delete: hard-deletes only when {@code physical=true} AND unreferenced; otherwise soft-deletes. */
    @Transactional
    @Auditable(entity = "competitive_commission_rule", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        CompetitiveCommissionRule rule = findManaged(uuid);
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            repository.delete(rule);
            return;
        }
        rule.setActive(false);
    }

    // ─── Validation (D11/D12/D13/D14/D16) ──────────────────────────────────

    private void validate(CompetitiveCommissionRule rule) {
        validateMetricThreshold(rule);
        validatePositions(rule);
        validateCampaignWindow(rule);
        validateGroup(rule);
    }

    /**
     * The threshold (the "N" in "first to reach N") is only meaningful — and
     * only required — for FIRST_TO_REACH (DB CHECK {@code chk_ccr_ftr_threshold}):
     * a RANKING rule just orders promoters by the metric, with no target to
     * reach, so {@code thresholdCount}/{@code thresholdAmount} normally stay
     * {@code null} there. If one IS set anyway (or always, for FIRST_TO_REACH),
     * its type (count vs. amount) must match the metric (D4). FIRST_TO_REACH
     * also can't use a snapshot-only metric (D4) — there's no achieved-at
     * moment to have "reached" it.
     */
    private void validateMetricThreshold(CompetitiveCommissionRule rule) {
        if (rule.getCompetitionType() == CompetitionType.FIRST_TO_REACH && SNAPSHOT_ONLY_METRICS.contains(rule.getMetric())) {
            throw new IllegalArgumentException("competitive_rule.metric_snapshot_ranking_only");
        }
        boolean hasThreshold = rule.getThresholdCount() != null || rule.getThresholdAmount() != null;
        if (rule.getCompetitionType() == CompetitionType.FIRST_TO_REACH && !hasThreshold) {
            throw new IllegalArgumentException("competitive_rule.ftr_threshold_required");
        }
        if (!hasThreshold) {
            return;
        }
        boolean countMetric = COUNT_METRICS.contains(rule.getMetric());
        if (countMetric) {
            if (rule.getThresholdCount() == null || rule.getThresholdAmount() != null) {
                throw new IllegalArgumentException("competitive_rule.threshold.metric_mismatch");
            }
        } else {
            if (rule.getThresholdAmount() == null || rule.getThresholdCurrency() == null || rule.getThresholdCount() != null) {
                throw new IllegalArgumentException("competitive_rule.threshold.metric_mismatch");
            }
        }
    }

    /** D11 (no overlap, at least 1 position) + D12 (min-per-position, RANKING only) + reward invariants. */
    private void validatePositions(CompetitiveCommissionRule rule) {
        List<CompetitiveCommissionRulePosition> positions = rule.getPositions();
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("competitive_rule.positions.required");
        }
        List<CompetitiveCommissionRulePosition> sorted = positions.stream()
                .sorted(Comparator.comparingInt(CompetitiveCommissionRulePosition::getPositionFrom)).toList();
        for (int i = 0; i < sorted.size(); i++) {
            CompetitiveCommissionRulePosition p = sorted.get(i);
            if (p.getPositionTo() < p.getPositionFrom()) {
                throw new IllegalArgumentException("competitive_rule.positions.range_invalid");
            }
            if (i > 0 && p.getPositionFrom() <= sorted.get(i - 1).getPositionTo()) {
                throw new IllegalArgumentException("competitive_rule.positions.overlap");
            }
            if (p.getRewardType() == RewardType.FLAT) {
                if (p.getFlatAmount() == null || p.getRewardCurrency() == null || p.getRewardPct() != null) {
                    throw new IllegalArgumentException("competitive_rule.positions.reward_xor");
                }
                if (p.getRewardMinAmount() != null || p.getRewardMaxAmount() != null) {
                    throw new IllegalArgumentException("competitive_rule.positions.minmax_percentage_only");
                }
            } else {
                if (p.getRewardPct() == null || p.getFlatAmount() != null) {
                    throw new IllegalArgumentException("competitive_rule.positions.reward_xor");
                }
                // PERCENTAGE only makes sense against an amount metric — a count metric has no
                // "amount" to take a percentage of.
                if (COUNT_METRICS.contains(rule.getMetric())) {
                    throw new IllegalArgumentException("competitive_rule.positions.percentage_requires_amount_metric");
                }
            }
            if (p.getRewardMinAmount() != null && p.getRewardMaxAmount() != null
                    && p.getRewardMaxAmount().compareTo(p.getRewardMinAmount()) < 0) {
                throw new IllegalArgumentException("competitive_rule.positions.minmax_order");
            }
            if (rule.getCompetitionType() != CompetitionType.RANKING
                    && (p.getMinThresholdCount() != null || p.getMinThresholdAmount() != null)) {
                throw new IllegalArgumentException("competitive_rule.positions.min_threshold_ranking_only");
            }
        }
    }

    /** Campaign must exist/be active/enabled; the rule's own window must sit inside it (dates are inherited when missing). */
    private void validateCampaignWindow(CompetitiveCommissionRule rule) {
        Campaign campaign = rule.getCampaign();
        if (campaign == null) {
            return;
        }
        if (!campaign.isActive() || !campaign.isEnabled()) {
            throw new IllegalArgumentException("competitive_rule.campaign.inactive");
        }
        if (rule.getStartsAt() != null && campaign.getStartsAt() != null && rule.getStartsAt().isBefore(campaign.getStartsAt())) {
            throw new IllegalArgumentException("competitive_rule.dates_outside_campaign");
        }
        if (rule.getEndsAt() != null && campaign.getEndsAt() != null && rule.getEndsAt().isAfter(campaign.getEndsAt())) {
            throw new IllegalArgumentException("competitive_rule.dates_outside_campaign");
        }
    }

    /** D16 — competitionGroup/groupPriority go together, and every rule in a group shares metric/type/accrual axis+anchor/window. */
    private void validateGroup(CompetitiveCommissionRule rule) {
        boolean hasGroup = rule.getCompetitionGroup() != null;
        boolean hasPriority = rule.getGroupPriority() != null;
        if (hasGroup != hasPriority) {
            throw new IllegalArgumentException("competitive_rule.group_priority_required");
        }
        if (!hasGroup) {
            return;
        }
        for (CompetitiveCommissionRule other : repository.findByCompetitionGroupAndActiveTrue(rule.getCompetitionGroup())) {
            if (other.getUuid() != null && other.getUuid().equals(rule.getUuid())) {
                continue;
            }
            boolean mismatch = other.getMetric() != rule.getMetric()
                    || other.getCompetitionType() != rule.getCompetitionType()
                    || other.getAccrualPeriodStrategy() != rule.getAccrualPeriodStrategy()
                    || !Objects.equals(other.getAccrualPeriodAnchor(), rule.getAccrualPeriodAnchor())
                    || !Objects.equals(other.getStartsAt(), rule.getStartsAt())
                    || !Objects.equals(other.getEndsAt(), rule.getEndsAt());
            if (mismatch) {
                throw new IllegalArgumentException("competitive_rule.group_mismatch");
            }
        }
    }

    /**
     * D14's settlement matrix + D15 (via {@code SettlementAxes}). Competitive
     * rules never have a live retroactive axis in v1 (neither RANKING nor
     * FIRST_TO_REACH — the position isn't confirmed early enough for one to
     * mean anything), so it's always collapsed to {@code partial}.
     */
    private static void applySettlementAxesResolution(CompetitiveCommissionRule rule) {
        String accrual = rule.getAccrualPeriodStrategy().name();
        String partial = rule.getPartialSettlementPeriodStrategy().name();

        if (rule.getCompetitionType() == CompetitionType.RANKING) {
            // D14: RANKING only ever pays at the accrual close or at the rule's ends_at —
            // never a genuine partial cut, because the position isn't final until then.
            if (!partial.equals(accrual) && !"END_DATE".equals(partial)) {
                throw new IllegalArgumentException("competitive_rule.ranking_close_only");
            }
            String finalStrategy = rule.getFinalSettlementPeriodStrategy().name();
            if (!finalStrategy.equals(accrual) && !"END_DATE".equals(finalStrategy)) {
                throw new IllegalArgumentException("competitive_rule.ranking_close_only");
            }
        } else {
            // FIRST_TO_REACH: partial/final must each be no coarser than accrual (or END_DATE).
            if (!SettlementAxes.isNoCoarserThanAccrual(accrual, partial)) {
                throw new IllegalArgumentException("settlement_axes.partial_coarser_than_accrual");
            }
            String finalStrategy = rule.getFinalSettlementPeriodStrategy().name();
            if (!SettlementAxes.isNoCoarserThanAccrual(accrual, finalStrategy)) {
                throw new IllegalArgumentException("settlement_axes.partial_coarser_than_accrual");
            }
        }

        // Neither type ever has a live retroactive axis (D14) — collapse to partial, no anchor.
        rule.setRetroactiveSettlementPeriodStrategy(PeriodAxisStrategy.valueOf(partial));
        rule.setRetroactiveSettlementPeriodAnchor(null);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Full-replace of {@link CompetitiveCommissionRule#getPositions()} — bidirectional, orphanRemoval handles the diff. */
    private void applyPositions(CompetitiveCommissionRule rule, List<CompetitiveRulePositionRequest> requests) {
        rule.getPositions().clear();
        for (CompetitiveRulePositionRequest req : requests) {
            CompetitiveCommissionRulePosition position = new CompetitiveCommissionRulePosition();
            position.setRule(rule);
            position.setPositionFrom(req.positionFrom());
            position.setPositionTo(req.positionTo());
            position.setLabel(req.label());
            position.setRewardType(req.rewardType());
            position.setFlatAmount(req.flatAmount());
            position.setRewardPct(req.rewardPct());
            position.setRewardCurrency(resolveCurrency(req.rewardCurrencyUuid()));
            position.setRewardMinAmount(req.rewardMinAmount());
            position.setRewardMaxAmount(req.rewardMaxAmount());
            position.setMinThresholdCount(req.minThresholdCount());
            position.setMinThresholdAmount(req.minThresholdAmount());
            rule.getPositions().add(position);
        }
    }

    /** Copies the campaign's dates onto the rule when its own are missing (hub plan §5 — the frontend already autofills this too). */
    private static void inheritCampaignDates(CompetitiveCommissionRule rule) {
        if (rule.getCampaign() == null) {
            return;
        }
        if (rule.getStartsAt() == null) {
            rule.setStartsAt(rule.getCampaign().getStartsAt());
        }
        if (rule.getEndsAt() == null) {
            rule.setEndsAt(rule.getCampaign().getEndsAt());
        }
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

    /** Empty/null = applies to every promoter type (same convention as the 4 legacy rule tables, V137). */
    private Set<PromoterType> resolvePromoterTypes(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return new HashSet<>();
        Set<PromoterType> resolved = new HashSet<>();
        for (UUID uuid : uuids) {
            resolved.add(promoterTypeRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter_type.not_found")));
        }
        return resolved;
    }

    /** Empty/null = applies to every rank — first M:N by rank scope in the codebase (see entity Javadoc). */
    private Set<PromoterRank> resolveRanks(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return new HashSet<>();
        Set<PromoterRank> resolved = new HashSet<>();
        for (UUID uuid : uuids) {
            resolved.add(rankRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found")));
        }
        return resolved;
    }

    private CompetitiveCommissionRule findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("competitive_commission_rule.not_found"));
    }

    private static Specification<CompetitiveCommissionRule> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
