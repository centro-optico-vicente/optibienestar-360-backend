package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InscriptionPaymentMethodResolver}: the "Modalidad de
 * pago" of the B2C dossier is the method of the first approved inscription
 * payment line, and {@code null} while there is none.
 */
@ExtendWith(MockitoExtension.class)
class InscriptionPaymentMethodResolverTest {

    @Mock private PaymentLineRepository paymentLineRepository;

    private InscriptionPaymentMethodResolver sut() {
        return new InscriptionPaymentMethodResolver(paymentLineRepository);
    }

    @Test
    void resolve_firstInscriptionLine_returnsItsMethod() {
        Membership membership = membership(7L);
        PaymentMethod pagoMovil = new PaymentMethod();
        pagoMovil.setUuid(UUID.randomUUID());
        pagoMovil.setCode("PAGO_MOVIL");
        pagoMovil.setName("Pago móvil");
        PaymentLine line = new PaymentLine();
        line.setPaymentType(pagoMovil);
        when(paymentLineRepository.findInscriptionLines(7L, PageRequest.of(0, 1))).thenReturn(List.of(line));

        DisplayRef method = sut().resolve(membership);

        assertThat(method).isNotNull();
        assertThat(method.uuid()).isEqualTo(pagoMovil.getUuid());
        assertThat(method.code()).isEqualTo("PAGO_MOVIL");
        assertThat(method.name()).isEqualTo("Pago móvil");
        verify(paymentLineRepository).findInscriptionLines(7L, PageRequest.of(0, 1));
    }

    @Test
    void resolve_noApprovedInscription_isNull() {
        when(paymentLineRepository.findInscriptionLines(any(), any())).thenReturn(List.of());

        assertThat(sut().resolve(membership(7L))).isNull();
    }

    @Test
    void resolve_unsavedMembership_isNullWithoutQuerying() {
        assertThat(sut().resolve(membership(null))).isNull();
        assertThat(sut().resolve(null)).isNull();
        verifyNoInteractions(paymentLineRepository);
    }

    private static Membership membership(Long id) {
        Membership m = new Membership();
        m.setId(id);
        return m;
    }
}
