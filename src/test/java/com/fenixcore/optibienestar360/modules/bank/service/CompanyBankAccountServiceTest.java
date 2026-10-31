package com.fenixcore.optibienestar360.modules.bank.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountRequest;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.entity.CompanyBankAccount;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.bank.repository.CompanyBankAccountRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CompanyBankAccountServiceTest {

    @Mock private CompanyBankAccountRepository repository;
    @Mock private BankRepository bankRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private CompanyBankAccountService sut() {
        return new CompanyBankAccountService(repository, bankRepository, currencyRepository, defaultSortResolver);
    }

    private static CompanyBankAccountRequest request(UUID bankUuid, String accountType, String phone) {
        return new CompanyBankAccountRequest(bankUuid, "VES", accountType, "01020000000000000000",
                "OptiBienestar", "J123456789", null, phone, 0, false, true);
    }

    @Test
    void create_requiresThePhone_forAPagoMovilAccount() {
        assertThatThrownBy(() -> sut().create(request(UUID.randomUUID(), "PAGO_MOVIL", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("company_bank_account.mobile_phone.required");

        verify(repository, never()).save(any());
    }

    @Test
    void create_storesTheTrimmedPhone_forAPagoMovilAccount() {
        UUID bankUuid = UUID.randomUUID();
        Bank bank = new Bank();
        bank.setActive(true);
        Currency ves = new Currency();
        ves.setCode("VES");
        ves.setActive(true);
        when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(repository.save(any(CompanyBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = sut().create(request(bankUuid, "PAGO_MOVIL", " 04141234567 "));

        assertThat(created.mobilePaymentPhone()).isEqualTo("04141234567");
    }

    @Test
    void create_doesNotNeedAPhone_forOtherTypes() {
        UUID bankUuid = UUID.randomUUID();
        Bank bank = new Bank();
        bank.setActive(true);
        Currency ves = new Currency();
        ves.setCode("VES");
        ves.setActive(true);
        when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(repository.save(any(CompanyBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = sut().create(request(bankUuid, "CORRIENTE", null));

        assertThat(created.mobilePaymentPhone()).isNull();
    }
}
