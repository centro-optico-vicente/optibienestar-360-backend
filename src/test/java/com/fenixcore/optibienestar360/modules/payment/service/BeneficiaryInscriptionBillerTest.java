package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
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
    @InjectMocks private BeneficiaryInscriptionBiller biller;

    @Test
    void chargeExtraInscription_createsPendingInscriptionPayment() {
        Membership membership = new Membership();
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(55L);
            return p;
        });

        Long id = biller.chargeExtraInscription(membership, new BigDecimal("5.00"));

        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getMembership()).isSameAs(membership);
        assertThat(saved.getAmount()).isEqualByComparingTo("5.00");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getPaymentMethod()).isEqualTo(Payment.PaymentMethod.OTHER);
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
}
