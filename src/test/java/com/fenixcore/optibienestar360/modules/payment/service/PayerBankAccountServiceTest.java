package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private PayerBankAccountRequest request(UUID bankUuid, String identifier) {
        return new PayerBankAccountRequest(bankUuid, "CORRIENTE", "0102", identifier, "V123", "Mi cuenta");
    }

    private void stubUser(Person person) {
        com.fenixcore.optibienestar360.modules.auth.entity.User user = new com.fenixcore.optibienestar360.modules.auth.entity.User();
        user.setPerson(person);
        when(userRepository.findByUuid(any())).thenReturn(Optional.of(user));
    }

    @Test
    void createMine_rejectsTheSameBankAndNumberTwice() {
        UUID bankUuid = UUID.randomUUID();
        stubUser(person());
        when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank(bankUuid)));
        when(repository.findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(3L, 7L, "0102123"))
                .thenReturn(Optional.of(new StoredAccountFactory().account()));

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), request(bankUuid, "0102123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.duplicate");
    }

    @Test
    void updateMine_cannotTouchAnotherPersonsAccount() {
        stubUser(person());
        when(repository.findByUuidAndPersonIdAndActiveTrue(any(), org.mockito.ArgumentMatchers.eq(3L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().updateMine(UUID.randomUUID(), UUID.randomUUID(), request(UUID.randomUUID(), "1")))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("payer_bank_account.not_found");
    }

    /** Builds a stored account with an id, so it is never mistaken for a different row. */
    private static final class StoredAccountFactory {
        PayerBankAccount account() {
            PayerBankAccount a = new PayerBankAccount();
            a.setId(99L);
            return a;
        }
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
