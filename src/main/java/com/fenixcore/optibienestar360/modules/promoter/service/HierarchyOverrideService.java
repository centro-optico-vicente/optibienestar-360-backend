package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.BasisType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The hierarchy-override cascade engine (V102, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2). Hooked into
 * {@code PaymentsService.attributeCommission} right after {@code
 * CommissionService.calculateAndPersistFor} — same best-effort semantics
 * (a failure here must never roll back the payment approval).
 *
 * <p><b>Cascade shape</b>: strictly one level at a time. {@link
 * #cascadeFrom(Commission)} computes the level-2 override (the earner's
 * immediate supervisor) from the {@link Commission} itself; it then
 * recurses <b>on the override just persisted</b> — never on the original
 * commission — so a level-3+ override (Coordinador and beyond) is always
 * funded by what the level right below just earned, exactly as confirmed by
 * the whiteboard numeric example (basis = the inferior's own commission/
 * override amount, never the gross sale/payment amount).</p>
 *
 * <p>The chain stops automatically the moment {@code
 * PromoterHierarchyService.resolveSupervisorAt} returns {@code null} — no
 * hardcoded level count.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HierarchyOverrideService {

    private final PromoterHierarchyService hierarchyService;
    private final HierarchyOverrideTierRepository tierRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final MemberRepository memberRepository;
    private final CommissionRepository commissionRepository;
    private final PaymentRepository paymentRepository;
    private final CurrencyConversionService currencyConversionService;

    @Transactional
    public void cascadeFrom(Commission commission) {
        if (commission == null) {
            return;
        }
        Promoter earner = commission.getPromoter();
        if (!generatesHierarchyOverride(earner)) {
            log.debug("Hierarchy override cascade skipped: promoter {} type does not generate overrides",
                    earner.getReferralCode());
            return;
        }
        OverrideCategory category = toCategory(commission.getAppliesTo());
        cascadeOnce(earner, commission.getAmount(), category,
                commission, null, commission.getCurrency(), commission.getEarnedAt());
    }

    /**
     * V103 — checked once, at the entry point, against the promoter who
     * actually made the sale (never re-checked per level in {@link
     * #cascadeOnce}): the still-unconfirmed business question (hub notes
     * pregunta 7) is "does an Independiente's own sale generate an override
     * for others", not "can an Independiente-classified Supervisor/Coordinador
     * receive one from a subordinate's sale" — those are different
     * questions, and only the first has a flag today. {@code null}
     * promoterType (legacy/unclassified rows) defaults to generating
     * overrides — same as the flag's own column default.
     */
    private static boolean generatesHierarchyOverride(Promoter earner) {
        return earner.getPromoterType() == null || earner.getPromoterType().isGeneratesHierarchyOverride();
    }

    private void cascadeOnce(Promoter earner, BigDecimal basisAmount, OverrideCategory category,
                              Commission sourceCommission, PromoterHierarchyOverride sourceOverride,
                              Currency currency, Instant asOf) {
        Promoter supervisor = hierarchyService.resolveSupervisorAt(earner.getId(), asOf);
        if (supervisor == null) {
            return; // top of the chain — cascade stops here automatically
        }

        HierarchyOverrideTier tier = selectTier(supervisor, category, asOf);
        if (tier == null) {
            log.debug("Hierarchy override skipped: no applicable tier for rank={} category={}",
                    supervisor.getRank() != null ? supervisor.getRank().getCode() : null, category);
            return;
        }

        BigDecimal amount = tier.getOverridePct() != null
                ? basisAmount.multiply(tier.getOverridePct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : tier.getFlatAmount();

        PromoterHierarchyOverride override = new PromoterHierarchyOverride();
        override.setPromoter(supervisor);
        override.setSourceCommission(sourceCommission);
        override.setSourceOverride(sourceOverride);
        override.setCategory(category);
        override.setBasisAmount(basisAmount);
        override.setTier(tier);
        override.setAmount(amount);
        override.setCurrency(currency);
        override.setPeriodStrategy(tier.getAccrualPeriodStrategy());

        LocalDate anchor = asOf.atZone(AppTimeZone.ZONE).toLocalDate();
        PeriodStrategies.Window window =
                PeriodStrategies.window(tier.getAccrualPeriodStrategy().name(), anchor, tier.getAccrualPeriodAnchor());
        override.setPeriodStart(window.start());
        override.setPeriodEnd(window.end());
        override.setEarnedAt(asOf);
        override.setStatus(OverrideStatus.PENDING.name());

        PromoterHierarchyOverride saved = overrideRepository.save(override);
        log.info("Hierarchy override persisted: beneficiary={} category={} amount={} {} tier={}",
                supervisor.getReferralCode(), category, saved.getAmount(), saved.getCurrency().getCode(),
                saved.getTier().getName());

        // Recurse on the override just earned (not the original commission) —
        // the cascade is strictly one level at a time.
        cascadeOnce(supervisor, saved.getAmount(), category, null, saved, currency, asOf);
    }

    /**
     * Highest band {@code supervisor}'s rank qualifies for in {@code
     * category}, by their <b>team's</b> volume (never their own). Candidates
     * arrive highest-threshold-first; a base band (threshold 0) is the
     * guaranteed fallback when one is configured — same selection shape as
     * {@code CommissionService.selectTier}.
     *
     * <p>{@code basis=AMOUNT} bands (Fase A, phase 2) are keyed on the
     * team's collected-amount volume instead — summed from every team
     * member's APPROVED {@code direction=IN} payments in the band's accrual
     * window and converted into the band's own {@link
     * HierarchyOverrideTier#getThresholdAmountCurrency()}, same
     * degrade-gracefully currency policy {@code CommissionService.selectTier}
     * uses for {@code CommissionTier}.</p>
     */
    private HierarchyOverrideTier selectTier(Promoter supervisor, OverrideCategory category, Instant asOf) {
        if (supervisor.getRank() == null) {
            return null;
        }
        List<HierarchyOverrideTier> candidates =
                tierRepository.findActiveApplicable(supervisor.getRank().getId(), category);
        if (candidates.isEmpty()) {
            return null;
        }

        LocalDate anchor = asOf.atZone(AppTimeZone.ZONE).toLocalDate();
        Set<Long> team = hierarchyService.resolveTeamMemberIds(supervisor.getId(), asOf);
        Map<String, Long> countByStrategy = new HashMap<>();

        for (HierarchyOverrideTier tier : candidates) {
            if (tier.getBasis() == BasisType.AMOUNT) {
                if (tier.getThresholdAmount() == null || tier.getThresholdAmount().signum() <= 0) {
                    return tier;   // base band — always qualifies
                }
                if (team.isEmpty()) {
                    continue; // no team yet — only a threshold-0 band (handled above) can qualify
                }
                BigDecimal convertedCollected = teamCollectedAmountInTierCurrency(team, anchor, tier);
                if (convertedCollected.compareTo(tier.getThresholdAmount()) >= 0) {
                    return tier;
                }
                continue;
            }
            if (tier.getThresholdCount() <= 0) {
                return tier;
            }
            if (team.isEmpty()) {
                continue; // no team yet — only a threshold-0 band (handled above) can qualify
            }
            long count = countByStrategy.computeIfAbsent(
                    tier.getAccrualPeriodStrategy().name() + "#" + tier.getAccrualPeriodAnchor(), strategy -> {
                PeriodStrategies.Window window =
                        PeriodStrategies.window(tier.getAccrualPeriodStrategy().name(), anchor, tier.getAccrualPeriodAnchor());
                return category == OverrideCategory.INSCRIPTION
                        ? memberRepository.countNewSubscribersForPromoters(team, window.start(), window.end())
                        : commissionRepository.countByPromotersAppliesToInPeriod(
                                team, Commission.AppliesTo.MONTHLY, window.start(), window.end());
            });
            if (count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Sum of the whole team's APPROVED {@code direction=IN} payments in
     * {@code tier}'s accrual window, converted payment-by-payment into
     * {@code tier.getThresholdAmountCurrency()} — same per-payment
     * degrade-gracefully policy as {@code
     * CommissionService#collectedAmountInTierCurrency}: a payment whose
     * currency pair has no exchange rate available is excluded and logged,
     * never blocking the rest of the evaluation.
     */
    private BigDecimal teamCollectedAmountInTierCurrency(Set<Long> team, LocalDate anchor, HierarchyOverrideTier tier) {
        PeriodStrategies.Window w =
                PeriodStrategies.window(tier.getAccrualPeriodStrategy().name(), anchor, tier.getAccrualPeriodAnchor());
        Instant from = w.start().atStartOfDay(AppTimeZone.ZONE).toInstant();
        Instant to = w.end().plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();
        List<Payment> payments = paymentRepository.findApprovedInForPromotersInWindow(team, from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : payments) {
            try {
                var conversion = currencyConversionService.convert(
                        p.getAmount(), p.getCurrency(), tier.getThresholdAmountCurrency(), p.getPaymentDate());
                total = total.add(conversion.convertedAmount());
            } catch (NoExchangeRateAvailableException ex) {
                log.warn("Hierarchy override tier {} — no exchange rate {}→{} for payment {}; excluded from AMOUNT threshold check",
                        tier.getUuid(), p.getCurrency().getCode(), tier.getThresholdAmountCurrency().getCode(), p.getUuid());
            }
        }
        return total;
    }

    private static OverrideCategory toCategory(Commission.AppliesTo appliesTo) {
        return appliesTo == Commission.AppliesTo.INSCRIPTION ? OverrideCategory.INSCRIPTION : OverrideCategory.COLLECTION;
    }
}
