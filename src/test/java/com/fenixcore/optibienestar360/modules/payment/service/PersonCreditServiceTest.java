package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PersonCreditEntry;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonCreditServiceTest {

    @Mock private PersonCreditEntryRepository repository;

    private PersonCreditService sut() {
        return new PersonCreditService(repository);
    }

    private static Payment collection(Membership membership) {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setDirection("IN");
        payment.setAmount(new BigDecimal("30.00"));
        payment.setCurrency(new Currency());
        payment.setMembership(membership);
        return payment;
    }

    @Test
    void recordCreditIfUnassigned_booksTheNetAmount_whenThereIsNoMembership() {
        sut().recordCreditIfUnassigned(collection(null));

        ArgumentCaptor<PersonCreditEntry> captor = ArgumentCaptor.forClass(PersonCreditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo("CREDIT");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void recordCreditIfUnassigned_creditsTheBeneficiary_whenSomebodyElsePaid() {
        Payment payment = collection(null);
        com.fenixcore.optibienestar360.modules.person.entity.Person payer = new com.fenixcore.optibienestar360.modules.person.entity.Person();
        com.fenixcore.optibienestar360.modules.person.entity.Person holder = new com.fenixcore.optibienestar360.modules.person.entity.Person();
        payment.setPerson(payer);
        payment.setBeneficiaryPerson(holder);

        sut().recordCreditIfUnassigned(payment);

        ArgumentCaptor<PersonCreditEntry> captor = ArgumentCaptor.forClass(PersonCreditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPerson()).isSameAs(holder);
    }

    @Test
    void recordCreditIfUnassigned_skipsPaymentsThatHaveAMembership() {
        sut().recordCreditIfUnassigned(collection(new Membership()));

        verify(repository, never()).save(any());
    }

    @Test
    void recordCreditIfUnassigned_isIdempotent() {
        when(repository.existsByPaymentIdAndEntryType(5L, "CREDIT")).thenReturn(true);

        sut().recordCreditIfUnassigned(collection(null));

        verify(repository, never()).save(any());
    }

    @Test
    void applyRemaining_booksTheNegativeRemainder() {
        Payment payment = collection(null);
        when(repository.remainingOfPayment(5L)).thenReturn(new BigDecimal("30.00"));

        BigDecimal applied = sut().applyRemaining(payment, new Membership());

        assertThat(applied).isEqualByComparingTo("30.00");
        ArgumentCaptor<PersonCreditEntry> captor = ArgumentCaptor.forClass(PersonCreditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo("APPLIED");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void applyRemaining_rejectsWhenNothingIsLeft() {
        when(repository.remainingOfPayment(5L)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> sut().applyRemaining(collection(null), new Membership()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment.credit.nothing_to_apply");
    }
}
