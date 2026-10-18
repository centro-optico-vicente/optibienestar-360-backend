package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutAccount;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterPayoutAccountRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract of the advisor payout-accounts sub-resource (V184): required
 * fields come from the payment method's {@code is_mandatory_*} flags, a
 * single default per advisor, own-only self-service, URL-tampering 404 and
 * the INSTITUCION system row being read-only. Pure unit tests — independent
 * of the V184 data migration.
 */
@ExtendWith(MockitoExtension.class)
class PromoterPayoutAccountsServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private PromoterPayoutAccountRepository accountRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private BankRepository bankRepository;

    private PromoterPayoutAccountsService service;
    private Promoter promoter;
    private PaymentMethod transfer;
    private PaymentMethod pagoMovil;
    private PaymentMethod ubii;
    private PaymentMethod zelle;
    private Bank bank;

    @BeforeEach
    void setup() {
        service = new PromoterPayoutAccountsService(promoterRepository, accountRepository, paymentMethodRepository, bankRepository);
        promoter = new Promoter();
        promoter.setId(7L);
        promoter.setUuid(UUID.randomUUID());
        lenient().when(promoterRepository.findByUuid(promoter.getUuid())).thenReturn(Optional.of(promoter));
        lenient().when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Flags as seeded by V115/V130/V134/V135/V136/V182.
        transfer = method("BANK_TRANSFER", m -> {
            m.setMandatoryIdentification(true);
            m.setMandatoryBank(true);
            m.setMandatoryAccountType(true);
            m.setMandatoryAccountCode(true);
        });
        pagoMovil = method("PAGO_MOVIL", m -> {
            m.setMandatoryIdentification(true);
            m.setMandatoryBank(true);
            m.setMandatoryPhone(true);
        });
        ubii = method("UBII", m -> {
            m.setMandatoryIdentification(true);
            m.setMandatoryBankAccount(true);
        });
        zelle = method("ZELLE", m -> m.setMandatoryEmail(true));

        bank = new Bank();
        bank.setUuid(UUID.randomUUID());
        lenient().when(bankRepository.findByUuid(bank.getUuid())).thenReturn(Optional.of(bank));
    }

    // ─── Per-method validation ──────────────────────────────────────────────

    @Test
    void create_transfer_requires20DigitAccount_bank_type_andHolder() {
        PromoterPayoutAccountDto dto = service.create(promoter.getUuid(),
                transferRequest("01020123450000012345"));

        assertThat(dto.paymentMethodCode()).isEqualTo("BANK_TRANSFER");
        assertThat(dto.accountNumber()).isEqualTo("01020123450000012345");
    }

    @Test
    void create_transfer_rejectsAccountThatIsNot20Digits() {
        assertThatThrownBy(() -> service.create(promoter.getUuid(), transferRequest("0102-0123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.account_number.format");
    }

    @Test
    void create_transfer_withoutBank_is422() {
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(transfer.getUuid(), null,
                null, "CORRIENTE", "01020123450000012345", null,
                null, "V", "12345678", null, null, null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.bank.required");
    }

    @Test
    void create_pagoMovil_requiresPhone() {
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(pagoMovil.getUuid(), null,
                bank.getUuid(), null, null, null,
                null, "V", "12345678", " ", null, null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.phone.required");
    }

    @Test
    void create_pagoMovil_requiresHolderDocument() {
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(pagoMovil.getUuid(), null,
                bank.getUuid(), null, null, null,
                null, null, null, "04145550101", null, null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.holder_document.required");
    }

    @Test
    void create_ubii_requiresAccountIdentifier() {
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(ubii.getUuid(), null,
                null, null, null, null,
                "Ana Ventas", "V", "12345678", null, null, null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.account_identifier.required");
    }

    @Test
    void create_zelle_requiresEmail_butNoBankNorHolderDocument() {
        PromoterPayoutAccountRequest missing = new PromoterPayoutAccountRequest(zelle.getUuid(), null,
                null, null, null, null, "Ana Ventas", null, null, null, null, null);
        assertThatThrownBy(() -> service.create(promoter.getUuid(), missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.email.required");

        PromoterPayoutAccountRequest ok = new PromoterPayoutAccountRequest(zelle.getUuid(), null,
                null, null, null, null, "Ana Ventas", null, null, null, "ana@example.com", null);
        assertThat(service.create(promoter.getUuid(), ok).email()).isEqualTo("ana@example.com");
    }

    @Test
    void create_rejectsHalfHolderDocumentPair() {
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(zelle.getUuid(), null,
                null, null, null, null, null, "V", null, null, "ana@example.com", null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.holder_document.incomplete");
    }

    @Test
    void create_rejectsInactivePaymentMethod() {
        zelle.setActive(false);
        PromoterPayoutAccountRequest req = new PromoterPayoutAccountRequest(zelle.getUuid(), null,
                null, null, null, null, null, null, null, null, "ana@example.com", null);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_account.payment_method.inactive");
    }

    @Test
    void create_rejectsSystemPromoter() {
        promoter.setSystem(true);

        assertThatThrownBy(() -> service.create(promoter.getUuid(), transferRequest("01020123450000012345")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.system.not_editable");
    }

    // ─── Single default ─────────────────────────────────────────────────────

    @Test
    void create_firstAccount_becomesDefault() {
        when(accountRepository.existsByPromoterIdAndActiveTrue(7L)).thenReturn(false);

        assertThat(service.create(promoter.getUuid(), transferRequest("01020123450000012345")).isDefault()).isTrue();
    }

    @Test
    void create_secondAccount_isNotDefaultUnlessAsked() {
        when(accountRepository.existsByPromoterIdAndActiveTrue(7L)).thenReturn(true);

        assertThat(service.create(promoter.getUuid(), transferRequest("01020123450000012345")).isDefault()).isFalse();
        verify(accountRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void setDefault_unsetsAndFlushesThePreviousDefault_beforeSettingTheNewOne() {
        PromoterPayoutAccount previous = account(10L, true);
        PromoterPayoutAccount target = account(11L, false);
        when(accountRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(accountRepository.findByPromoterIdAndActiveTrueAndDefaultAccountTrue(7L)).thenReturn(List.of(previous));

        PromoterPayoutAccountDto dto = service.setDefault(promoter.getUuid(), target.getUuid());

        assertThat(dto.isDefault()).isTrue();
        assertThat(previous.isDefaultAccount()).isFalse();
        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByPromoterIdAndActiveTrueAndDefaultAccountTrue(7L);
        order.verify(accountRepository).saveAllAndFlush(List.of(previous));
    }

    @Test
    void setDefault_onTheCurrentDefault_isANoOp() {
        PromoterPayoutAccount target = account(11L, true);
        when(accountRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));

        assertThat(service.setDefault(promoter.getUuid(), target.getUuid()).isDefault()).isTrue();
        verify(accountRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void delete_softDeletes_andDropsTheDefaultFlag() {
        PromoterPayoutAccount target = account(11L, true);
        when(accountRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));

        service.delete(promoter.getUuid(), target.getUuid());

        assertThat(target.isActive()).isFalse();
        assertThat(target.getStatus()).isEqualTo("INACTIVE");
        assertThat(target.isDefaultAccount()).isFalse();
    }

    // ─── Ownership / access ─────────────────────────────────────────────────

    @Test
    void accountOfAnotherPromoter_is404() {
        Promoter other = new Promoter();
        other.setId(8L);
        other.setUuid(UUID.randomUUID());
        PromoterPayoutAccount foreign = account(12L, false);
        foreign.setPromoter(other);
        when(accountRepository.findByUuid(foreign.getUuid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(promoter.getUuid(), foreign.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter_payout_account.not_found");
    }

    @Test
    void listMine_onlyReadsTheCallersOwnPromoter() {
        UUID userUuid = UUID.randomUUID();
        PromoterPayoutAccount own = account(10L, true);
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(accountRepository.findByPromoterIdAndActiveTrue(7L, PromoterPayoutAccountsService.DEFAULT_SORT))
                .thenReturn(List.of(own));

        List<PromoterPayoutAccountDto> mine = service.listMine(userUuid);

        assertThat(mine).extracting(PromoterPayoutAccountDto::uuid).containsExactly(own.getUuid());
        assertThat(mine).allMatch(dto -> dto.promoterUuid().equals(promoter.getUuid()));
    }

    @Test
    void listMine_userWithoutPromoterRow_is404() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMine(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private PaymentMethod method(String code, java.util.function.Consumer<PaymentMethod> flags) {
        PaymentMethod m = new PaymentMethod();
        m.setUuid(UUID.randomUUID());
        m.setCode(code);
        m.setName(code);
        flags.accept(m);
        lenient().when(paymentMethodRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        return m;
    }

    private PromoterPayoutAccountRequest transferRequest(String accountNumber) {
        return new PromoterPayoutAccountRequest(transfer.getUuid(), "Nómina",
                bank.getUuid(), "CORRIENTE", accountNumber, null,
                "Ana Ventas", "V", "12345678", null, null, null);
    }

    private PromoterPayoutAccount account(Long id, boolean isDefault) {
        PromoterPayoutAccount a = new PromoterPayoutAccount();
        a.setId(id);
        a.setUuid(UUID.randomUUID());
        a.setPromoter(promoter);
        a.setPaymentMethod(zelle);
        a.setEmail("ana@example.com");
        a.setDefaultAccount(isDefault);
        return a;
    }
}
