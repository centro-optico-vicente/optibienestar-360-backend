package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
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
import java.util.Optional;

/**
 * Commission calculation + persistence engine. Singular service name — it owns
 * the one operation "given an approved payment, attribute a commission". Admin
 * CRUD over the resulting rows lives in {@code CommissionsService}.
 *
 * <p><b>Tier-driven calculation (v2 PDF #5, V42)</b>: the promoter's rate comes
 * from {@code commission_tiers} (config), replacing the v1 hardcoded plan-type
 * switch. Candidate tiers are those matching the payment's plan type (or
 * unscoped) and fee type (or BOTH); the engine applies the highest tier whose
 * {@code threshold_count} the promoter meets by their new-subscriber count in
 * the tier's period. The v1 per-plan rates are seeded as base tiers (threshold 0)
 * so the cutover is behavior-preserving. The tier values are snapshotted inline
 * on the {@link Commission} row so later tier edits never rewrite history.</p>
 *
 * <p><b>Promoter resolution</b>: walks {@code payment.membership.member.promoter};
 * falls back to the seeded INSTITUCION row when the member has no active promoter
 * (PDF #5 default attribution).</p>
 *
 * <p><b>Idempotency</b>: pre-checks the V26 partial UNIQUE
 * {@code (payment_id, promoter_id) WHERE status <> 'VOIDED'} for a clean skip.
 * <b>Failure policy</b>: best-effort — a failure here must not roll back the
 * payment approval (the caller wraps this in try/catch).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    public static final String FALLBACK_PROMOTER_CODE = "INSTITUCION";

    private final CommissionRepository commissionRepository;
    private final CommissionTierRepository tierRepository;
    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;

    /**
     * Computes + persists a commission row for the given approved payment.
     * Returns {@link Optional#empty()} when no row was created (already exists,
     * no promoter resolvable, or no applicable tier configured).
     */
    @Transactional
    public Optional<Commission> calculateAndPersistFor(Payment payment) {
        if (payment == null || payment.getMembership() == null) {
            log.warn("Commission skipped: payment or membership null");
            return Optional.empty();
        }

        Member member = payment.getMembership().getMember();
        if (member == null) {
            log.warn("Commission skipped: payment {} has no member resolvable", payment.getUuid());
            return Optional.empty();
        }

        Optional<Promoter> promoterOpt = resolvePromoter(member);
        if (promoterOpt.isEmpty()) {
            log.error("Commission skipped: no promoter (real or INSTITUCION) for member {} — "
                    + "INSTITUCION seed likely missing or soft-deleted in this environment", member.getUuid());
            return Optional.empty();
        }
        Promoter promoter = promoterOpt.get();

        if (commissionRepository.existsActiveForPaymentAndPromoter(payment.getId(), promoter.getId())) {
            log.debug("Commission already exists for payment {} promoter {} — skipping",
                    payment.getUuid(), promoter.getReferralCode());
            return Optional.empty();
        }

        Plan plan = payment.getMembership().getPlan();
        PlanType planType = plan != null ? plan.getType() : null;
        if (planType == null) {
            log.warn("Commission skipped: payment {} membership has no plan type", payment.getUuid());
            return Optional.empty();
        }

        AppliesTo appliesTo = payment.isInscription() ? AppliesTo.INSCRIPTION : AppliesTo.MONTHLY;
        LocalDate anchor = resolvePeriodAnchor(payment);

        CommissionTier tier = selectTier(promoter, planType, appliesTo, anchor);
        if (tier == null) {
            log.warn("Commission skipped: no applicable commission tier for payment {} (plan {} / {})",
                    payment.getUuid(), planType, appliesTo);
            return Optional.empty();
        }

        PeriodStrategies.Window window = PeriodStrategies.window(tier.getPeriodStrategy().name(), anchor);
        BigDecimal basis = payment.getAmount();
        BigDecimal pct = tier.getCommissionPct();
        BigDecimal flat = tier.getFlatAmount();
        BigDecimal amount = pct != null
                ? basis.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : flat;

        Commission commission = new Commission();
        commission.setPromoter(promoter);
        commission.setPayment(payment);
        commission.setMember(member);
        commission.setAmount(amount);
        commission.setCurrency(payment.getCurrency());
        commission.setCalculationBasis(basis);
        commission.setCommissionPct(pct);
        commission.setFlatAmount(flat);
        commission.setCommissionTierId(tier.getId());
        commission.setTierNameSnapshot(tier.getName());
        commission.setAppliesTo(appliesTo);
        commission.setPeriodStrategy(tier.getPeriodStrategy());
        commission.setPeriodStart(window.start());
        commission.setPeriodEnd(window.end());
        commission.setEarnedAt(payment.getReviewedAt() != null ? payment.getReviewedAt() : Instant.now());
        commission.setStatus(CommissionStatus.PENDING.name());

        Commission saved = commissionRepository.save(commission);
        log.info("Commission persisted: payment={} promoter={} amount={} {} tier={}",
                payment.getUuid(), promoter.getReferralCode(), saved.getAmount(), saved.getCurrency(),
                saved.getTierNameSnapshot());
        return Optional.of(saved);
    }

    // ─── Tier selection ───────────────────────────────────────────────────────

    /**
     * Highest tier the promoter qualifies for among those applicable to the
     * payment. Candidates arrive highest-threshold first; a base tier
     * (threshold 0) is the guaranteed fallback. New-subscriber counts are
     * computed once per distinct period strategy.
     */
    private CommissionTier selectTier(Promoter promoter, PlanType planType, AppliesTo appliesTo, LocalDate anchor) {
        CommissionTier.AppliesTo tierApplies = appliesTo == AppliesTo.INSCRIPTION
                ? CommissionTier.AppliesTo.INSCRIPTION
                : CommissionTier.AppliesTo.MONTHLY;
        List<CommissionTier> candidates =
                tierRepository.findActiveApplicable(planType, tierApplies, CommissionTier.AppliesTo.BOTH);

        Map<String, Long> countByStrategy = new HashMap<>();
        for (CommissionTier tier : candidates) {
            if (tier.getThresholdCount() <= 0) {
                return tier;   // base tier — always qualifies (ordered last among candidates)
            }
            long count = countByStrategy.computeIfAbsent(tier.getPeriodStrategy().name(), s -> {
                PeriodStrategies.Window w = PeriodStrategies.window(s, anchor);
                return memberRepository.countNewSubscribersForPromoter(promoter.getId(), w.start(), w.end());
            });
            if (count >= tier.getThresholdCount()) {
                return tier;
            }
        }
        return null;
    }

    private Optional<Promoter> resolvePromoter(Member member) {
        Promoter direct = member.getPromoter();
        if (direct != null && direct.isActive()) {
            return Optional.of(direct);
        }
        return promoterRepository.findByReferralCode(FALLBACK_PROMOTER_CODE).filter(Promoter::isActive);
    }

    /**
     * Calendar date the commission is attributed to. Inscription payments anchor
     * on the payment date; recurring payments anchor on the {@code applied_period}
     * (billing month) when present, else the payment date.
     */
    private static LocalDate resolvePeriodAnchor(Payment payment) {
        if (payment.isInscription()) {
            return payment.getPaymentDate();
        }
        return payment.getAppliedPeriod() != null ? payment.getAppliedPeriod() : payment.getPaymentDate();
    }
}
