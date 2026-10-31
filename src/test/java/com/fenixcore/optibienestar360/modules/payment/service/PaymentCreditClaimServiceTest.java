package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentClaimResolveRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCreditClaim;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCreditClaim.ClaimStatus;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCreditClaimRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCreditClaimServiceTest {

    @Mock private PaymentCreditClaimRepository repository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PersonCreditEntryRepository creditRepository;
    @Mock private PersonCreditService personCreditService;
    @Mock private UserRepository userRepository;

    private static final UUID ACTOR = UUID.randomUUID();
    private Person payer;
    private User user;
    private Payment payment;

    private PaymentCreditClaimService sut() {
        return new PaymentCreditClaimService(repository, paymentRepository, creditRepository, personCreditService, userRepository);
    }

    @BeforeEach
    void setUp() {
        payer = new Person();
        payer.setId(3L);
        user = new User();
        user.setPerson(payer);
        Currency usd = new Currency();
        usd.setCode("USD");
        payment = new Payment();
        payment.setId(1L);
        payment.setUuid(UUID.randomUUID());
        payment.setActive(true);
        payment.setDirection("IN");
        payment.setStatus(Payment.PaymentStatus.APPROVED.name());
        payment.setPerson(payer);
        payment.setCurrency(usd);
        lenient().when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user));
        lenient().when(paymentRepository.findByUuid(payment.getUuid())).thenReturn(Optional.of(payment));
        lenient().when(creditRepository.remainingOfPayment(1L)).thenReturn(new BigDecimal("30.00"));
        lenient().when(repository.findOpenByPaymentId(1L)).thenReturn(Optional.empty());
        lenient().when(repository.save(any(PaymentCreditClaim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PaymentClaimRequest request(String amount) {
        return new PaymentClaimRequest("OVERPAID", new BigDecimal(amount), "Paid twice");
    }

    @Test
    void openOwn_createsAnOpenClaim_forThePayer() {
        var dto = sut().openOwn(ACTOR, payment.getUuid(), request("10.00"));

        assertThat(dto.status()).isEqualTo(ClaimStatus.OPEN.name());
        assertThat(dto.claimedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void openOwn_hidesAPaymentThatIsNotTheCallers() {
        Person stranger = new Person();
        stranger.setId(99L);
        user.setPerson(stranger);

        assertThatThrownBy(() -> sut().openOwn(ACTOR, payment.getUuid(), request("10.00")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("payment.not_found");
    }

    @Test
    void openOwn_rejectsAPaymentThatIsNotApproved() {
        payment.setStatus(Payment.PaymentStatus.PENDING.name());

        assertThatThrownBy(() -> sut().openOwn(ACTOR, payment.getUuid(), request("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.claim.not_claimable");
    }

    @Test
    void openOwn_rejectsAnAmountAboveTheCreditLeft() {
        assertThatThrownBy(() -> sut().openOwn(ACTOR, payment.getUuid(), request("30.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.claim.amount_exceeds_credit");
    }

    @Test
    void openOwn_allowsOnlyOneOpenClaimPerPayment() {
        when(repository.findOpenByPaymentId(1L)).thenReturn(Optional.of(new PaymentCreditClaim()));

        assertThatThrownBy(() -> sut().openOwn(ACTOR, payment.getUuid(), request("5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.claim.already_open");
    }

    @Test
    void usableCredit_isTheRemainderMinusTheFrozenAmount() {
        when(repository.frozenAmountOfPayment(1L)).thenReturn(new BigDecimal("10.00"));

        assertThat(sut().usableCredit(payment)).isEqualByComparingTo("20.00");
    }

    private PaymentCreditClaim openClaim(String amount) {
        PaymentCreditClaim claim = new PaymentCreditClaim();
        claim.setUuid(UUID.randomUUID());
        claim.setPayment(payment);
        claim.setReason("REFUND_REQUEST");
        claim.setClaimedAmount(new BigDecimal(amount));
        when(repository.findByUuid(claim.getUuid())).thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void resolve_refund_booksAReversal_andClosesTheClaim() {
        PaymentCreditClaim claim = openClaim("10.00");

        var dto = sut().resolve(claim.getUuid(), new PaymentClaimResolveRequest("REFUND", new BigDecimal("8.00"), "Agreed"), ACTOR);

        verify(personCreditService).recordReversal(payment, new BigDecimal("8.00"), "Agreed");
        assertThat(dto.status()).isEqualTo(ClaimStatus.REFUNDED.name());
        assertThat(claim.getRefundedAmount()).isEqualByComparingTo("8.00");
    }

    @Test
    void resolve_refund_rejectsAnAmountAboveTheClaim() {
        PaymentCreditClaim claim = openClaim("10.00");

        assertThatThrownBy(() -> sut().resolve(claim.getUuid(),
                new PaymentClaimResolveRequest("REFUND", new BigDecimal("10.01"), null), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.claim.refund_amount_invalid");
        verify(personCreditService, never()).recordReversal(any(), any(), any());
    }

    @Test
    void resolve_keepAsCredit_releasesTheClaimWithoutMovingMoney() {
        PaymentCreditClaim claim = openClaim("10.00");

        var dto = sut().resolve(claim.getUuid(), new PaymentClaimResolveRequest("KEEP_AS_CREDIT", null, null), ACTOR);

        assertThat(dto.status()).isEqualTo(ClaimStatus.KEPT_AS_CREDIT.name());
        verify(personCreditService, never()).recordReversal(any(), any(), any());
    }

    @Test
    void resolve_rejectsAClaimThatIsNoLongerOpen() {
        PaymentCreditClaim claim = openClaim("10.00");
        claim.setClaimStatus(ClaimStatus.WITHDRAWN.name());

        assertThatThrownBy(() -> sut().resolve(claim.getUuid(), new PaymentClaimResolveRequest("REJECT", null, null), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.claim.not_open");
    }

    @Test
    void closeAsUsedByAgreement_closesTheOpenClaim() {
        PaymentCreditClaim claim = new PaymentCreditClaim();
        claim.setPayment(payment);
        when(repository.findOpenByPaymentId(1L)).thenReturn(Optional.of(claim));

        sut().closeAsUsedByAgreement(payment, ACTOR);

        assertThat(claim.getClaimStatus()).isEqualTo(ClaimStatus.USED_BY_AGREEMENT.name());
        assertThat(claim.getResolvedBy()).isSameAs(user);
    }
}
