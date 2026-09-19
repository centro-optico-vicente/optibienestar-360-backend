package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentMethodUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock private PaymentMethodRepository repository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private PaymentMethodService service() {
        return new PaymentMethodService(repository, defaultSortResolver);
    }

    private static PaymentMethod method(UUID uuid, String code, String name,
                                       boolean bankAccount, boolean phone, boolean email, boolean referenceNumber,
                                       boolean active) {
        PaymentMethod m = new PaymentMethod();
        m.setUuid(uuid);
        m.setCode(code);
        m.setName(name);
        m.setMandatoryBankAccount(bankAccount);
        m.setMandatoryPhone(phone);
        m.setMandatoryEmail(email);
        m.setMandatoryReferenceNumber(referenceNumber);
        m.setActive(active);
        return m;
    }

    @Test
    void createPersistsEveryFieldAndSetsStatusActive() {
        ArgumentCaptor<PaymentMethod> captor = ArgumentCaptor.forClass(PaymentMethod.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethodDto result = service().create(new PaymentMethodCreateRequest(
                "PRUEBA_DOS", "Prueba Dos", false, false, false, true));

        PaymentMethod saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("PRUEBA_DOS");
        assertThat(saved.getName()).isEqualTo("Prueba Dos");
        assertThat(saved.isMandatoryBankAccount()).isFalse();
        assertThat(saved.isMandatoryPhone()).isFalse();
        assertThat(saved.isMandatoryEmail()).isFalse();
        assertThat(saved.isMandatoryReferenceNumber()).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        assertThat(result.code()).isEqualTo("PRUEBA_DOS");
        assertThat(result.name()).isEqualTo("Prueba Dos");
        assertThat(result.mandatoryReferenceNumber()).isTrue();
        assertThat(result.active()).isTrue();
    }

    @Test
    void updateChangesFieldsButNeverCode() {
        UUID uuid = UUID.randomUUID();
        PaymentMethod existing = method(uuid, "ZELLE", "Zelle", false, false, true, true, true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethodDto result = service().update(uuid,
                new PaymentMethodUpdateRequest("Zelle Updated", true, true, true, false, false));

        assertThat(result.code()).isEqualTo("ZELLE");
        assertThat(result.name()).isEqualTo("Zelle Updated");
        assertThat(result.mandatoryBankAccount()).isTrue();
        assertThat(result.mandatoryPhone()).isTrue();
        assertThat(result.mandatoryEmail()).isTrue();
        assertThat(result.mandatoryReferenceNumber()).isFalse();
        assertThat(result.active()).isFalse();
    }

    @Test
    void deleteIsSoftOnly() {
        UUID uuid = UUID.randomUUID();
        PaymentMethod existing = method(uuid, "CASH", "Efectivo", false, false, false, false, true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(repository.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        service().delete(uuid);

        assertThat(existing.isActive()).isFalse();
        verify(repository, never()).delete(any(PaymentMethod.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void getThrowsWhenNotFound() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(uuid))
                .isInstanceOf(NoSuchElementException.class);
    }
}
