package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse.TopUpOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.LedgerType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
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
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    private final PromoterHierarchyService hierarchyService;
    private final MemberRepository memberRepository;
    private final CommissionRetroactiveTopUpRepository topUpRepository;

    @Transactional
    public CommissionRetroactiveTopUpResponse execute(CommissionRetroactiveTopUpRequest request) {
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
}
