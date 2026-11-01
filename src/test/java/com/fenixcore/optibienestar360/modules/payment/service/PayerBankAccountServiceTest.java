package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PayerBankAccount;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PayerBankAccountRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A person's own accounts need the sum of the is_mandatory_* flags of the methods they accept, like the company accounts (V218). */
@ExtendWith(MockitoExtension.class)
class PayerBankAccountServiceTest {

    @Mock private PayerBankAccountRepository repository;
    @Mock private BankRepository bankRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;

    private final UUID methodUuid = UUID.randomUUID();
    private final UUID bankUuid = UUID.randomUUID();

    private PayerBankAccountService sut() {
        return new PayerBankAccountService(repository, bankRepository, userRepository, paymentMethodRepository);
    }

    private static Person person() {
        Person p = new Person();
        p.setId(3L);
        return p;
    }

    /** Pago Móvil: bank, holder document and phone; no account type or number. */
    private PaymentMethod pagoMovil() {
        PaymentMethod m = new PaymentMethod();
        m.setId(11L);
        m.setUuid(methodUuid);
        m.setCode("PAGO_MOVIL");
        m.setActive(true);
        m.setMandatoryBank(true);
        m.setMandatoryIdentification(true);
        m.setMandatoryPhone(true);
        return m;
    }

    /** Bank transfer: bank, account type and account number; no phone. */
    private PaymentMethod transfer() {
        PaymentMethod m = new PaymentMethod();
        m.setId(12L);
        m.setUuid(methodUuid);
        m.setCode("BANK_TRANSFER");
        m.setActive(true);
        m.setMandatoryBank(true);
        m.setMandatoryAccountType(true);
        m.setMandatoryBankAccount(true);
        return m;
    }

    private void stubCatalogs(PaymentMethod method) {
        Bank bank = new Bank();
        bank.setId(7L);
        bank.setUuid(bankUuid);
        bank.setCode("0105");
        bank.setShortName("Mercantil");
        bank.setActive(true);
        lenient().when(paymentMethodRepository.findByUuid(methodUuid)).thenReturn(Optional.of(method));
        lenient().when(bankRepository.findByUuid(bankUuid)).thenReturn(Optional.of(bank));
        lenient().when(repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(3L)).thenReturn(List.of());
        lenient().when(repository.save(any(PayerBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubUser(Person person) {
        User user = new User();
        user.setPerson(person);
        when(userRepository.findByUuid(any())).thenReturn(Optional.of(user));
    }

    private PayerBankAccountRequest request(String type, String number, String document, String phone) {
        return new PayerBankAccountRequest(List.of(methodUuid), bankUuid, type, null, number, document, phone, null, "Mi cuenta");
    }

    // ─── Rules by method ────────────────────────────────────────────────────

    @Test
    void pagoMovil_needsAPhone() {
        stubUser(person());
        stubCatalogs(pagoMovil());

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), request(null, null, "V12345678", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.phone.required");
    }

    @Test
    void pagoMovil_needsNoAccountTypeOrNumber() {
        stubUser(person());
        stubCatalogs(pagoMovil());

        var created = sut().createMine(UUID.randomUUID(), request(null, null, "V12345678", " 04141234567 "));

        assertThat(created.phone()).isEqualTo("04141234567");
        assertThat(created.accountIdentifier()).isNull();
        assertThat(created.paymentMethodCodes()).containsExactly("PAGO_MOVIL");
    }

    @Test
    void transfer_needsAnAccountNumber() {
        stubUser(person());
        stubCatalogs(transfer());

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), request("CORRIENTE", " ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.account_number.required");
    }

    @Test
    void transfer_needsNoPhone() {
        stubUser(person());
        stubCatalogs(transfer());

        var created = sut().createMine(UUID.randomUUID(), request("CORRIENTE", "01050000000000000000", null, null));

        assertThat(created.phone()).isNull();
        assertThat(created.accountIdentifier()).isEqualTo("01050000000000000000");
    }

    // ─── Duplicates and ownership ───────────────────────────────────────────

    @Test
    void createMine_rejectsTheSameAccountTwice() {
        stubUser(person());
        stubCatalogs(pagoMovil());
        PayerBankAccount existing = new PayerBankAccount();
        existing.setId(99L);
        existing.setBank(bankOf(7L));
        existing.setPhone("04141234567");
        when(repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(3L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), request(null, null, "V12345678", "04141234567")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.duplicate");
    }

    @Test
    void updateMine_cannotTouchAnotherPersonsAccount() {
        stubUser(person());
        when(repository.findByUuidAndPersonIdAndActiveTrue(any(), eq(3L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().updateMine(UUID.randomUUID(), UUID.randomUUID(), request("CORRIENTE", "1", null, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("payer_bank_account.not_found");
    }

    private static Bank bankOf(Long id) {
        Bank b = new Bank();
        b.setId(id);
        return b;
    }

    // ─── Remembering from a payment ─────────────────────────────────────────

    @Test
    void remember_isANoOp_withoutAnythingThatIdentifiesTheAccount() {
        stubCatalogs(pagoMovil());

        sut().remember(person(), methodUuid, bankUuid, null, null, null, "V123", "  ", null);

        verify(repository, never()).save(any());
    }

    @Test
    void remember_isANoOp_whenTheMethodRequiresMoreThanWasGiven() {
        stubCatalogs(pagoMovil());

        // Pago Móvil needs the holder document too.
        sut().remember(person(), methodUuid, bankUuid, null, null, null, null, "04141234567", null);

        verify(repository, never()).save(any());
    }

    @Test
    void remember_savesAPagoMovilAccount_scopedToThePerson() {
        stubCatalogs(pagoMovil());
        Person person = person();

        sut().remember(person, methodUuid, bankUuid, null, null, null, "V123", " 04141234567 ", null);

        ArgumentCaptor<PayerBankAccount> captor = ArgumentCaptor.forClass(PayerBankAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPerson()).isSameAs(person);
        assertThat(captor.getValue().getPhone()).isEqualTo("04141234567");
        assertThat(captor.getValue().getPaymentMethods()).extracting(PaymentMethod::getCode).containsExactly("PAGO_MOVIL");
    }

    @Test
    void remember_refreshesTheExistingAccount_insteadOfDuplicatingIt() {
        stubCatalogs(transfer());
        PayerBankAccount existing = new PayerBankAccount();
        existing.setId(50L);
        existing.setBank(bankOf(7L));
        existing.setAccountIdentifier("0102123");
        when(repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(3L)).thenReturn(List.of(existing));

        sut().remember(person(), methodUuid, bankUuid, "AHORRO", null, "0102123", "V999", null, null);

        verify(repository).save(existing);
        assertThat(existing.getAccountType()).isEqualTo("AHORRO");
        assertThat(existing.getIdentification()).isEqualTo("V999");
    }

    @Test
    void remember_addsTheMethodToTheExistingAccount_andFillsInWhatItLacked() {
        stubCatalogs(pagoMovil());
        PaymentMethod transfer = transfer();
        PayerBankAccount existing = new PayerBankAccount();
        existing.setId(50L);
        existing.setBank(bankOf(7L));
        existing.setAccountIdentifier("0102123");
        existing.setAccountType("CORRIENTE");
        existing.getPaymentMethods().add(transfer);
        when(repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(3L)).thenReturn(List.of(existing));

        // The same account is now paid by Pago Móvil: same bank and number, plus a phone and the holder document.
        sut().remember(person(), methodUuid, bankUuid, null, null, "0102123", "V999", "04141234567", null);

        verify(repository).save(existing);
        assertThat(existing.getPaymentMethods()).extracting(PaymentMethod::getCode)
                .containsExactlyInAnyOrder("BANK_TRANSFER", "PAGO_MOVIL");
        assertThat(existing.getPhone()).isEqualTo("04141234567");
        assertThat(existing.getAccountType()).isEqualTo("CORRIENTE");
    }

    @Test
    void remember_doesNotAddAMethod_theAccountCannotSatisfy() {
        stubCatalogs(pagoMovil());
        PayerBankAccount existing = new PayerBankAccount();
        existing.setId(50L);
        existing.setBank(bankOf(7L));
        existing.setAccountIdentifier("0102123");
        existing.getPaymentMethods().add(transfer());
        when(repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(3L)).thenReturn(List.of(existing));

        // Pago Móvil asks for a phone and none is known for this account.
        sut().remember(person(), methodUuid, bankUuid, null, null, "0102123", "V999", null, null);

        verify(repository, never()).save(any());
        assertThat(existing.getPaymentMethods()).hasSize(1);
    }

    @Test
    void severalMethods_needTheSumOfWhatEachAsks() {
        stubUser(person());
        PaymentMethod pagoMovil = pagoMovil();
        PaymentMethod transfer = transfer();
        UUID transferUuid = UUID.randomUUID();
        transfer.setUuid(transferUuid);
        stubCatalogs(pagoMovil);
        lenient().when(paymentMethodRepository.findByUuid(transferUuid)).thenReturn(Optional.of(transfer));
        PayerBankAccountRequest noPhone = new PayerBankAccountRequest(List.of(methodUuid, transferUuid), bankUuid,
                "CORRIENTE", null, "01050000000000000000", "V12345678", null, null, null);

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), noPhone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.phone.required");
    }

    @Test
    void anAccountWithoutMethods_isRejected() {
        stubUser(person());
        stubCatalogs(pagoMovil());
        PayerBankAccountRequest none = new PayerBankAccountRequest(List.of(), bankUuid, null, null, null, "V1",
                "04141234567", null, null);

        assertThatThrownBy(() -> sut().createMine(UUID.randomUUID(), none))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payer_bank_account.payment_methods.required");
    }
}
