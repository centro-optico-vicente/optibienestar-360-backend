package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodCutCalculator;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride.OverrideStatus;
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
import java.util.List;
import java.util.Set;

/**
 * Per-cut settlement engine for {@link PromoterHierarchyOverride}s — the
 * hierarchy-override analogue of {@link CommissionPeriodicSettlementService}
 * (hub plan ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3).
 *
 * <p>Overrides carry no approval state of their own (only the direct {@code
 * Commission} does — see {@link CommissionStatus} Javadoc), so
 * this service settles {@code PENDING} overrides whose root commission is
 * {@code APPROVED}, mirroring {@code CommissionPayoutService
 * #isRootCommissionApproved} exactly (small intentional duplication — that
 * method is {@code private} on a class this service otherwise has no reason
 * to depend on; extracting a shared helper is a one-line follow-up if a
 * third caller ever needs it).</p>
 *
 * <p>Same relationship to {@link HierarchyOverrideReRatingService} (still
 * owns PENDING re-rating before the root commission is approved — orthogonal
 * here since we only ever touch rows whose root already IS approved) and to
 * {@link CommissionRetroactiveTopUpService} (still owns the month-close
 * top-up for whatever this settled as PAID) as the commission-side sibling
 * has to its own neighbors.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HierarchyOverridePeriodicSettlementService {

    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final HierarchyOverrideTierRepository tierRepository;
    private final CommissionRepository commissionRepository;
    private final MemberRepository memberRepository;
    private final PromoterHierarchyService hierarchyService;

    /** Outcome of settling one beneficiary's cut for a given category. */
    public record SettlementOutcome(
            LocalDate settlementStart, LocalDate settlementEnd,
            LocalDate cutStart, LocalDate cutEnd,
            long accumulatedCount, String targetTierName,
            int overridesPaid, BigDecimal totalPaid) {
    }

    /**
     * Settles the cut of {@code rule} that contains {@code asOf} for {@code
     * beneficiary}: re-prices every root-approved {@code PENDING} override
     * of {@code rule.getCategory()} falling inside the cut to the band the
     * beneficiary's team volume qualifies for as of the cut's end, then
     * marks them {@code PAID}.
     *
     * @param rule          the hierarchy-override-tier rule whose period
     *                      config drives the cut calculation; {@code
     *                      rule.getRank()}/{@code rule.getCategory()} scope
     *                      which overrides are in play (band selection still
     *                      iterates every active tier for that (rank,
     *                      category), same as {@link
     *                      CommissionRetroactiveTopUpService
     *                      #highestQualifyingOverrideTier}).
     */
    @Transactional
    public SettlementOutcome settleCut(Promoter beneficiary, HierarchyOverrideTier rule, LocalDate asOf,
                                       String payoutReference, boolean dryRun) {
        PeriodStrategies.Window settlementWindow = PeriodStrategies.window(
                rule.getFinalSettlementPeriodStrategy().name(), asOf, rule.getFinalSettlementPeriodAnchor());
        PeriodCutCalculator.Cut cut = PeriodCutCalculator.cutContaining(
                rule.getPartialSettlementPeriodStrategy().name(), settlementWindow.start(), settlementWindow.end(), asOf,
                rule.getPartialSettlementPeriodAnchor());

        Instant cutEndAsOf = cut.end().atStartOfDay(AppTimeZone.ZONE).toInstant();
        Set<Long> team = hierarchyService.resolveTeamMemberIds(beneficiary.getId(), cutEndAsOf);
        OverrideCategory category = rule.getCategory();
        long accumulatedCount = team.isEmpty() ? 0 : (category == OverrideCategory.INSCRIPTION
                ? memberRepository.countNewSubscribersForPromoters(team, settlementWindow.start(), cut.end())
                : commissionRepository.countByPromotersAppliesToInPeriod(team, AppliesTo.MONTHLY, settlementWindow.start(), cut.end()));

        HierarchyOverrideTier target = highestQualifyingOverrideTier(beneficiary, category, accumulatedCount);
        if (target == null) {
            log.warn("Periodic settlement skipped for {} ({}, cut {}..{}): no applicable tier",
                    beneficiary.getReferralCode(), category, cut.start(), cut.end());
            return new SettlementOutcome(settlementWindow.start(), settlementWindow.end(),
                    cut.start(), cut.end(), accumulatedCount, null, 0, BigDecimal.ZERO);
        }

        List<PromoterHierarchyOverride> cutOverrides = overrideRepository
                .findPendingForPromoterCategoryInPeriod(beneficiary.getId(), category, cut.start(), cut.end())
                .stream()
                .filter(this::isRootCommissionApproved)
                .toList();

        BigDecimal totalPaid = BigDecimal.ZERO;
        Instant now = Instant.now();
        for (PromoterHierarchyOverride o : cutOverrides) {
            BigDecimal newAmount = recompute(target.getOverridePct(), target.getFlatAmount(), o.getBasisAmount());
            totalPaid = totalPaid.add(newAmount);
            if (dryRun) {
                continue;
            }
            o.setTier(target);
            o.setAmount(newAmount);
            o.setStatus(OverrideStatus.PAID.name());
            o.setPaidAt(now);
            o.setPayoutReference(payoutReference);
        }

        log.info("HIERARCHY_OVERRIDE_PERIODIC_SETTLEMENT beneficiary={} category={} settlement={}..{} cut={}..{} "
                        + "dryRun={} accumulatedCount={} targetTier={} overridesPaid={} totalPaid={}",
                beneficiary.getReferralCode(), category, settlementWindow.start(), settlementWindow.end(),
                cut.start(), cut.end(), dryRun, accumulatedCount, target.getName(), cutOverrides.size(), totalPaid);

        return new SettlementOutcome(settlementWindow.start(), settlementWindow.end(),
                cut.start(), cut.end(), accumulatedCount, target.getName(), cutOverrides.size(), totalPaid);
    }

    private HierarchyOverrideTier highestQualifyingOverrideTier(Promoter beneficiary, OverrideCategory category, long count) {
        if (beneficiary.getRank() == null) {
            return null;
        }
        List<HierarchyOverrideTier> candidates =
                tierRepository.findActiveApplicable(beneficiary.getRank().getId(), category);
        for (HierarchyOverrideTier tier : candidates) {
            if (tier.getThresholdCount() <= 0 || count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Mirrors {@code CommissionPayoutService#isRootCommissionApproved}
     * exactly — walks {@code sourceOverride} up to the root {@code
     * sourceCommission} and returns whether that root is {@code APPROVED}.
     */
    private boolean isRootCommissionApproved(PromoterHierarchyOverride override) {
        PromoterHierarchyOverride current = override;
        while (current.getSourceCommission() == null) {
            if (current.getSourceOverride() == null) {
                log.warn("Hierarchy override {} has neither source_commission nor source_override", current.getUuid());
                return false;
            }
            current = current.getSourceOverride();
        }
        return CommissionStatus.APPROVED.name().equals(current.getSourceCommission().getStatus());
    }

    private static BigDecimal recompute(BigDecimal pct, BigDecimal flatAmount, BigDecimal basis) {
        return pct != null
                ? basis.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : flatAmount;
    }
}
