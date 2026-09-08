package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingResponse.BeneficiaryReRatingOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Month-close retroactive re-rating for {@code promoter_hierarchy_overrides}
 * (V102, hub plan ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2,
 * PR3) — sibling of {@code CommissionReRatingService}, same "mutate PENDING
 * rows in place, never touch PAID/VOIDED" rule.
 *
 * <p>Two passes, always in this order:</p>
 * <ol>
 *   <li><b>Basis resync</b> ({@link #resyncBasisAmounts}): {@code
 *       HierarchyOverrideService.cascadeFrom} snapshots {@code basisAmount}
 *       from its source at cascade time — if the source {@link Commission}
 *       (or, for a level-3+ row, the source override) is itself re-rated
 *       afterward, that snapshot goes stale. This pass walks every PENDING
 *       override whose {@code basisAmount} no longer matches its source's
 *       current amount, recomputes {@code amount} from the row's own
 *       (unchanged) tier, and repeats until stable — so a change to a
 *       level-2 override is visible to a dependent level-3 override in the
 *       very same run, cascading exactly one level at a time like the
 *       original calculation.</li>
 *   <li><b>Band re-rate</b> ({@link #reRateBands}): once every basis is
 *       current, re-rates each (beneficiary, category) group to the single
 *       highest team-volume band the beneficiary's team reached across the
 *       <i>whole</i> requested period — same "no progressive blend" rule as
 *       {@code CommissionReRatingService}.</li>
 * </ol>
 *
 * <p>Per-row audit trail is a documented gap for this PR (would need a
 * {@code PromoterHierarchyOverride}-specific recorder, same shape as {@code
 * CommissionAuditRecorder}) — deferred, not blocking, since {@code
 * audit_entity_config} already fails safe on a missing writer.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HierarchyOverrideReRatingService {

    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final HierarchyOverrideTierRepository tierRepository;
    private final PromoterHierarchyService hierarchyService;
    private final MemberRepository memberRepository;
    private final CommissionRepository commissionRepository;

    /** Fixed-point-loop guard for {@link #resyncBasisAmounts} — generous relative to any real hierarchy depth. */
    private static final int MAX_RESYNC_PASSES = 20;

    @Transactional
    public HierarchyOverrideReRatingResponse execute(HierarchyOverrideReRatingRequest request) {
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<PromoterHierarchyOverride> pending =
                overrideRepository.findPendingForPeriod(request.periodStart(), request.periodEnd());

        // Resolved (possibly resynced) basis/amount per override id — read by
        // reRateBands instead of the entity's own fields directly, so a
        // dry-run preview sees phase 1's effect even though nothing was
        // actually mutated yet. Populated for every row up front so phase 2
        // always has an entry to read.
        Map<Long, BigDecimal> resolvedBasis = new HashMap<>();
        Map<Long, BigDecimal> resolvedAmount = new HashMap<>();
        Map<Long, HierarchyOverrideTier> resolvedTier = new HashMap<>();
        for (PromoterHierarchyOverride override : pending) {
            resolvedBasis.put(override.getId(), override.getBasisAmount());
            resolvedAmount.put(override.getId(), override.getAmount());
            resolvedTier.put(override.getId(), override.getTier());
        }

        BigDecimal resyncDelta = resyncBasisAmounts(pending, resolvedBasis, resolvedAmount);
        List<BeneficiaryReRatingOutcome> outcomes = reRateBands(pending, resolvedBasis, resolvedAmount, resolvedTier, request);

        if (!dryRun) {
            for (PromoterHierarchyOverride override : pending) {
                Long id = override.getId();
                override.setBasisAmount(resolvedBasis.get(id));
                override.setAmount(resolvedAmount.get(id));
                override.setTier(resolvedTier.get(id));
            }
        }

        int overridesUpdated = outcomes.stream().mapToInt(BeneficiaryReRatingOutcome::overridesChanged).sum();
        BigDecimal totalDelta = resyncDelta.add(outcomes.stream()
                .map(BeneficiaryReRatingOutcome::deltaAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        log.info("HIERARCHY_OVERRIDE_RE_RATING period={}..{} dryRun={} beneficiaries={} overridesUpdated={} delta={}",
                request.periodStart(), request.periodEnd(), dryRun, outcomes.size(), overridesUpdated, totalDelta);

        return new HierarchyOverrideReRatingResponse(request.periodStart(), request.periodEnd(), dryRun,
                outcomes.size(), overridesUpdated, totalDelta, "USD", Instant.now(), outcomes);
    }

    /**
     * Fixed-point loop over in-memory maps only (never mutates the entities
     * directly — {@link #execute} applies the final values afterward, only
     * when {@code !dryRun}) — so a dry-run preview computes the exact same
     * numbers a real run would, and each override contributes to {@code
     * totalDelta} exactly once regardless of how many passes converge it.
     * {@code resolvedAmount.get(sourceOverride.getId())} — not the entity's
     * own possibly-stale {@code getAmount()} — is what lets a dependent
     * level-3+ row see a level-2 row's resync from earlier in this same run.
     */
    private BigDecimal resyncBasisAmounts(List<PromoterHierarchyOverride> pending,
                                          Map<Long, BigDecimal> resolvedBasis, Map<Long, BigDecimal> resolvedAmount) {
        BigDecimal totalDelta = BigDecimal.ZERO;
        Set<Long> resynced = new HashSet<>();
        boolean changed = true;
        int pass = 0;
        while (changed && pass++ < MAX_RESYNC_PASSES) {
            changed = false;
            for (PromoterHierarchyOverride override : pending) {
                Long id = override.getId();
                if (resynced.contains(id)) {
                    continue; // each override resyncs at most once per run
                }
                BigDecimal currentSourceAmount = override.getSourceCommission() != null
                        ? override.getSourceCommission().getAmount()
                        : override.getSourceOverride() != null
                                ? resolvedAmount.getOrDefault(override.getSourceOverride().getId(), override.getSourceOverride().getAmount())
                                : null;
                if (currentSourceAmount == null || currentSourceAmount.compareTo(resolvedBasis.get(id)) == 0) {
                    continue; // not stale (yet) — a dependency may resync later this pass or a future one
                }
                BigDecimal newAmount = recompute(override.getTier(), currentSourceAmount);
                totalDelta = totalDelta.add(newAmount.subtract(resolvedAmount.get(id)));
                resolvedBasis.put(id, currentSourceAmount);
                resolvedAmount.put(id, newAmount);
                resynced.add(id);
                changed = true;
            }
        }
        if (pass >= MAX_RESYNC_PASSES && changed) {
            log.warn("Hierarchy override basis resync did not converge within {} passes — possible cycle in source chain", MAX_RESYNC_PASSES);
        }
        return totalDelta;
    }

    /**
     * Re-rates each (beneficiary, category) group to the highest band their
     * team's volume reached across the whole requested period — mirrors
     * {@code CommissionReRatingService.execute}'s grouping/selection shape.
     */
    private List<BeneficiaryReRatingOutcome> reRateBands(List<PromoterHierarchyOverride> pending,
                                                          Map<Long, BigDecimal> resolvedBasis,
                                                          Map<Long, BigDecimal> resolvedAmount,
                                                          Map<Long, HierarchyOverrideTier> resolvedTier,
                                                          HierarchyOverrideReRatingRequest request) {
        LocalDate periodStart = request.periodStart();
        LocalDate periodEnd = request.periodEnd();
        Instant asOf = periodEnd.atStartOfDay(AppTimeZone.ZONE).toInstant();

        Map<BeneficiaryCategory, List<PromoterHierarchyOverride>> grouped = pending.stream()
                .collect(Collectors.groupingBy(o -> new BeneficiaryCategory(o.getPromoter().getId(), o.getCategory())));

        List<BeneficiaryReRatingOutcome> outcomes = new ArrayList<>();
        for (Map.Entry<BeneficiaryCategory, List<PromoterHierarchyOverride>> entry : grouped.entrySet()) {
            List<PromoterHierarchyOverride> rows = entry.getValue();
            Promoter beneficiary = rows.get(0).getPromoter();
            OverrideCategory category = entry.getKey().category();

            if (beneficiary.getRank() == null) {
                log.warn("Re-rating skipped for {}: no rank set", beneficiary.getReferralCode());
                continue;
            }
            Set<Long> team = hierarchyService.resolveTeamMemberIds(beneficiary.getId(), asOf);
            long count = team.isEmpty() ? 0 : (category == OverrideCategory.INSCRIPTION
                    ? memberRepository.countNewSubscribersForPromoters(team, periodStart, periodEnd)
                    : commissionRepository.countByPromotersAppliesToInPeriod(team, Commission.AppliesTo.MONTHLY, periodStart, periodEnd));

            HierarchyOverrideTier target = highestQualifyingTier(beneficiary, category, count);
            if (target == null) {
                log.warn("Re-rating skipped for {}: no applicable {} tier for rank {}",
                        beneficiary.getReferralCode(), category, beneficiary.getRank().getCode());
                continue;
            }

            BigDecimal groupDelta = BigDecimal.ZERO;
            int groupChanged = 0;
            for (PromoterHierarchyOverride override : rows) {
                Long id = override.getId();
                BigDecimal basis = resolvedBasis.get(id);
                BigDecimal currentAmount = resolvedAmount.get(id);
                BigDecimal newAmount = recompute(target, basis);
                if (target.getId().equals(resolvedTier.get(id).getId()) && newAmount.compareTo(currentAmount) == 0) {
                    continue; // already at the target band with a current basis — no-op, re-runs stay idempotent
                }
                groupDelta = groupDelta.add(newAmount.subtract(currentAmount));
                groupChanged++;
                resolvedAmount.put(id, newAmount);
                resolvedTier.put(id, target);
            }

            outcomes.add(new BeneficiaryReRatingOutcome(
                    beneficiary.getUuid(), beneficiary.getReferralCode(), beneficiary.getDisplayName(),
                    category.name(), count, target.getUuid(), target.getName(), groupChanged, groupDelta));
        }
        return outcomes;
    }

    /**
     * Highest band the beneficiary's rank qualifies for in {@code category},
     * by team volume — same "iterate highest-threshold-first, first
     * qualifying wins" rule as {@code HierarchyOverrideService.selectTier}.
     */
    private HierarchyOverrideTier highestQualifyingTier(Promoter beneficiary, OverrideCategory category, long count) {
        List<HierarchyOverrideTier> candidates =
                tierRepository.findActiveApplicable(beneficiary.getRank().getId(), category);
        for (HierarchyOverrideTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    private static BigDecimal recompute(HierarchyOverrideTier tier, BigDecimal basisAmount) {
        return tier.getOverridePct() != null
                ? basisAmount.multiply(tier.getOverridePct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : tier.getFlatAmount();
    }

    private record BeneficiaryCategory(Long promoterId, OverrideCategory category) {}
}
