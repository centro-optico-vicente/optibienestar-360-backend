package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.Kind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTie;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTieCandidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.OpenTie;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.PositionSpec;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.ProjectedAward;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.RankingResult;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionManualDecisionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionTieRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evaluates one {@link CompetitiveCommissionRule} for one period (hub plan
 * competitive-commission-rules, Fase 2b): resolves the window, asks the
 * matching {@link CompetitiveMetricProvider} for events/totals, runs {@link
 * CompetitiveRankingEngine}, and reconciles the projection against existing
 * awards.
 *
 * <p><b>Reconciliation (§7):</b> a promoter with a {@code PENDING}/{@code PAID}
 * award is fed back into the engine as a <i>pin</i> — the same mechanism D16
 * uses for a human's decision, generalized: once an award is confirmed, its
 * position is never renegotiated by a later run, and the engine only reorders
 * what's still free. A {@code PROVISIONAL} award whose promoter no longer
 * wins anything is voided ({@code DISPLACED}); one that still wins is
 * upserted in place. Settlement (D14, paying what's owed) and D16 pins from
 * manual decisions are Fase 2c/later — this service only decides who's
 * winning, never pays.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitiveCommissionEvaluationService {

    private static final Set<CompetitiveMetric> COUNT_METRICS = EnumSet.of(
            CompetitiveMetric.NEW_SUBSCRIBERS, CompetitiveMetric.ACTIVE_SUBSCRIBERS,
            CompetitiveMetric.SALES_COUNT, CompetitiveMetric.COLLECTION_COUNT, CompetitiveMetric.ADVANCE_COUNT,
            CompetitiveMetric.OVERDUE_SETTLED_COUNT);

    private final CompetitiveCommissionRuleRepository ruleRepository;
    private final CompetitiveCommissionAwardRepository awardRepository;
    private final CompetitiveCommissionTieRepository tieRepository;
    private final CompetitiveCommissionManualDecisionRepository manualDecisionRepository;
    private final PromoterRepository promoterRepository;
    private final List<CompetitiveMetricProvider> providers;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    private Map<CompetitiveMetric, CompetitiveMetricProvider> providerByMetric;

    @PostConstruct
    void indexProviders() {
        providerByMetric = providers.stream()
                .collect(Collectors.toMap(CompetitiveMetricProvider::metric, p -> p));
    }

    public record EvaluationOutcome(LocalDate periodStart, LocalDate periodEnd, int created, int updated,
                                     int displaced, OpenTie openTie, UUID openTieUuid) {
    }

    /**
     * Evaluates the rule for the accrual window containing {@code periodRef}. Takes an advisory
     * lock keyed on the rule's uuid so a manual recalculation can never race the scheduled job
     * (§7) — released automatically at transaction end. Fetches the rule itself (rather than
     * taking the entity as a parameter) and runs in its own new transaction (§7: "cada regla se
     * evalúa en su propia transacción") — the job runner loops calling this once per active rule,
     * so one rule's error can never abort the others, and a caller never risks passing in an
     * entity detached from any active persistence context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EvaluationOutcome evaluateRule(UUID ruleUuid, LocalDate periodRef, boolean dryRun) {
        CompetitiveCommissionRule rule = ruleRepository.findByUuid(ruleUuid)
                .orElseThrow(() -> new java.util.NoSuchElementException("competitive_commission_rule.not_found"));
        lockRule(rule);
        PeriodStrategies.Window window = CompetitiveRuleWindowResolver.resolveWindow(rule, periodRef);
        MetricScope scope = CompetitiveRuleWindowResolver.buildScope(rule);
        CompetitiveMetricProvider provider = providerByMetric.get(rule.getMetric());
        if (provider == null) {
            throw new IllegalStateException("competitive_rule.no_provider_for_metric:" + rule.getMetric());
        }
        boolean countMetric = COUNT_METRICS.contains(rule.getMetric());
        boolean firstToReach = rule.getCompetitionType() == CompetitionType.FIRST_TO_REACH;

        List<Candidate> candidates = firstToReach
                ? CompetitiveRankingEngine.firstToReach(
                        provider.events(window, rule.getAchievementDateBasis(), scope), threshold(rule, countMetric))
                : provider.snapshot(window, rule.getAchievementDateBasis(), scope);

        List<PositionSpec> positions = rule.getPositions().stream()
                .map(p -> new PositionSpec(p.getPositionFrom(), p.getPositionTo(),
                        p.getMinThresholdCount(), p.getMinThresholdAmount()))
                .toList();

        List<CompetitiveCommissionAward> existing = awardRepository
                .findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(rule.getId(), window.start(), "VOIDED");
        Map<Long, CompetitiveCommissionAward> existingByPromoter = existing.stream()
                .collect(Collectors.toMap(a -> a.getPromoter().getId(), a -> a));

        Map<Integer, Long> pins = new HashMap<>();
        for (CompetitiveCommissionAward award : existing) {
            if (!"PROVISIONAL".equals(award.getStatus())) {
                pins.put(award.getAwardPosition(), award.getPromoter().getId());
            }
        }
        Set<Long> exclusions = new java.util.HashSet<>();
        applyManualDecisions(rule, window.start(), pins, exclusions);
        applyGroupExclusions(rule, window.start(), exclusions);

        CompetitiveRankingEngine.TiePolicy enginePolicy = CompetitiveRankingEngine.TiePolicy.valueOf(rule.getTiePolicy().name());
        RankingResult result = CompetitiveRankingEngine.rank(candidates, positions, countMetric,
                !firstToReach, firstToReach, enginePolicy, pins, exclusions);

        UUID openTieUuid = null;
        if (!dryRun) {
            openTieUuid = upsertOpenTie(rule, window, result.openTie());
        }

        Set<Long> winningPromoterIds = result.awards().stream()
                .map(ProjectedAward::promoterId).collect(Collectors.toSet());

        int created = 0, updated = 0, displaced = 0;

        for (CompetitiveCommissionAward award : existing) {
            if ("PROVISIONAL".equals(award.getStatus()) && !winningPromoterIds.contains(award.getPromoter().getId())) {
                displaced++;
                if (!dryRun) {
                    award.setStatus("VOIDED");
                    award.setVoidedAt(Instant.now());
                    award.setVoidReason("DISPLACED");
                }
            }
        }

        String freshStatus = (firstToReach && rule.getAchievementDateBasis() == AchievementDateBasis.APPROVED_AT)
                ? "PENDING" : "PROVISIONAL";

        for (ProjectedAward projected : result.awards()) {
            CompetitiveCommissionAward existingAward = existingByPromoter.get(projected.promoterId());
            if (existingAward != null && !"PROVISIONAL".equals(existingAward.getStatus())) {
                continue; // pinned — already confirmed/paid, never renegotiated
            }
            CompetitiveCommissionRulePosition position = findPosition(rule, projected.position());
            if (position == null) {
                log.warn("competitive_rule {} projected position {} matches no configured range — skipping",
                        rule.getUuid(), projected.position());
                continue;
            }
            if (existingAward != null) {
                updated++;
            } else {
                created++;
            }
            if (dryRun) {
                continue;
            }
            CompetitiveCommissionAward award = existingAward != null ? existingAward : new CompetitiveCommissionAward();
            if (existingAward == null) {
                award.setRule(rule);
                award.setPromoter(promoterRepository.getReferenceById(projected.promoterId()));
                award.setPeriodStart(window.start());
                award.setPeriodEnd(window.end());
            }
            applyProjection(award, rule, position, projected, freshStatus);
            awardRepository.save(award);
        }

        return new EvaluationOutcome(window.start(), window.end(), created, updated, displaced, result.openTie(), openTieUuid);
    }

    /**
     * Freezes PROVISIONAL awards whose period closed {@code confirmationDelayDays} ago or more
     * into PENDING — §7 step 2. Applies uniformly to both competition types: FIRST_TO_REACH rows
     * left PROVISIONAL (basis other than APPROVED_AT) get their one chance to firm up here too.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int confirmDuePeriods(UUID ruleUuid, LocalDate asOf) {
        CompetitiveCommissionRule rule = ruleRepository.findByUuid(ruleUuid)
                .orElseThrow(() -> new java.util.NoSuchElementException("competitive_commission_rule.not_found"));
        int confirmed = 0;
        for (CompetitiveCommissionAward award : awardRepository
                .findByRule_IdAndActiveTrueAndStatus(rule.getId(), "PROVISIONAL")) {
            LocalDate dueAt = award.getPeriodEnd().plusDays(rule.getConfirmationDelayDays());
            if (!dueAt.isAfter(asOf)) {
                award.setStatus("PENDING");
                award.setConfirmedAt(Instant.now());
                confirmed++;
            }
        }
        return confirmed;
    }

    private void lockRule(CompetitiveCommissionRule rule) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended('ccr:' || :ruleUuid, 0))")
                .setParameter("ruleUuid", rule.getUuid().toString())
                .getSingleResult();
    }

    private static BigDecimal threshold(CompetitiveCommissionRule rule, boolean countMetric) {
        return countMetric ? BigDecimal.valueOf(rule.getThresholdCount()) : rule.getThresholdAmount();
    }

    /**
     * D16: every non-reverted decision of this rule+period becomes a pin (the winner the
     * coordinator picked) or an exclusion (a disqualified promoter) — read fresh on every run so
     * the job is idempotent and never overrides a human's call.
     */
    private void applyManualDecisions(CompetitiveCommissionRule rule, LocalDate periodStart,
                                       Map<Integer, Long> pins, Set<Long> exclusions) {
        for (CompetitiveCommissionManualDecision decision : manualDecisionRepository
                .findByRule_IdAndPeriodStartAndActiveTrueAndStatus(rule.getId(), periodStart, "ACTIVE")) {
            switch (decision.getKind()) {
                case TIE_RESOLUTION, REDIRECT -> {
                    if (decision.getAwardPosition() != null) {
                        pins.put(decision.getAwardPosition(), decision.getPromoter().getId());
                    }
                    if (decision.getKind() == Kind.REDIRECT && decision.getReplacedPromoter() != null) {
                        exclusions.add(decision.getReplacedPromoter().getId());
                    }
                }
                case DISQUALIFY -> exclusions.add(decision.getPromoter().getId());
            }
        }
    }

    /**
     * D16 competition groups: a promoter who already won a strictly higher-priority sibling rule
     * (lower {@code group_priority} number) for the same period is excluded here — "at most one
     * prize per group per period." A group-wide DISQUALIFY ({@code excludeFromGroup}) on ANY
     * sibling rule (regardless of priority order) excludes that promoter everywhere in the group.
     */
    private void applyGroupExclusions(CompetitiveCommissionRule rule, LocalDate periodStart, Set<Long> exclusions) {
        if (rule.getCompetitionGroup() == null) {
            return;
        }
        List<CompetitiveCommissionRule> siblings = ruleRepository.findByCompetitionGroupAndActiveTrue(rule.getCompetitionGroup());
        List<Long> siblingIds = siblings.stream().map(CompetitiveCommissionRule::getId).toList();

        for (CompetitiveCommissionRule sibling : siblings) {
            if (sibling.getId().equals(rule.getId()) || sibling.getGroupPriority() == null
                    || rule.getGroupPriority() == null || sibling.getGroupPriority() >= rule.getGroupPriority()) {
                continue;
            }
            for (CompetitiveCommissionAward award : awardRepository
                    .findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(sibling.getId(), periodStart, "VOIDED")) {
                exclusions.add(award.getPromoter().getId());
            }
        }
        for (CompetitiveCommissionManualDecision decision : manualDecisionRepository
                .findByRule_IdInAndPeriodStartAndActiveTrueAndStatusAndExcludeFromGroupTrue(siblingIds, periodStart, "ACTIVE")) {
            exclusions.add(decision.getPromoter().getId());
        }
    }

    /**
     * Persists the engine's {@link OpenTie} (if any) as a {@code competitive_commission_ties} row
     * with its candidates, updating an existing OPEN one in place ({@code uq_cct_open}: at most one
     * per rule/period/position). No open tie this run and one was previously open at that position
     * means it's now resolved by the automatic criteria alone — left as-is; a coordinator never
     * needs to act on it and the unique index already prevents it from blocking anything.
     */
    private UUID upsertOpenTie(CompetitiveCommissionRule rule, PeriodStrategies.Window window, OpenTie openTie) {
        if (openTie == null) {
            return null;
        }
        CompetitiveCommissionTie tie = tieRepository
                .findByRule_IdAndPeriodStartAndPositionFromAndActiveTrueAndStatusIn(
                        rule.getId(), window.start(), openTie.positionFrom(), List.of("OPEN", "STALE"))
                .orElseGet(CompetitiveCommissionTie::new);
        tie.setRule(rule);
        tie.setPeriodStart(window.start());
        tie.setPeriodEnd(window.end());
        tie.setPositionFrom(openTie.positionFrom());
        tie.setSlots(openTie.slots());
        tie.setStatus("OPEN");
        tie.getCandidates().clear();
        for (Candidate candidate : openTie.candidates()) {
            CompetitiveCommissionTieCandidate row = new CompetitiveCommissionTieCandidate();
            row.setTie(tie);
            row.setPromoter(promoterRepository.getReferenceById(candidate.promoterId()));
            row.setMetricValue(candidate.value());
            row.setAchievedAt(candidate.achievedAt());
            row.setMetricTransactionCount(candidate.transactionCount());
            tie.getCandidates().add(row);
        }
        tieRepository.save(tie);
        return tie.getUuid();
    }

    private static CompetitiveCommissionRulePosition findPosition(CompetitiveCommissionRule rule, int position) {
        for (CompetitiveCommissionRulePosition candidate : rule.getPositions()) {
            if (position >= candidate.getPositionFrom() && position <= candidate.getPositionTo()) {
                return candidate;
            }
        }
        return null;
    }

    private void applyProjection(CompetitiveCommissionAward award, CompetitiveCommissionRule rule,
                                  CompetitiveCommissionRulePosition position, ProjectedAward projected, String status) {
        award.setPosition(position);
        award.setAwardPosition(projected.position());
        award.setTieGroupSize((short) projected.tieGroupSize());
        award.setMetricValue(projected.metricValue());
        award.setMetricTransactionCount(projected.transactionCount());
        award.setAchievedAt(projected.achievedAt());
        award.setSelectionSource(projected.selectionSource() == CompetitiveRankingEngine.SelectionSource.MANUAL
                ? CompetitiveCommissionAward.SelectionSource.MANUAL : CompetitiveCommissionAward.SelectionSource.AUTO);
        award.setRewardType(position.getRewardType());
        award.setCurrency(position.getRewardCurrency());

        BigDecimal amount;
        if (position.getRewardType() == RewardType.FLAT) {
            amount = position.getFlatAmount();
            award.setFlatAmount(position.getFlatAmount());
            award.setRewardPct(null);
            award.setBasisAmount(null);
        } else {
            BigDecimal basis = projected.metricValue();
            amount = basis.multiply(position.getRewardPct())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (position.getRewardMinAmount() != null && amount.compareTo(position.getRewardMinAmount()) < 0) {
                amount = position.getRewardMinAmount();
            }
            if (position.getRewardMaxAmount() != null && amount.compareTo(position.getRewardMaxAmount()) > 0) {
                amount = position.getRewardMaxAmount();
            }
            award.setRewardPct(position.getRewardPct());
            award.setBasisAmount(basis);
            award.setFlatAmount(null);
        }
        award.setAmount(amount);
        award.setRuleNameSnapshot(rule.getName());
        award.setSnapshotJson(buildSnapshot(rule, position));
        award.setStatus(status);
        if ("PENDING".equals(status) && award.getConfirmedAt() == null) {
            award.setConfirmedAt(Instant.now());
        }
    }

    private String buildSnapshot(CompetitiveCommissionRule rule, CompetitiveCommissionRulePosition position) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("ruleUuid", rule.getUuid());
        snapshot.put("metric", rule.getMetric().name());
        snapshot.put("competitionType", rule.getCompetitionType().name());
        snapshot.put("tiePolicy", rule.getTiePolicy().name());
        snapshot.put("achievementDateBasis", rule.getAchievementDateBasis().name());
        snapshot.put("positionFrom", position.getPositionFrom());
        snapshot.put("positionTo", position.getPositionTo());
        snapshot.put("campaignUuid", rule.getCampaign() != null ? rule.getCampaign().getUuid() : null);
        snapshot.put("timeZone", AppTimeZone.ZONE.getId());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("competitive_rule.snapshot_serialization_failed", e);
        }
    }
}
