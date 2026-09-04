package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral.ReferralStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the affiliate referral service. Mocks the repository
 * + member lookup; verifies the resolve / self-ref / anti-dup guards
 * and the FIFO reward-grant policy.
 */
@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CurrencyRepository currencyRepository;

    @InjectMocks private ReferralService service;

    // ─── registerOnEnrollment ───────────────────────────────────────────────

    @Test
    void register_resolves_code_and_persists_REGISTERED_row_with_v1_reward() {
        Member referrer = member(10L, "REFERRER");
        Member referred = member(20L, "REFERRED");
        when(memberRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(referrer));
        when(referralRepository.existsActiveByReferrerAndReferred(10L, 20L)).thenReturn(false);
        when(referralRepository.save(any(Referral.class))).thenAnswer(i -> i.getArgument(0));
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd()));

        Optional<Referral> result = service.registerOnEnrollment(referred, "ABC123");

        assertThat(result).isPresent();
        Referral r = result.get();
        assertThat(r.getReferrer()).isEqualTo(referrer);
        assertThat(r.getReferred()).isEqualTo(referred);
        assertThat(r.getReferralCode()).isEqualTo("ABC123");
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.REGISTERED.name());
        assertThat(r.getRewardPct()).isEqualByComparingTo("10.00");
        assertThat(r.getRewardCurrency().getCode()).isEqualTo("USD");
        assertThat(r.getRewardFlatAmount()).isNull();
        assertThat(r.getEnrolledAt()).isNotNull();
    }

    @Test
    void register_trims_whitespace_in_code() {
        Member referrer = member(10L, "REFERRER");
        Member referred = member(20L, "REFERRED");
        when(memberRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(referrer));
        when(referralRepository.existsActiveByReferrerAndReferred(10L, 20L)).thenReturn(false);
        when(referralRepository.save(any(Referral.class))).thenAnswer(i -> i.getArgument(0));
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd()));

        Optional<Referral> result = service.registerOnEnrollment(referred, "  ABC123  ");

        assertThat(result).isPresent();
        assertThat(result.get().getReferralCode()).isEqualTo("ABC123");
    }

    @Test
    void register_skips_when_code_blank_or_null() {
        Member referred = member(20L, "REFERRED");

        assertThat(service.registerOnEnrollment(referred, null)).isEmpty();
        assertThat(service.registerOnEnrollment(referred, "")).isEmpty();
        assertThat(service.registerOnEnrollment(referred, "   ")).isEmpty();

        verify(memberRepository, never()).findByReferralCode(any());
        verify(referralRepository, never()).save(any());
    }

    @Test
    void register_skips_when_code_does_not_resolve() {
        Member referred = member(20L, "REFERRED");
        when(memberRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());

        Optional<Referral> result = service.registerOnEnrollment(referred, "NOPE");

        assertThat(result).isEmpty();
        verify(referralRepository, never()).save(any());
    }

    @Test
    void register_skips_when_referrer_is_inactive() {
        Member referrer = member(10L, "REFERRER");
        referrer.setActive(false);
        Member referred = member(20L, "REFERRED");
        when(memberRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(referrer));

        Optional<Referral> result = service.registerOnEnrollment(referred, "ABC123");

        assertThat(result).isEmpty();
        verify(referralRepository, never()).save(any());
    }

    @Test
    void register_skips_self_referral() {
        Member self = member(10L, "SELF");
        when(memberRepository.findByReferralCode("SELF")).thenReturn(Optional.of(self));

        Optional<Referral> result = service.registerOnEnrollment(self, "SELF");

        assertThat(result).isEmpty();
        verify(referralRepository, never()).existsActiveByReferrerAndReferred(anyLong(), anyLong());
        verify(referralRepository, never()).save(any());
    }

    @Test
    void register_skips_when_active_pair_already_exists() {
        Member referrer = member(10L, "REFERRER");
        Member referred = member(20L, "REFERRED");
        when(memberRepository.findByReferralCode("ABC123")).thenReturn(Optional.of(referrer));
        when(referralRepository.existsActiveByReferrerAndReferred(10L, 20L)).thenReturn(true);

        Optional<Referral> result = service.registerOnEnrollment(referred, "ABC123");

        assertThat(result).isEmpty();
        verify(referralRepository, never()).save(any());
    }

    // ─── applyRewardsTo ────────────────────────────────────────────────────

    @Test
    void apply_grants_oldest_unclaimed_FIFO() {
        Member referrer = member(10L, "REFERRER");
        Referral older = pendingReferral(referrer, Instant.parse("2026-01-01T00:00:00Z"));
        Referral newer = pendingReferral(referrer, Instant.parse("2026-06-01T00:00:00Z"));
        Payment payment = paymentFor(referrer);
        when(referralRepository.findUnclaimedByReferrer(10L)).thenReturn(List.of(older, newer));

        Optional<Referral> granted = service.applyRewardsTo(payment);

        assertThat(granted).isPresent();
        assertThat(granted.get()).isSameAs(older);
        assertThat(older.getStatus()).isEqualTo(ReferralStatus.REWARD_GRANTED.name());
        assertThat(older.getRewardPayment()).isEqualTo(payment);
        assertThat(older.getRewardGrantedAt()).isNotNull();
        // Newer untouched
        assertThat(newer.getStatus()).isEqualTo(ReferralStatus.REGISTERED.name());
        assertThat(newer.getRewardGrantedAt()).isNull();
    }

    @Test
    void apply_returns_empty_when_no_unclaimed() {
        Member referrer = member(10L, "REFERRER");
        Payment payment = paymentFor(referrer);
        when(referralRepository.findUnclaimedByReferrer(10L)).thenReturn(List.of());

        Optional<Referral> result = service.applyRewardsTo(payment);

        assertThat(result).isEmpty();
    }

    @Test
    void apply_returns_empty_for_null_or_broken_payment() {
        assertThat(service.applyRewardsTo(null)).isEmpty();

        Payment paymentNoMembership = new Payment();
        assertThat(service.applyRewardsTo(paymentNoMembership)).isEmpty();

        Payment paymentNoMember = new Payment();
        paymentNoMember.setMembership(new Membership());
        assertThat(service.applyRewardsTo(paymentNoMember)).isEmpty();
    }

    // ─── discountFor ────────────────────────────────────────────────────────

    @Test
    void discount_computes_percentage_correctly() {
        Referral r = new Referral();
        r.setRewardPct(new BigDecimal("10.00"));

        assertThat(service.discountFor(r, new BigDecimal("5.00")))
                .isEqualByComparingTo("0.50");
        assertThat(service.discountFor(r, new BigDecimal("100.00")))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void discount_returns_flat_when_pct_null() {
        Referral r = new Referral();
        r.setRewardFlatAmount(new BigDecimal("2.50"));

        assertThat(service.discountFor(r, new BigDecimal("100.00")))
                .isEqualByComparingTo("2.50");
    }

    @Test
    void discount_returns_zero_when_no_reward_configured() {
        Referral r = new Referral();  // both pct + flat null

        assertThat(service.discountFor(r, new BigDecimal("100.00")))
                .isEqualByComparingTo("0");
    }

    @Test
    void discount_safe_against_nulls() {
        assertThat(service.discountFor(null, new BigDecimal("100.00")))
                .isEqualByComparingTo("0");
        assertThat(service.discountFor(new Referral(), null))
                .isEqualByComparingTo("0");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Member member(Long id, String referralCode) {
        Member m = new Member();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        m.setReferralCode(referralCode);
        m.setActive(true);
        return m;
    }

    private static Referral pendingReferral(Member referrer, Instant enrolledAt) {
        Referral r = new Referral();
        r.setUuid(UUID.randomUUID());
        r.setReferrer(referrer);
        r.setReferred(member(99L, "REFERRED"));
        r.setReferralCode(referrer.getReferralCode());
        r.setEnrolledAt(enrolledAt);
        r.setRewardPct(new BigDecimal("10.00"));
        r.setRewardCurrency(usd());
        r.setStatus(ReferralStatus.REGISTERED.name());
        return r;
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        c.setName("Dolar estadounidense");
        c.setSymbol("US$");
        c.setDecimalPlaces((short) 2);
        return c;
    }

    private static Payment paymentFor(Member member) {
        Membership membership = new Membership();
        membership.setMember(member);
        Payment p = new Payment();
        p.setId(77L);
        p.setUuid(UUID.randomUUID());
        p.setMembership(membership);
        return p;
    }
}
