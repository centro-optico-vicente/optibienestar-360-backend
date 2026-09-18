package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BeneficiaryInscriptionBiller} — the extra-beneficiary
 * inscription charge (v2 flyer "Afiliado Adicional $5") and the id→UUID resolver.
 */
@ExtendWith(MockitoExtension.class)
class BeneficiaryInscriptionBillerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private PaymentCategoryRepository paymentCategoryRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @InjectMocks private BeneficiaryInscriptionBiller biller;

    @Test
    void chargeExtraInscription_createsPendingInscriptionPayment() {
        Member member = new Member();
        member.setPerson(new Person());
        Membership membership = new Membership();
        membership.setMember(member);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(55L);
            return p;
        });
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd()));
        when(paymentCategoryRepository.findByCode("INSCRIPTION_FEE")).thenReturn(Optional.of(inscriptionFee()));
        when(paymentMethodRepository.findByCode("OTHER")).thenReturn(Optional.of(otherMethod()));

        Long id = biller.chargeExtraInscription(membership, new BigDecimal("5.00"));

        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getMembership()).isSameAs(membership);
        assertThat(saved.getAmount()).isEqualByComparingTo("5.00");
        assertThat(saved.getCurrency().getCode()).isEqualTo("USD");
        assertThat(saved.getDirection()).isEqualTo("IN");
        assertThat(saved.getPaymentType().getCode()).isEqualTo("INSCRIPTION_FEE");
        assertThat(saved.getLines()).hasSize(1);
        assertThat(saved.getLines().getFirst().getPaymentType().getCode()).isEqualTo("OTHER");
        assertThat(saved.isInscription()).isTrue();
        assertThat(saved.getAppliedPeriod()).isNull();   // V23 CHECK: inscription rows carry no period
        assertThat(saved.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING.name());
        assertThat(saved.getPaymentDate()).isNotNull();
    }

    @Test
    void resolveUuid_nullId_returnsNullWithoutQuery() {
        assertThat(biller.resolveUuid(null)).isNull();
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void resolveUuid_resolvesToPaymentUuid() {
        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID());
        when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));

        assertThat(biller.resolveUuid(55L)).isEqualTo(payment.getUuid());
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        c.setName("Dolar estadounidense");
        c.setSymbol("US$");
        c.setDecimalPlaces((short) 2);
        return c;
    }

    private static PaymentCategory inscriptionFee() {
        PaymentCategory c = new PaymentCategory();
        c.setCode("INSCRIPTION_FEE");
        c.setName("Cuota de inscripción");
        c.setDirection("IN");
        return c;
    }

    private static PaymentMethod otherMethod() {
        PaymentMethod m = new PaymentMethod();
        m.setCode("OTHER");
        m.setName("Otro");
        return m;
    }
}
