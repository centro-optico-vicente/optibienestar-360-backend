package com.fenixcore.optisaludplus.modules.promoter.service;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optisaludplus.modules.payment.entity.Payment;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter;
import com.fenixcore.optisaludplus.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optisaludplus.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Commission calculation + persistence engine. Singular service name
 * (not {@code CommissionsService}) because it owns the one operation
 * "given an approved payment, attribute a commission" — admin CRUD over
 * the resulting rows lives in a separate {@code CommissionsService}
 * when that bullet lands.
 *
 * <p><b>v1 calculation table</b> (hardcoded defaults — v2
 * {@code commission_tiers} replaces this with DB-driven config; the
 * snapshot fields on the Commission row stay identical so existing
 * historical rows keep their inline calc intact across the cutover):</p>
 *
 * <table>
 *   <tr><th>Plan type</th><th>Rate</th><th>Tier name snapshot</th></tr>
 *   <tr><td>INDIVIDUAL</td><td>20% pct</td><td>v1-individual-20pct</td></tr>
 *   <tr><td>FAMILIAR</td><td>25% pct</td><td>v1-familiar-25pct</td></tr>
 *   <tr><td>CORPORATIVO</td><td>$5 flat</td><td>v1-corporativo-5flat</td></tr>
 * </table>
 *
 * <p><b>Promoter resolution</b>: walks {@code payment.membership.member.promoter}.
 * When the member has no promoter assigned (legacy back-fill rows), falls
 * back to the seeded INSTITUCION row from V25 — every enrollment
 * attributes to a promoter, the institution wears the hat when no human
 * does (PDF #5 default attribution).</p>
 *
 * <p><b>Idempotency</b>: the V26 partial UNIQUE on
 * {@code (payment_id, promoter_id) WHERE status <> 'VOIDED'} forbids a
 * second active commission for the same payment+promoter. The service
 * pre-checks via {@code existsActiveForPaymentAndPromoter} for a clean
 * skip instead of a constraint-violation roundtrip when {@code approve()}
 * is invoked twice (re-attempt after partial failure).</p>
 *
 * <p><b>Failure policy</b>: failure to attribute a commission must NOT
 * roll back a payment approval. The caller wraps {@code calculateAndPersistFor}
 * in a try/catch and treats it as best-effort — admin tooling can
 * re-attribute manually if necessary. Same policy as the email-dispatch
 * helper in {@code PaymentsService}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    public static final String FALLBACK_PROMOTER_CODE = "INSTITUCION";

    private final CommissionRepository commissionRepository;
    private final PromoterRepository promoterRepository;

    /**
     * Computes + persists a commission row for the given approved payment.
     * Returns {@link Optional#empty()} when no row was created (already
     * exists, or no promoter could be resolved at all — which only
     * happens if the INSTITUCION seed is missing in this environment).
     */
    @Transactional
    public Optional<Commission> calculateAndPersistFor(Payment payment) {
        if (payment == null || payment.getMembership() == null) {
            log.warn("Commission skipped: payment or membership null");
            return Optional.empty();
        }

        Member member = payment.getMembership().getMember();
        if (member == null) {
            log.warn("Commission skipped: payment {} has no member resolvable",
                    payment.getUuid());
            return Optional.empty();
        }

        Optional<Promoter> promoterOpt = resolvePromoter(member);
        if (promoterOpt.isEmpty()) {
            log.error("Commission skipped: no promoter (real or INSTITUCION) for member {} — "
                    + "INSTITUCION seed likely missing or soft-deleted in this environment",
                    member.getUuid());
            return Optional.empty();
        }
        Promoter promoter = promoterOpt.get();

        if (commissionRepository.existsActiveForPaymentAndPromoter(
                payment.getId(), promoter.getId())) {
            log.debug("Commission already exists for payment {} promoter {} — skipping",
                    payment.getUuid(), promoter.getReferralCode());
            return Optional.empty();
        }

        Plan plan = payment.getMembership().getPlan();
        PlanType planType = plan != null ? plan.getType() : null;
        if (planType == null) {
            log.warn("Commission skipped: payment {} membership has no plan type",
                    payment.getUuid());
            return Optional.empty();
        }

        CalcResult calc = computeForPlan(planType, payment.getAmount());

        Commission commission = new Commission();
        commission.setPromoter(promoter);
        commission.setPayment(payment);
        commission.setMember(member);
        commission.setAmount(calc.amount);
        commission.setCurrency(payment.getCurrency());
        commission.setCalculationBasis(payment.getAmount());
        commission.setCommissionPct(calc.pct);
        commission.setFlatAmount(calc.flat);
        commission.setTierNameSnapshot(calc.tierName);
        commission.setAppliesTo(payment.isInscription() ? AppliesTo.INSCRIPTION : AppliesTo.MONTHLY);
        commission.setPeriodStrategy(PeriodStrategy.MONTHLY);

        LocalDate anchor = resolvePeriodAnchor(payment);
        commission.setPeriodStart(anchor.withDayOfMonth(1));
        commission.setPeriodEnd(anchor.with(TemporalAdjusters.lastDayOfMonth()));

        commission.setEarnedAt(payment.getReviewedAt() != null
                ? payment.getReviewedAt()
                : Instant.now());
        commission.setStatus(CommissionStatus.PENDING.name());

        Commission saved = commissionRepository.save(commission);
        log.info("Commission persisted: payment={} promoter={} amount={} {} tier={}",
                payment.getUuid(), promoter.getReferralCode(),
                saved.getAmount(), saved.getCurrency(), saved.getTierNameSnapshot());
        return Optional.of(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Optional<Promoter> resolvePromoter(Member member) {
        Promoter direct = member.getPromoter();
        if (direct != null && direct.isActive()) {
            return Optional.of(direct);
        }
        // Fallback to INSTITUCION system row (V25 seed). Member with no
        // promoter attribution attributes to administración central per
        // v2 PDF #5.
        return promoterRepository.findByReferralCode(FALLBACK_PROMOTER_CODE)
                .filter(Promoter::isActive);
    }

    /**
     * Plan-type → calculation rule. Hardcoded defaults for v1; replaced by
     * {@code commission_tiers} lookup in v2 (the snapshot fields on the
     * Commission row stay identical so the cutover doesn't break
     * historical reporting).
     */
    private static CalcResult computeForPlan(PlanType planType, BigDecimal basis) {
        return switch (planType) {
            case INDIVIDUAL -> percentCalc(basis, new BigDecimal("20.00"), "v1-individual-20pct");
            case FAMILIAR   -> percentCalc(basis, new BigDecimal("25.00"), "v1-familiar-25pct");
            case CORPORATIVO -> flatCalc(new BigDecimal("5.00"), "v1-corporativo-5flat");
        };
    }

    private static CalcResult percentCalc(BigDecimal basis, BigDecimal pct, String tierName) {
        BigDecimal amount = basis
                .multiply(pct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new CalcResult(amount, pct, null, tierName);
    }

    private static CalcResult flatCalc(BigDecimal flat, String tierName) {
        return new CalcResult(flat, null, flat, tierName);
    }

    /**
     * Choose the calendar date the commission is attributed to. Inscription
     * payments anchor on the payment date (when the affiliate signed up);
     * recurring payments anchor on the {@code applied_period} (the
     * billing month covered) when present, else the payment date's first
     * of month.
     */
    private static LocalDate resolvePeriodAnchor(Payment payment) {
        if (payment.isInscription()) {
            return payment.getPaymentDate();
        }
        return payment.getAppliedPeriod() != null
                ? payment.getAppliedPeriod()
                : payment.getPaymentDate();
    }

    /** Internal result of the plan-type → calculation switch. */
    private record CalcResult(
            BigDecimal amount,
            BigDecimal pct,        // null when flat
            BigDecimal flat,       // null when pct
            String tierName
    ) {}
}
