package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService.ConversionResult;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.ChargeStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApplicationServiceTest {

    @Mock private PersonCreditEntryRepository creditRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PaymentCreditClaimService claimService;
    @Mock private MembershipChargeService chargeService;
    @Mock private PersonCreditService personCreditService;
    @Mock private PaymentsService paymentsService;
    @Mock private CurrencyConversionService conversionService;
    @Mock private PlatformTransactionManager transactionManager;

    private final Currency usd = currency(1L, "USD");
    private Person person;
    private Membership membership;
    private Payment payment;

    private CreditApplicationService sut() {
        return new CreditApplicationService(creditRepository, paymentRepository, membershipRepository, claimService,
                chargeService, personCreditService, paymentsService, conversionService, transactionManager);
    }

    private static Currency currency(Long id, String code) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        return c;
    }

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(3L);
        Member member = new Member();
        member.setPerson(person);
        membership = new Membership();
        membership.setId(7L);
        membership.setMember(member);
        membership.setActive(true);
        membership.setStatus(Membership.LifecycleStatus.ACTIVE.name());
        membership.setEnrolledAt(LocalDate.of(2026, 1, 15));
        payment = new Payment();
        payment.setId(1L);
        payment.setCurrency(usd);
        payment.setPaymentDate(Instant.parse("2026-01-10T12:00:00Z"));

        lenient().when(creditRepository.paymentsWithCredit(3L)).thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("30.00")}));
        lenient().when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        // Every month costs 10.00 USD; the same currency converts 1:1.
        lenient().when(chargeService.ensureChargeForPeriod(any(Membership.class), any(LocalDate.class))).thenAnswer(inv -> {
            MembershipCharge charge = new MembershipCharge();
            charge.setPeriodStart(inv.getArgument(1));
            charge.setAmount(new BigDecimal("10.00"));
            charge.setCurrency(usd);
            charge.setStatus(ChargeStatus.PENDING.name());
            return charge;
        });
        lenient().when(conversionService.convert(any(), any(), any(), any())).thenAnswer(inv ->
                new ConversionResult(inv.getArgument(0), inv.getArgument(0), BigDecimal.ONE, LocalDate.of(2026, 1, 10)));
    }

    @Test
    void applyCredit_coversOnlyFullMonths_andAttachesThePaymentOnce() {
        when(claimService.usableCredit(payment)).thenReturn(new BigDecimal("25.00"));

        int covered = sut().applyCredit(membership);

        assertThat(covered).isEqualTo(2);   // 25.00 buys two 10.00 months, never half of a third
        verify(chargeService, times(2)).coverWithCredit(eq(membership), any(LocalDate.class), eq(payment));
        verify(personCreditService, times(2)).applyForCharge(eq(payment), eq(membership), any(MembershipCharge.class),
                eq(new BigDecimal("10.00")), eq(BigDecimal.ONE), any(LocalDate.class));
        verify(paymentsService).attachCreditPaymentToMembership(payment, membership,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), false);
    }

    @Test
    void applyCredit_coversThreeMonths_withThirtyOfCredit() {
        when(claimService.usableCredit(payment)).thenReturn(new BigDecimal("30.00"));

        assertThat(sut().applyCredit(membership)).isEqualTo(3);
        verify(paymentsService).attachCreditPaymentToMembership(payment, membership,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), false);
    }

    @Test
    void applyCredit_doesNothing_whenThePersonsCreditIsOnHold() {
        person.setCreditAutoApply(false);

        assertThat(sut().applyCredit(membership)).isZero();
        verifyNoInteractions(chargeService, personCreditService, paymentsService);
    }

    @Test
    void applyCredit_doesNothing_whenTheMembershipIsNotActive() {
        membership.setStatus(Membership.LifecycleStatus.SUSPENDED.name());

        assertThat(sut().applyCredit(membership)).isZero();
        verifyNoInteractions(chargeService, personCreditService, paymentsService);
    }

    @Test
    void applyCredit_skipsAPaymentWhoseCreditIsAllFrozen() {
        when(claimService.usableCredit(payment)).thenReturn(BigDecimal.ZERO);

        assertThat(sut().applyCredit(membership)).isZero();
        verify(chargeService, never()).coverWithCredit(any(), any(), any());
        verify(paymentsService, never()).attachCreditPaymentToMembership(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void applyCredit_leavesThePaymentForManualAssignment_whenThereIsNoExchangeRate() {
        when(claimService.usableCredit(payment)).thenReturn(new BigDecimal("30.00"));
        when(conversionService.convert(any(), any(), any(), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available:USD->VES"));

        assertThat(sut().applyCredit(membership)).isZero();
        verify(chargeService, never()).coverWithCredit(any(), any(), any());
        verify(personCreditService, never()).applyForCharge(any(), any(), any(), any(), any(), any());
    }

    @Test
    void applyCredit_doesNotCommissionAgain_whenThePaymentWasAlreadyAttached() {
        payment.setMembership(membership);   // a first application already attached it (and commissioned it)
        when(claimService.usableCredit(payment)).thenReturn(new BigDecimal("10.00"));

        assertThat(sut().applyCredit(membership)).isEqualTo(1);
        verify(paymentsService, never()).attachCreditPaymentToMembership(any(), any(), any(), any(), anyBoolean());
    }
}
