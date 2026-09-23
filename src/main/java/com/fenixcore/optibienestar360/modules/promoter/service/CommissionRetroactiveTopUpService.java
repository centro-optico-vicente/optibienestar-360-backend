package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse.TopUpOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.LedgerType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUpCut;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.repository.CollectionCommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRetroactiveTopUpCutRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRetroactiveTopUpRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Month-close (or whatever settlement frequency is configured) retroactive
 * top-up for "corte parcial" payouts (V105, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3, PR4).
 *
 * <p>Complements — never duplicates — {@code CommissionReRatingService} /
 * {@code HierarchyOverrideReRatingService} (PR3): those bump {@code PENDING}
 * rows in place to the period's final highest-qualifying band; this service
 * covers the rows that were already {@code PAID} by an earlier partial cut
 * and are therefore off-limits to re-rating. Both act on disjoint row sets
 * (PENDING vs. PAID) targeting the very same final band, so running both at
 * a settlement close pays every dollar the correct final rate exactly
 * once — never a double top-up.</p>
 *
 * <p>Per-beneficiary result is upserted by the V105 unique key ({@code
 * promoter_id, ledger_type, period_start, period_end}), so re-running the
 * close for an already-closed period recomputes in place instead of
 * duplicating a row.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionRetroactiveTopUpService {

    private final CommissionRepository commissionRepository;
    private final CommissionTierRepository commissionTierRepository;
    private final CollectionCommissionTierRepository collectionCommissionTierRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    private final PromoterHierarchyService hierarchyService;
    private final MemberRepository memberRepository;
    private final CommissionRetroactiveTopUpRepository topUpRepository;
    private final CommissionRetroactiveTopUpCutRepository cutRepository;
    private final CommissionService commissionService;

    @Transactional
    public CommissionRetroactiveTopUpResponse execute(CommissionRetroactiveTopUpRequest request) {
        if (request.periodStart() == null || request.periodEnd() == null) {
            throw new IllegalArgumentException("retroactive_topup.period_required");
        }
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        LocalDate start = request.periodStart();
        LocalDate end = request.periodEnd();

        List<TopUpOutcome> outcomes = new ArrayList<>();
        outcomes.addAll(computeDirectInscriptionTopUps(start, end, dryRun));
        outcomes.addAll(computeHierarchyOverrideTopUps(start, end, dryRun));

        BigDecimal totalRetro = outcomes.stream()
                .map(TopUpOutcome::retroAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("COMMISSION_RETROACTIVE_TOPUP period={}..{} dryRun={} topUps={} totalRetro={}",
                start, end, dryRun, outcomes.size(), totalRetro);

        return new CommissionRetroactiveTopUpResponse(start, end, dryRun,
                outcomes.size(), totalRetro, "USD", Instant.now(), outcomes);
    }

    // ─── DIRECT_INSCRIPTION (from commissions) ─────────────────────────────

    private List<TopUpOutcome> computeDirectInscriptionTopUps(LocalDate start, LocalDate end, boolean dryRun) {
        List<Commission> paid = commissionRepository.findPaidForPeriod(start, end).stream()
                .filter(c -> c.getAppliesTo() == AppliesTo.INSCRIPTION)
                .toList();
        Map<Long, List<Commission>> byPromoter = paid.stream()
                .collect(Collectors.groupingBy(c -> c.getPromoter().getId()));

        List<TopUpOutcome> outcomes = new ArrayList<>();
        for (List<Commission> rows : byPromoter.values()) {
            Promoter promoter = rows.get(0).getPromoter();
            BigDecimal basis = sumCommissionBasis(rows);
            BigDecimal alreadyPaid = sumCommissionAmount(rows);
            Currency currency = rows.get(0).getCurrency();

            long count = memberRepository.countNewSubscribersForPromoter(promoter.getId(), start, end);
            CommissionTier target = highestQualifyingCommissionTier(count, promoter);
            if (target == null) {
                log.warn("Retroactive top-up skipped for promoter {} (DIRECT_INSCRIPTION): no applicable tier",
                        promoter.getReferralCode());
                continue;
            }

            BigDecimal targetAmount = recompute(target.getCommissionPct(), target.getFlatAmount(), basis);
            BigDecimal retro = targetAmount.subtract(alreadyPaid);
            if (retro.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // already at (or above, e.g. a manual adjustment) the final band — nothing owed
            }

            if (!dryRun) {
                upsert(promoter, LedgerType.DIRECT_INSCRIPTION, start, end, basis, targetAmount, alreadyPaid, retro,
                        currency, target.getId(), target.getName());
            }
            outcomes.add(new TopUpOutcome(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    LedgerType.DIRECT_INSCRIPTION.name(), basis, targetAmount, alreadyPaid, retro, target.getName()));
        }
        return outcomes;
    }

    private CommissionTier highestQualifyingCommissionTier(long count, Promoter promoter) {
        Long promoterTypeId = promoter.getPromoterType() != null ? promoter.getPromoterType().getId() : null;
        List<CommissionTier> candidates = commissionTierRepository.findActiveApplicable(
                null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, promoterTypeId);
        for (CommissionTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    private static BigDecimal sumCommissionBasis(List<Commission> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (Commission c : rows) total = total.add(c.getCalculationBasis());
        return total;
    }

    private static BigDecimal sumCommissionAmount(List<Commission> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (Commission c : rows) total = total.add(c.getAmount());
        return total;
    }

    // ─── HIERARCHY_OVERRIDE_INSCRIPTION / _COLLECTION (from overrides) ─────

    private List<TopUpOutcome> computeHierarchyOverrideTopUps(LocalDate start, LocalDate end, boolean dryRun) {
        List<PromoterHierarchyOverride> paid = overrideRepository.findPaidForPeriod(start, end);
        Map<BeneficiaryCategory, List<PromoterHierarchyOverride>> grouped = paid.stream()
                .collect(Collectors.groupingBy(o -> new BeneficiaryCategory(o.getPromoter().getId(), o.getCategory())));

        List<TopUpOutcome> outcomes = new ArrayList<>();
        Instant asOf = end.atStartOfDay(AppTimeZone.ZONE).toInstant();

        for (List<PromoterHierarchyOverride> rows : grouped.values()) {
            Promoter beneficiary = rows.get(0).getPromoter();
            OverrideCategory category = rows.get(0).getCategory();
            if (beneficiary.getRank() == null) {
                log.warn("Retroactive top-up skipped for {}: no rank set", beneficiary.getReferralCode());
                continue;
            }

            BigDecimal basis = sumOverrideBasis(rows);
            BigDecimal alreadyPaid = sumOverrideAmount(rows);
            Currency currency = rows.get(0).getCurrency();

            Set<Long> team = hierarchyService.resolveTeamMemberIds(beneficiary.getId(), asOf);
            long count = team.isEmpty() ? 0 : (category == OverrideCategory.INSCRIPTION
                    ? memberRepository.countNewSubscribersForPromoters(team, start, end)
                    : commissionRepository.countByPromotersAppliesToInPeriod(team, AppliesTo.MONTHLY, start, end));

            HierarchyOverrideTier target = highestQualifyingOverrideTier(beneficiary, category, count);
            if (target == null) {
                log.warn("Retroactive top-up skipped for {} ({}): no applicable tier",
                        beneficiary.getReferralCode(), category);
                continue;
            }

            BigDecimal targetAmount = recompute(target.getOverridePct(), target.getFlatAmount(), basis);
            BigDecimal retro = targetAmount.subtract(alreadyPaid);
            if (retro.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LedgerType ledgerType = category == OverrideCategory.INSCRIPTION
                    ? LedgerType.HIERARCHY_OVERRIDE_INSCRIPTION : LedgerType.HIERARCHY_OVERRIDE_COLLECTION;
            if (!dryRun) {
                upsert(beneficiary, ledgerType, start, end, basis, targetAmount, alreadyPaid, retro,
                        currency, target.getId(), target.getName());
            }
            outcomes.add(new TopUpOutcome(beneficiary.getUuid(), beneficiary.getReferralCode(), beneficiary.getDisplayName(),
                    ledgerType.name(), basis, targetAmount, alreadyPaid, retro, target.getName()));
        }
        return outcomes;
    }

    private HierarchyOverrideTier highestQualifyingOverrideTier(Promoter beneficiary, OverrideCategory category, long count) {
        List<HierarchyOverrideTier> candidates =
                hierarchyOverrideTierRepository.findActiveApplicable(beneficiary.getRank().getId(), category);
        for (HierarchyOverrideTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    private static BigDecimal sumOverrideBasis(List<PromoterHierarchyOverride> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (PromoterHierarchyOverride o : rows) total = total.add(o.getBasisAmount());
        return total;
    }

    private static BigDecimal sumOverrideAmount(List<PromoterHierarchyOverride> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (PromoterHierarchyOverride o : rows) total = total.add(o.getAmount());
        return total;
    }

    // ─── Shared ─────────────────────────────────────────────────────────────

    private static BigDecimal recompute(BigDecimal pct, BigDecimal flatAmount, BigDecimal basis) {
        return pct != null
                ? basis.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : flatAmount;
    }

    /** Upserts by the V105 unique key so re-running a closed period's top-up recomputes in place. */
    private void upsert(Promoter promoter, LedgerType ledgerType, LocalDate start, LocalDate end,
                        BigDecimal basis, BigDecimal target, BigDecimal alreadyPaid, BigDecimal retro,
                        Currency currency, Long tierId, String tierName) {
        CommissionRetroactiveTopUp topUp = topUpRepository
                .findByPromoterIdAndLedgerTypeAndPeriodStartAndPeriodEnd(promoter.getId(), ledgerType, start, end)
                .orElseGet(CommissionRetroactiveTopUp::new);
        // A previously-PAID top-up is a settled, out-of-band disbursement — never silently
        // overwritten by a recompute; the admin must void it explicitly first if it was wrong.
        if (CommissionRetroactiveTopUp.TopUpStatus.PAID.name().equals(topUp.getStatus())) {
            log.warn("Retroactive top-up recompute skipped for promoter {} ({}, {}..{}): already PAID",
                    promoter.getReferralCode(), ledgerType, start, end);
            return;
        }
        topUp.setPromoter(promoter);
        topUp.setLedgerType(ledgerType);
        topUp.setPeriodStart(start);
        topUp.setPeriodEnd(end);
        topUp.setBasisAmount(basis);
        topUp.setTargetAmount(target);
        topUp.setAlreadyPaidAmount(alreadyPaid);
        topUp.setRetroAmount(retro);
        topUp.setCurrency(currency);
        topUp.setTierId(tierId);
        topUp.setTierNameSnapshot(tierName);
        topUp.setStatus(CommissionRetroactiveTopUp.TopUpStatus.PENDING.name());
        topUpRepository.save(topUp);
    }

    private record BeneficiaryCategory(Long promoterId, OverrideCategory category) {}

    // ═══════════════════════════════════════════════════════════════════════
    // ─── CUT MODE (Fase A, retroactive settlement axis) ─────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * How far back of {@code asOf} the candidate-discovery net reaches when
     * looking for beneficiaries with recent PAID activity to evaluate a cut
     * for. This is deliberately wider than any single rule's accrual window
     * could be (the widest configured {@code PeriodStrategy} is {@code
     * ANNUAL}) — it is only used to build the candidate list, never to bound
     * the actual accrual/cut windows used for the math below, which are
     * always rule-derived from {@code asOf}.
     */
    private static final long CANDIDATE_NET_MONTHS = 12;

    /**
     * Cut-mode counterpart of {@link #execute(CommissionRetroactiveTopUpRequest)}
     * (Fase A, hub plan commission-frequency-currency-unification, retroactive
     * settlement axis). Computes ONE retroactive cut per {@link LedgerType} —
     * the {@code retroactiveSettlementPeriodStrategy} cut containing {@link
     * CommissionRetroactiveTopUpRequest#asOf()}, inside the accrual period
     * containing {@code asOf} — for every beneficiary with recent PAID
     * activity, and upserts the result into {@code commission_retroactive_topup_cuts}
     * (V150) instead of the legacy {@code commission_retroactive_topups} (V105)
     * table {@link #execute} writes to.
     *
     * <p>Unlike {@link #execute}, which is driven by an admin-supplied
     * {@code [periodStart, periodEnd]}, every window here is derived from
     * each beneficiary's own applicable rule's frequency axis
     * ({@code accrualPeriodStrategy}/{@code retroactiveSettlementPeriodStrategy}
     * + their anchors) evaluated against {@code asOf} — see {@link
     * PeriodStrategies#window} and {@link PeriodCutCalculator#cuts}.</p>
     */
    @Transactional
    public CommissionRetroactiveTopUpResponse executeCut(CommissionRetroactiveTopUpRequest request) {
        LocalDate asOf = request.asOf();
        if (asOf == null) {
            throw new IllegalArgumentException("retroactive_topup.as_of_required");
        }
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        Instant asOfInstant = asOf.atStartOfDay(AppTimeZone.ZONE).toInstant();
        LocalDate netStart = asOf.minusMonths(CANDIDATE_NET_MONTHS);

        List<TopUpOutcome> outcomes = new ArrayList<>();
        outcomes.addAll(cutDirectInscription(netStart, asOf, asOfInstant, dryRun));
        outcomes.addAll(cutDirectCollection(netStart, asOf, asOfInstant, dryRun));
        outcomes.addAll(cutHierarchyOverrides(netStart, asOf, asOfInstant, dryRun));

        BigDecimal totalRetro = outcomes.stream()
                .map(TopUpOutcome::retroAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("COMMISSION_RETROACTIVE_TOPUP_CUT asOf={} dryRun={} topUps={} totalRetro={}",
                asOf, dryRun, outcomes.size(), totalRetro);

        // periodStart/periodEnd are the legacy whole-period-mode fields — cut
        // mode has no single such window (each beneficiary's is different),
        // so both are left null here, same as the DTO's own cut-mode contract.
        return new CommissionRetroactiveTopUpResponse(null, null, dryRun,
                outcomes.size(), totalRetro, "USD", Instant.now(), outcomes);
    }

    // ─── DIRECT_INSCRIPTION (cut mode) ──────────────────────────────────────

    private List<TopUpOutcome> cutDirectInscription(LocalDate netStart, LocalDate asOf, Instant asOfInstant, boolean dryRun) {
        List<Commission> net = commissionRepository.findPaidForPeriod(netStart, asOf).stream()
                .filter(c -> c.getAppliesTo() == AppliesTo.INSCRIPTION)
                .toList();
        List<TopUpOutcome> outcomes = new ArrayList<>();
        for (Promoter promoter : distinctPromoters(net)) {
            Long promoterTypeId = promoter.getPromoterType() != null ? promoter.getPromoterType().getId() : null;

            // Assumption (documented per task instructions): when several candidate
            // tiers apply to this promoter, the highest-priority one (the same
            // ordering CommissionTierRepository#findActiveApplicable already uses
            // for tier selection) is also the one whose frequency axis "wins" for
            // deriving the accrual/cut windows below. There is no configured
            // ambiguity today (every tier of a given scope shares the same
            // frequency axis), so this never changes behavior in practice.
            List<CommissionTier> ruleCandidates = commissionTierRepository.findActiveApplicable(
                    null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, promoterTypeId);
            if (ruleCandidates.isEmpty()) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_INSCRIPTION): no applicable tier",
                        promoter.getReferralCode());
                continue;
            }
            CommissionTier rule = ruleCandidates.get(0);

            CutWindow cut = resolveCutWindow(rule.getAccrualPeriodStrategy().name(), rule.getAccrualPeriodAnchor(),
                    rule.getRetroactiveSettlementPeriodStrategy().name(), rule.getRetroactiveSettlementPeriodAnchor(), asOf);
            if (cut == null) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_INSCRIPTION): asOf {} outside every cut",
                        promoter.getReferralCode(), asOf);
                continue;
            }

            long count = memberRepository.countNewSubscribersForPromoter(promoter.getId(), cut.accrualWindow().start(), cut.cutEnd());
            CommissionTier target = highestQualifyingCommissionTier(count, promoter);
            if (target == null) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_INSCRIPTION): no qualifying tier",
                        promoter.getReferralCode());
                continue;
            }

            List<Commission> paidInWindow = commissionRepository.findPaidForPromoterAppliesToInPeriod(
                    promoter.getId(), AppliesTo.INSCRIPTION, cut.accrualWindow().start(), cut.cutEnd());
            BigDecimal basisCumulative = sumCommissionBasis(paidInWindow);
            BigDecimal alreadyPaidBase = sumCommissionAmount(paidInWindow);
            Currency currency = resolveCurrency(paidInWindow.isEmpty() ? null : paidInWindow.get(0).getCurrency(),
                    target.getFlatAmountCurrency());
            if (currency == null) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_INSCRIPTION): no resolvable currency",
                        promoter.getReferralCode());
                continue;
            }

            BigDecimal targetAmountCumulative = recompute(target.getCommissionPct(), target.getFlatAmount(), basisCumulative);
            BigDecimal alreadyPaidRetro = cutRepository.sumPaidRetroBeforeSequence(
                    promoter.getId(), LedgerType.DIRECT_INSCRIPTION, cut.accrualWindow().start(), cut.accrualWindow().end(), cut.sequence());
            BigDecimal alreadyPaid = alreadyPaidBase.add(alreadyPaidRetro);
            BigDecimal retro = targetAmountCumulative.subtract(alreadyPaid);
            if (retro.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!dryRun) {
                upsertCut(promoter, LedgerType.DIRECT_INSCRIPTION, cut, basisCumulative, targetAmountCumulative,
                        alreadyPaid, retro, currency, target.getId(), target.getName());
            }
            outcomes.add(new TopUpOutcome(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    LedgerType.DIRECT_INSCRIPTION.name(), basisCumulative, targetAmountCumulative, alreadyPaid, retro,
                    target.getName(), cut.sequence(), cut.accrualWindow().start(), cut.accrualWindow().end(),
                    cut.cutStart(), cut.cutEnd()));
        }
        return outcomes;
    }

    // ─── DIRECT_COLLECTION (cut mode — new ledger, never calculated before) ─

    private List<TopUpOutcome> cutDirectCollection(LocalDate netStart, LocalDate asOf, Instant asOfInstant, boolean dryRun) {
        List<Commission> net = commissionRepository.findPaidForPeriod(netStart, asOf).stream()
                .filter(c -> c.getAppliesTo() == AppliesTo.MONTHLY)
                .toList();
        List<TopUpOutcome> outcomes = new ArrayList<>();
        for (Promoter promoter : distinctPromoters(net)) {
            Long promoterTypeId = promoter.getPromoterType() != null ? promoter.getPromoterType().getId() : null;

            // Same "first candidate carries the frequency axis" assumption as
            // cutDirectInscription — here scoped to the basis=AMOUNT collection
            // candidates, since a cumulative multi-payment basis (see the
            // TODO(revisar) below) only ever resolves through that lookup.
            List<CollectionCommissionTier> ruleCandidates =
                    collectionCommissionTierRepository.findActiveApplicableByAmount(promoterTypeId);
            if (ruleCandidates.isEmpty()) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_COLLECTION): no applicable tier",
                        promoter.getReferralCode());
                continue;
            }
            CollectionCommissionTier rule = ruleCandidates.get(0);

            CutWindow cut = resolveCutWindow(rule.getAccrualPeriodStrategy().name(), rule.getAccrualPeriodAnchor(),
                    rule.getRetroactiveSettlementPeriodStrategy().name(), rule.getRetroactiveSettlementPeriodAnchor(), asOf);
            if (cut == null) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_COLLECTION): asOf {} outside every cut",
                        promoter.getReferralCode(), asOf);
                continue;
            }

            List<Commission> paidInWindow = commissionRepository.findPaidForPromoterAppliesToInPeriod(
                    promoter.getId(), AppliesTo.MONTHLY, cut.accrualWindow().start(), cut.cutEnd());
            BigDecimal basisCumulative = sumCommissionBasis(paidInWindow);
            BigDecimal alreadyPaidBase = sumCommissionAmount(paidInWindow);
            Currency currency = resolveCurrency(paidInWindow.isEmpty() ? null : paidInWindow.get(0).getCurrency(),
                    rule.getMinAmountCurrency() != null ? rule.getMinAmountCurrency() : rule.getFlatAmountCurrency());
            if (currency == null) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_COLLECTION): no resolvable currency",
                        promoter.getReferralCode());
                continue;
            }

            // TODO(revisar): collection-commission tiers are normally selected
            // per-payment by how many days late THAT payment was collected
            // (selectCollectionTier's basis=DAYS path) — there is no single
            // "days late" figure for a cumulative multi-payment cut basis, so
            // basis=DAYS buckets are structurally inapplicable here. We pass
            // Integer.MAX_VALUE as `days` so selectCollectionTier's DAYS lookup
            // (which requires maxDays >= days) never matches, forcing the
            // basis=AMOUNT fallback path — the same cumulative-threshold model
            // used elsewhere in this method. If the business wants a DAYS-based
            // retroactive cut too, this needs its own product decision on what
            // "days late" cumulatively means across several payments.
            Optional<CollectionCommissionTier> targetOpt = commissionService.selectCollectionTier(
                    Integer.MAX_VALUE, basisCumulative, currency, asOfInstant, promoterTypeId);
            if (targetOpt.isEmpty()) {
                log.warn("Retroactive top-up cut skipped for promoter {} (DIRECT_COLLECTION): no qualifying tier",
                        promoter.getReferralCode());
                continue;
            }
            CollectionCommissionTier target = targetOpt.get();

            BigDecimal targetAmountCumulative = recompute(target.getCommissionPct(), target.getFlatAmount(), basisCumulative);
            BigDecimal alreadyPaidRetro = cutRepository.sumPaidRetroBeforeSequence(
                    promoter.getId(), LedgerType.DIRECT_COLLECTION, cut.accrualWindow().start(), cut.accrualWindow().end(), cut.sequence());
            BigDecimal alreadyPaid = alreadyPaidBase.add(alreadyPaidRetro);
            BigDecimal retro = targetAmountCumulative.subtract(alreadyPaid);
            if (retro.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!dryRun) {
                upsertCut(promoter, LedgerType.DIRECT_COLLECTION, cut, basisCumulative, targetAmountCumulative,
                        alreadyPaid, retro, currency, target.getId(), target.getName());
            }
            outcomes.add(new TopUpOutcome(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    LedgerType.DIRECT_COLLECTION.name(), basisCumulative, targetAmountCumulative, alreadyPaid, retro,
                    target.getName(), cut.sequence(), cut.accrualWindow().start(), cut.accrualWindow().end(),
                    cut.cutStart(), cut.cutEnd()));
        }
        return outcomes;
    }

    // ─── HIERARCHY_OVERRIDE_INSCRIPTION / _COLLECTION (cut mode) ────────────

    private List<TopUpOutcome> cutHierarchyOverrides(LocalDate netStart, LocalDate asOf, Instant asOfInstant, boolean dryRun) {
        List<PromoterHierarchyOverride> net = overrideRepository.findPaidForPeriod(netStart, asOf);
        Map<BeneficiaryCategory, List<PromoterHierarchyOverride>> grouped = net.stream()
                .collect(Collectors.groupingBy(o -> new BeneficiaryCategory(o.getPromoter().getId(), o.getCategory())));

        List<TopUpOutcome> outcomes = new ArrayList<>();
        for (List<PromoterHierarchyOverride> rows : grouped.values()) {
            Promoter beneficiary = rows.get(0).getPromoter();
            OverrideCategory category = rows.get(0).getCategory();
            if (beneficiary.getRank() == null) {
                log.warn("Retroactive top-up cut skipped for {}: no rank set", beneficiary.getReferralCode());
                continue;
            }

            // Same "first candidate carries the frequency axis" assumption as
            // cutDirectInscription, scoped to (rank, category).
            List<HierarchyOverrideTier> ruleCandidates =
                    hierarchyOverrideTierRepository.findActiveApplicable(beneficiary.getRank().getId(), category);
            if (ruleCandidates.isEmpty()) {
                log.warn("Retroactive top-up cut skipped for {} ({}): no applicable tier",
                        beneficiary.getReferralCode(), category);
                continue;
            }
            HierarchyOverrideTier rule = ruleCandidates.get(0);

            CutWindow cut = resolveCutWindow(rule.getAccrualPeriodStrategy().name(), rule.getAccrualPeriodAnchor(),
                    rule.getRetroactiveSettlementPeriodStrategy().name(), rule.getRetroactiveSettlementPeriodAnchor(), asOf);
            if (cut == null) {
                log.warn("Retroactive top-up cut skipped for {} ({}): asOf {} outside every cut",
                        beneficiary.getReferralCode(), category, asOf);
                continue;
            }

            Set<Long> team = hierarchyService.resolveTeamMemberIds(beneficiary.getId(), asOfInstant);
            long count = team.isEmpty() ? 0 : (category == OverrideCategory.INSCRIPTION
                    ? memberRepository.countNewSubscribersForPromoters(team, cut.accrualWindow().start(), cut.cutEnd())
                    : commissionRepository.countByPromotersAppliesToInPeriod(team, AppliesTo.MONTHLY, cut.accrualWindow().start(), cut.cutEnd()));

            HierarchyOverrideTier target = highestQualifyingOverrideTier(beneficiary, category, count);
            if (target == null) {
                log.warn("Retroactive top-up cut skipped for {} ({}): no qualifying tier",
                        beneficiary.getReferralCode(), category);
                continue;
            }

            List<PromoterHierarchyOverride> paidInWindow = overrideRepository.findPaidForPromoterCategoryInPeriod(
                    beneficiary.getId(), category, cut.accrualWindow().start(), cut.cutEnd());
            BigDecimal basisCumulative = sumOverrideBasis(paidInWindow);
            BigDecimal alreadyPaidBase = sumOverrideAmount(paidInWindow);
            Currency currency = resolveCurrency(paidInWindow.isEmpty() ? null : paidInWindow.get(0).getCurrency(),
                    target.getFlatAmountCurrency());
            if (currency == null) {
                log.warn("Retroactive top-up cut skipped for {} ({}): no resolvable currency",
                        beneficiary.getReferralCode(), category);
                continue;
            }

            LedgerType ledgerType = category == OverrideCategory.INSCRIPTION
                    ? LedgerType.HIERARCHY_OVERRIDE_INSCRIPTION : LedgerType.HIERARCHY_OVERRIDE_COLLECTION;

            BigDecimal targetAmountCumulative = recompute(target.getOverridePct(), target.getFlatAmount(), basisCumulative);
            BigDecimal alreadyPaidRetro = cutRepository.sumPaidRetroBeforeSequence(
                    beneficiary.getId(), ledgerType, cut.accrualWindow().start(), cut.accrualWindow().end(), cut.sequence());
            BigDecimal alreadyPaid = alreadyPaidBase.add(alreadyPaidRetro);
            BigDecimal retro = targetAmountCumulative.subtract(alreadyPaid);
            if (retro.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!dryRun) {
                upsertCut(beneficiary, ledgerType, cut, basisCumulative, targetAmountCumulative,
                        alreadyPaid, retro, currency, target.getId(), target.getName());
            }
            outcomes.add(new TopUpOutcome(beneficiary.getUuid(), beneficiary.getReferralCode(), beneficiary.getDisplayName(),
                    ledgerType.name(), basisCumulative, targetAmountCumulative, alreadyPaid, retro,
                    target.getName(), cut.sequence(), cut.accrualWindow().start(), cut.accrualWindow().end(),
                    cut.cutStart(), cut.cutEnd()));
        }
        return outcomes;
    }

    // ─── Cut-mode shared helpers ────────────────────────────────────────────

    /** The resolved accrual window + the one retroactive cut (and its 1-based sequence) containing {@code asOf}. */
    private record CutWindow(PeriodStrategies.Window accrualWindow, int sequence, PeriodCutCalculator.Cut cut) {
        LocalDate cutStart() { return cut.start(); }
        LocalDate cutEnd() { return cut.end(); }
    }

    /**
     * Resolves the accrual window containing {@code asOf} and, within it,
     * the retroactive-settlement cut that contains {@code asOf}. When {@code
     * asOf} doesn't fall inside any cut (e.g. it is after the accrual
     * window's own end — shouldn't normally happen since the accrual window
     * itself is the one containing {@code asOf}, but guarded defensively),
     * falls back to the last cut whose end is {@code <= asOf}; returns
     * {@code null} when neither exists (nothing to compute yet).
     */
    private CutWindow resolveCutWindow(String accrualStrategy, Short accrualAnchor,
                                        String retroStrategy, Short retroAnchor, LocalDate asOf) {
        PeriodStrategies.Window accrualWindow = PeriodStrategies.window(accrualStrategy, asOf, accrualAnchor);
        List<PeriodCutCalculator.Cut> retroCuts = PeriodCutCalculator.cuts(
                retroStrategy, accrualWindow.start(), accrualWindow.end(), retroAnchor);

        for (int i = 0; i < retroCuts.size(); i++) {
            PeriodCutCalculator.Cut c = retroCuts.get(i);
            if (!asOf.isBefore(c.start()) && !asOf.isAfter(c.end())) {
                return new CutWindow(accrualWindow, i + 1, c);
            }
        }
        for (int i = retroCuts.size() - 1; i >= 0; i--) {
            PeriodCutCalculator.Cut c = retroCuts.get(i);
            if (!c.end().isAfter(asOf)) {
                return new CutWindow(accrualWindow, i + 1, c);
            }
        }
        return null;
    }

    private static Set<Promoter> distinctPromoters(List<Commission> rows) {
        Map<Long, Promoter> byId = new java.util.LinkedHashMap<>();
        for (Commission c : rows) {
            byId.putIfAbsent(c.getPromoter().getId(), c.getPromoter());
        }
        return new java.util.LinkedHashSet<>(byId.values());
    }

    /** First non-null of {@code fromPaidRows}/{@code fallback} — currency degrades from "observed" to "tier-configured". */
    private static Currency resolveCurrency(Currency fromPaidRows, Currency fallback) {
        return fromPaidRows != null ? fromPaidRows : fallback;
    }

    /**
     * Upserts a retroactive cut row by the V150 unique key ({@code promoter,
     * ledgerType, accrualPeriodStart, accrualPeriodEnd, cutSequence}) — same
     * PAID guardrail as {@link #upsert}: a cut already disbursed is a
     * settled, out-of-band payout that a recompute never silently overwrites.
     */
    private void upsertCut(Promoter promoter, LedgerType ledgerType, CutWindow cut,
                            BigDecimal basisCumulative, BigDecimal targetAmountCumulative,
                            BigDecimal alreadyPaid, BigDecimal retro, Currency currency,
                            Long tierId, String tierName) {
        CommissionRetroactiveTopUpCut row = cutRepository
                .findByPromoterIdAndLedgerTypeAndAccrualPeriodStartAndAccrualPeriodEndAndCutSequence(
                        promoter.getId(), ledgerType, cut.accrualWindow().start(), cut.accrualWindow().end(), cut.sequence())
                .orElseGet(CommissionRetroactiveTopUpCut::new);
        if (CommissionRetroactiveTopUpCut.CutStatus.PAID.name().equals(row.getStatus())) {
            log.warn("Retroactive top-up cut recompute skipped for promoter {} ({}, cut {} of {}..{}): already PAID",
                    promoter.getReferralCode(), ledgerType, cut.sequence(), cut.accrualWindow().start(), cut.accrualWindow().end());
            return;
        }
        row.setPromoter(promoter);
        row.setLedgerType(ledgerType);
        row.setAccrualPeriodStart(cut.accrualWindow().start());
        row.setAccrualPeriodEnd(cut.accrualWindow().end());
        row.setCutSequence(cut.sequence());
        row.setCutStart(cut.cutStart());
        row.setCutEnd(cut.cutEnd());
        row.setBasisAmountCumulative(basisCumulative);
        row.setTargetAmountCumulative(targetAmountCumulative);
        row.setAlreadyPaidAmount(alreadyPaid);
        row.setRetroAmount(retro);
        row.setCurrency(currency);
        row.setTierId(tierId);
        row.setTierNameSnapshot(tierName);
        row.setStatus(CommissionRetroactiveTopUpCut.CutStatus.PENDING.name());
        cutRepository.save(row);
    }
}
