package com.fenixcore.optibienestar360.modules.bank.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountRequest;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.entity.CompanyBankAccount;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.bank.repository.CompanyBankAccountRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Required fields of a company account follow its payment method's is_mandatory_* flags (V206). */
@ExtendWith(MockitoExtension.class)
class CompanyBankAccountServiceTest {

    @Mock private CompanyBankAccountRepository repository;
    @Mock private BankRepository bankRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private final UUID methodUuid = UUID.randomUUID();
    private final UUID bankUuid = UUID.randomUUID();

    private CompanyBankAccountService sut() {
        return new CompanyBankAccountService(repository, bankRepository, currencyRepository, paymentMethodRepository,
                defaultSortResolver);
    }

    /** Pago Móvil: bank, holder document and phone; no account type or number. */
    private PaymentMethod pagoMovil() {
        PaymentMethod m = new PaymentMethod();
        m.setUuid(methodUuid);
        m.setCode("PAGO_MOVIL");
        m.setActive(true);
        m.setMandatoryBank(true);
        m.setMandatoryIdentification(true);
        m.setMandatoryPhone(true);
        return m;
    }

    /** Bank transfer: bank, holder document, account type and account number; no phone. */
    private PaymentMethod transfer() {
        PaymentMethod m = new PaymentMethod();
        m.setUuid(methodUuid);
        m.setCode("BANK_TRANSFER");
        m.setActive(true);
        m.setMandatoryBank(true);
        m.setMandatoryIdentification(true);
        m.setMandatoryAccountType(true);
        m.setMandatoryBankAccount(true);
        return m;
    }

    private void stubCatalogs(PaymentMethod method) {
        Bank bank = new Bank();
        bank.setActive(true);
        bank.setCode("0102");
        bank.setShortName("Venezuela");
        Currency ves = new Currency();
        ves.setCode("VES");
        ves.setActive(true);
        when(paymentMethodRepository.findByUuid(methodUuid)).thenReturn(Optional.of(method));
        lenient().when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank));
        lenient().when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        lenient().when(repository.save(any(CompanyBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CompanyBankAccountRequest request(String accountType, String accountNumber, String phone) {
        return new CompanyBankAccountRequest(methodUuid, bankUuid, "VES", accountType, accountNumber,
                "OptiBienestar", "J123456789", null, phone, null, 0, false, true);
    }

    @Test
    void pagoMovil_needsAPhone() {
        stubCatalogs(pagoMovil());

        assertThatThrownBy(() -> sut().create(request(null, null, "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("company_bank_account.phone.required");
        verify(repository, never()).save(any());
    }

    @Test
    void pagoMovil_needsNoAccountTypeOrNumber() {
        stubCatalogs(pagoMovil());

        var created = sut().create(request(null, null, " 04141234567 "));

        assertThat(created.phone()).isEqualTo("04141234567");
        assertThat(created.accountType()).isNull();
        assertThat(created.accountNumber()).isNull();
        assertThat(created.paymentMethodCode()).isEqualTo("PAGO_MOVIL");
        assertThat(created.bankCode()).isEqualTo("0102");
    }

    @Test
    void transfer_needsAnAccountType() {
        stubCatalogs(transfer());

        assertThatThrownBy(() -> sut().create(request(null, "01020000000000000000", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("company_bank_account.account_type.required");
    }

    @Test
    void transfer_needsAnAccountNumber() {
        stubCatalogs(transfer());

        assertThatThrownBy(() -> sut().create(request("CORRIENTE", " ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("company_bank_account.account_number.required");
    }

    @Test
    void transfer_needsNoPhone() {
        stubCatalogs(transfer());

        var created = sut().create(request("CORRIENTE", "01020000000000000000", null));

        assertThat(created.phone()).isNull();
        assertThat(created.accountNumber()).isEqualTo("01020000000000000000");
    }
}
