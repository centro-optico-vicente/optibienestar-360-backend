package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral.ReferralStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Two-operation service for the affiliate-to-affiliate referral program
 * (V27). Single-purpose singular name matching the {@code CommissionService}
 * pattern — admin reads / queue endpoints would live in a plural
 * {@code ReferralsService} when those bullets arrive.
 *
 * <ol>
 *   <li><b>{@link #registerOnEnrollment(Member, String)}</b> — at member
 *       enrollment time, if the new affiliate provided a code, walks
 *       {@code members.referral_code} (V27) to resolve the referrer
 *       and registers a {@code REGISTERED} referral row with the v1
 *       reward config inline.</li>
 *
 *   <li><b>{@link #applyRewardsTo(Payment)}</b> — when the referrer's
 *       monthly payment is created (or approved), check for unclaimed
 *       referrals and mark one as {@code REWARD_GRANTED} (FIFO). The
 *       caller uses the returned {@link Referral} to apply the discount
 *       to the payment amount.</li>
 * </ol>
 *
 * <p><b>v1 reward policy</b> (hardcoded — v2 {@code referral_programs}
 * replaces with DB-driven config; the snapshot fields on the referral
 * row stay identical so historical rows keep their inline config across
 * the cutover):</p>
 *
 * <ul>
 *   <li>Reward: 10% off the referrer's next monthly fee.</li>
 *   <li>Currency: USD (matches V26 / V23 defaults).</li>
 *   <li>One referral granted per referrer payment (FIFO oldest first).</li>
 *   <li>Self-referral guard: skipped silently if the code resolves to
 *       the referred member themselves.</li>
 * </ul>
 *
 * <p><b>Cross-table code resolution</b>: per V27 design, the
 * service-layer resolver gives precedence to the promoter table when a
 * code is valid for both. This service is therefore <i>only</i> called
 * <b>after</b> the caller has verified the code is NOT a promoter
 * referral_code — it walks {@code members.referral_code} unconditionally
 * when invoked. The collision case never produces a duplicate flow.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    /** Hardcoded v1 reward — v2 commission_tiers analogue lives in referral_programs. */
    private static final BigDecimal V1_REWARD_PCT = new BigDecimal("10.00");
    private static final String V1_REWARD_CURRENCY = "USD";

    private final ReferralRepository referralRepository;
    private final MemberRepository memberRepository;
    private final CurrencyRepository currencyRepository;

    // ─── 1. Register at enrollment ─────────────────────────────────────────

    /**
     * Resolves the code to a referrer Member and creates a REGISTERED
     * referral row. No-op when the code is blank, doesn't resolve, would
     * be a self-referral, or a referral already exists for the pair.
     *
     * @return the persisted referral, or empty when nothing was created
     */
    @Transactional
    public Optional<Referral> registerOnEnrollment(Member referredMember, String code) {
        if (referredMember == null) return Optional.empty();
        if (code == null || code.isBlank()) return Optional.empty();

        Optional<Member> referrerOpt = memberRepository.findByReferralCode(code.trim());
        if (referrerOpt.isEmpty() || !referrerOpt.get().isActive()) {
            log.debug("Referral skipped: code {} did not resolve to an active member", code);
            return Optional.empty();
        }
        Member referrer = referrerOpt.get();

        if (referrer.getId().equals(referredMember.getId())) {
            log.debug("Referral skipped: self-referral for member {}", referredMember.getUuid());
            return Optional.empty();
        }

        if (referralRepository.existsActiveByReferrerAndReferred(referrer.getId(), referredMember.getId())) {
            log.debug("Referral skipped: active row already exists for referrer {} → referred {}",
                    referrer.getUuid(), referredMember.getUuid());
            return Optional.empty();
        }

        Referral referral = new Referral();
        referral.setReferrer(referrer);
        referral.setReferred(referredMember);
        referral.setReferralCode(code.trim());
        referral.setEnrolledAt(Instant.now());
        // Reward configured inline at registration; remains pct (v1 policy).
        referral.setRewardPct(V1_REWARD_PCT);
        referral.setRewardCurrency(currencyRepository.findByCode(V1_REWARD_CURRENCY)
                .orElseThrow(() -> new java.util.NoSuchElementException("currency.not_found")));
        referral.setStatus(ReferralStatus.REGISTERED.name());

        Referral saved = referralRepository.save(referral);
        log.info("Referral registered: referrer={} referred={} code={} reward={}%",
                referrer.getUuid(), referredMember.getUuid(), code, V1_REWARD_PCT);
        return Optional.of(saved);
    }

    // ─── 2. Apply reward to referrer's payment ─────────────────────────────

    /**
     * Picks one unclaimed referral for the payment's referrer (FIFO,
     * oldest enrolled first) and marks it {@code REWARD_GRANTED} with the
     * payment as the discount target. The CALLER is responsible for
     * actually subtracting the discount from {@code payment.amount} —
     * this service only tracks the grant.
     *
     * @return the granted referral, or empty when no unclaimed referrals
     *         exist for the referrer
     */
    @Transactional
    public Optional<Referral> applyRewardsTo(Payment payment) {
        if (payment == null || payment.getMembership() == null) return Optional.empty();
        Member referrer = payment.getMembership().getMember();
        if (referrer == null) return Optional.empty();

        List<Referral> unclaimed = referralRepository.findUnclaimedByReferrer(referrer.getId());
        if (unclaimed.isEmpty()) return Optional.empty();

        Referral toGrant = unclaimed.get(0);
        toGrant.setStatus(ReferralStatus.REWARD_GRANTED.name());
        toGrant.setRewardPayment(payment);
        toGrant.setRewardGrantedAt(Instant.now());

        log.info("Referral reward granted: referral={} referrer={} payment={} reward={}%",
                toGrant.getUuid(), referrer.getUuid(), payment.getUuid(), toGrant.getRewardPct());
        return Optional.of(toGrant);
    }

    /**
     * Computes the discount amount a granted referral would apply to a
     * given base amount. Pure function — no DB writes, safe to call
     * before persisting. Callers use this to preview the discount in a
     * UI or to compute the final {@code payment.amount} after granting.
     */
    public BigDecimal discountFor(Referral referral, BigDecimal baseAmount) {
        if (referral == null || baseAmount == null) return BigDecimal.ZERO;
        if (referral.getRewardPct() != null) {
            return baseAmount
                    .multiply(referral.getRewardPct())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }
        if (referral.getRewardFlatAmount() != null) {
            return referral.getRewardFlatAmount();
        }
        return BigDecimal.ZERO;
    }
}
