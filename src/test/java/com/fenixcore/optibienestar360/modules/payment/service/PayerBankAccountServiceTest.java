package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.PayerBankAccount;
import com.fenixcore.optibienestar360.modules.payment.repository.PayerBankAccountRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayerBankAccountServiceTest {

    @Mock private PayerBankAccountRepository repository;
    @Mock private BankRepository bankRepository;
    @Mock private UserRepository userRepository;

    private PayerBankAccountService sut() {
        return new PayerBankAccountService(repository, bankRepository, userRepository);
    }

    private static Person person() {
        Person p = new Person();
        p.setId(3L);
        return p;
    }

    private static Bank bank(UUID uuid) {
        Bank b = new Bank();
        b.setId(7L);
        b.setUuid(uuid);
        b.setActive(true);
        return b;
    }

    @Test
    void remember_isANoOp_withoutAnAccountNumber() {
        sut().remember(person(), UUID.randomUUID(), "CORRIENTE", null, "  ", "V123");

        verify(repository, never()).save(any());
    }

    @Test
    void remember_isANoOp_withoutABank() {
        sut().remember(person(), null, "CORRIENTE", null, "0102123", "V123");

        verify(repository, never()).save(any());
    }

    @Test
    void remember_savesANewAccount_scopedToThePerson() {
        UUID bankUuid = UUID.randomUUID();
        Person person = person();
        when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank(bankUuid)));
        when(repository.findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(3L, 7L, "0102123"))
                .thenReturn(Optional.empty());

        sut().remember(person, bankUuid, "CORRIENTE", "0102", " 0102123 ", "V123");

        ArgumentCaptor<PayerBankAccount> captor = ArgumentCaptor.forClass(PayerBankAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPerson()).isSameAs(person);
        assertThat(captor.getValue().getAccountIdentifier()).isEqualTo("0102123");
        assertThat(captor.getValue().getIdentification()).isEqualTo("V123");
    }

    @Test
    void remember_refreshesTheExistingAccount_insteadOfDuplicatingIt() {
        UUID bankUuid = UUID.randomUUID();
        PayerBankAccount existing = new PayerBankAccount();
        existing.setAccountIdentifier("0102123");
        when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank(bankUuid)));
        when(repository.findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(3L, 7L, "0102123"))
                .thenReturn(Optional.of(existing));

        sut().remember(person(), bankUuid, "AHORRO", null, "0102123", "V999");

        verify(repository).save(existing);
        assertThat(existing.getAccountType()).isEqualTo("AHORRO");
        assertThat(existing.getIdentification()).isEqualTo("V999");
    }
}
