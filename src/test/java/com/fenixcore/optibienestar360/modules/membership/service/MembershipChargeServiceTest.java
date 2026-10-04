package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.ChargeStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipChargeRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionPricing;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MembershipChargeService} — the payment-driven half of
 * the billing cycle (V153): idempotent charge generation, single-month
 * settlement, and the multi-month advance case (one payment covering several
 * consecutive {@code MembershipCharge} rows).
 */
@ExtendWith(MockitoExtension.class)
class MembershipChargeServiceTest {

    @Mock private MembershipChargeRepository chargeRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private ScheduledJobRepository jobRepository;
    @Mock private MembershipStatusService membershipStatusService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PromotionPricing promotionPricing;

    private MembershipChargeService service;

    @BeforeEach
    void setUp() {
        service = new MembershipChargeService(chargeRepository, membershipRepository, jobRepository, membershipStatusService, eventPublisher, promotionPricing);
        lenient().when(chargeRepository.save(any(MembershipCharge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // No promotion or subsidy by default: the charge is the full monthly fee.
        lenient().when(promotionPricing.priceMonthly(any(), any())).thenAnswer(invocation ->
                new PromotionPricing.MonthlyPrice(((Membership) invocation.getArgument(0)).getMonthlyFee(),
                        BigDecimal.ZERO, null, null));
    }

    // ─── Pricing (hub ADR 0018) ─────────────────────────────────────────────

    @Test
    void createdCharge_carriesGrossDiscountAndNet_fromTheWinningDiscount() {
        Membership membership = membership(1L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        when(chargeRepository.findByMembership_IdAndPeriodStart(1L, LocalDate.of(2026, 3, 1))).thenReturn(Optional.empty());
        when(promotionPricing.priceMonthly(membership, LocalDate.of(2026, 3, 1))).thenReturn(
                new PromotionPricing.MonthlyPrice(new BigDecimal("25.00"), new BigDecimal("5.00"),
                        MembershipCharge.DiscountSource.PROMOTION, null));

        MembershipCharge charge = service.ensureChargeForPeriod(membership, LocalDate.of(2026, 3, 1));

        assertThat(charge.getGrossAmount()).isEqualByComparingTo("25.00");
        assertThat(charge.getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(charge.getAmount()).isEqualByComparingTo("20.00");
        assertThat(charge.getDiscountSource()).isEqualTo(MembershipCharge.DiscountSource.PROMOTION);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING.name());
    }

    @Test
    void fullyDiscountedCharge_isWaived_andAdvancesTheMembership_whenItIsTheNextUnpaidMonth() {
        // Enrolled 2026-01-15, nothing paid yet → January is the next unpaid month.
        Membership membership = membership(1L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        when(chargeRepository.findByMembership_IdAndPeriodStart(1L, LocalDate.of(2026, 1, 1))).thenReturn(Optional.empty());
        when(promotionPricing.priceMonthly(membership, LocalDate.of(2026, 1, 1))).thenReturn(
                new PromotionPricing.MonthlyPrice(new BigDecimal("25.00"), new BigDecimal("25.00"),
                        MembershipCharge.DiscountSource.PROMOTION, null));

        MembershipCharge charge = service.ensureChargeForPeriod(membership, LocalDate.of(2026, 1, 1));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.WAIVED.name());
        assertThat(charge.getAmount()).isEqualByComparingTo("0.00");
        assertThat(membership.getLastPaidThrough()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(membership.getNextDueDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        verify(membershipStatusService).applyTransition(eq(membership), any());
    }

    @Test
    void fullyDiscountedCharge_furtherAhead_doesNotSkipAnUnpaidMonth() {
        Membership membership = membership(1L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        when(chargeRepository.findByMembership_IdAndPeriodStart(1L, LocalDate.of(2026, 3, 1))).thenReturn(Optional.empty());
        when(promotionPricing.priceMonthly(membership, LocalDate.of(2026, 3, 1))).thenReturn(
                new PromotionPricing.MonthlyPrice(new BigDecimal("25.00"), new BigDecimal("25.00"),
                        MembershipCharge.DiscountSource.SUBSIDY, null));

        service.ensureChargeForPeriod(membership, LocalDate.of(2026, 3, 1));

        assertThat(membership.getLastPaidThrough()).isNull();
        verify(membershipStatusService, never()).applyTransition(any(), any());
    }

    // ─── ensureChargeForPeriod ──────────────────────────────────────────────

    @Test
    void ensureChargeForPeriod_returnsExisting_withoutSaving_whenAlreadyPresent() {
        Membership membership = membership(1L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        MembershipCharge existing = new MembershipCharge();
        existing.setStatus(ChargeStatus.PENDING.name());
        when(chargeRepository.findByMembership_IdAndPeriodStart(1L, LocalDate.of(2026, 3, 1)))
                .thenReturn(Optional.of(existing));

        MembershipCharge result = service.ensureChargeForPeriod(membership, LocalDate.of(2026, 3, 5));

        assertThat(result).isSameAs(existing);
        verify(chargeRepository, never()).save(any());
    }

    @Test
    void ensureChargeForPeriod_createsPending_whenNoneExists() {
        Membership membership = membership(1L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        when(chargeRepository.findByMembership_IdAndPeriodStart(1L, LocalDate.of(2026, 3, 1)))
                .thenReturn(Optional.empty());

        MembershipCharge result = service.ensureChargeForPeriod(membership, LocalDate.of(2026, 3, 1));

        assertThat(result.getStatus()).isEqualTo(ChargeStatus.PENDING.name());
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(result.getAmount()).isEqualByComparingTo("25.00");
        verify(chargeRepository).save(result);
    }

    // ─── applyPayment ───────────────────────────────────────────────────────

    @Test
    void applyPayment_singleMonth_marksChargeCovered_andAdvancesDates() {
        Membership membership = membership(2L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        when(chargeRepository.findByMembership_IdAndPeriodStart(anyLong(), any())).thenReturn(Optional.empty());

        Payment payment = new Payment();
        payment.setMembership(membership);
        payment.setInscription(false);
        payment.setAppliedPeriod(LocalDate.of(2026, 2, 1));

        service.applyPayment(payment);

        assertThat(membership.getLastPaidThrough()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(membership.getNextDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        verify(chargeRepository, times(1)).save(any(MembershipCharge.class));
        verify(membershipStatusService).applyTransition(eq(membership), any(LocalDate.class));
    }

    @Test
    void applyPayment_sixMonthAdvance_coversEveryMonth_andFlipsSuspendedToActive() {
        Membership membership = membership(3L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.SUSPENDED);
        when(chargeRepository.findByMembership_IdAndPeriodStart(anyLong(), any())).thenReturn(Optional.empty());
        when(membershipStatusService.applyTransition(eq(membership), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    membership.setStatus(LifecycleStatus.ACTIVE.name());
                    return true;
                });

        Payment payment = new Payment();
        payment.setMembership(membership);
        payment.setInscription(false);
        payment.setAppliedPeriod(LocalDate.of(2026, 1, 1));
        payment.setCoverageThroughPeriod(LocalDate.of(2026, 6, 1));

        service.applyPayment(payment);

        verify(chargeRepository, times(6)).save(any(MembershipCharge.class));
        assertThat(membership.getLastPaidThrough()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(membership.getNextDueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(membership.getStatus()).isEqualTo(LifecycleStatus.ACTIVE.name());
    }

    @Test
    void applyPayment_isNoop_forInscriptionPayment() {
        Membership membership = membership(4L, LocalDate.of(2026, 1, 15), null, LifecycleStatus.ACTIVE);
        Payment payment = new Payment();
        payment.setMembership(membership);
        payment.setInscription(true);

        service.applyPayment(payment);

        verify(chargeRepository, never()).save(any());
        verify(membershipStatusService, never()).applyTransition(any(), any());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Membership membership(Long id, LocalDate enrolledAt, Integer billingStartDay, LifecycleStatus status) {
        Member member = new Member();
        member.setId(id);

        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("USD");

        Membership m = new Membership();
        m.setId(id);
        m.setMember(member);
        m.setActive(true);
        m.setStatus(status.name());
        m.setEnrolledAt(enrolledAt);
        m.setBillingStartDay(billingStartDay);
        m.setMonthlyFee(new BigDecimal("25.00"));
        m.setCurrency(currency);
        m.setGracePeriodDays(7);
        return m;
    }
}
