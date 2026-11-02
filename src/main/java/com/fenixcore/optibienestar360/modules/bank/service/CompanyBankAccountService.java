package com.fenixcore.optibienestar360.modules.bank.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountDto;
import com.fenixcore.optibienestar360.modules.bank.dto.CompanyBankAccountRequest;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.entity.CompanyBankAccount;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.bank.repository.CompanyBankAccountRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.AccountMethodDto;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@code company_bank_accounts} (V201) plus the read-only list payers see when
 * they register a payment. Soft-delete only — {@code payment_lines.company_bank_account_id} points here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyBankAccountService {

    private static final String[] SEARCHABLE_FIELDS = {"accountNumber", "holderName", "holderDocument"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CompanyBankAccount.class, Map.of());

    private final CompanyBankAccountRepository repository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final DefaultSortResolver defaultSortResolver;

    public Page<CompanyBankAccountDto> list(Pageable pageable, String q, boolean includeInactive) {
        Pageable resolved = SortFieldValidator.resolve(
                defaultSortResolver.withDefaultSortIfUnsorted("company_bank_account", pageable),
                SORTABLE_FIELDS, "company_bank_account");
        Specification<CompanyBankAccount> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("active"), Boolean.TRUE);
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolved).map(CompanyBankAccountService::toDto);
    }

    /** Active accounts flagged public, in display order — what anyone may see without logging in. */
    public List<CompanyBankAccountDto> listPublic() {
        return repository.findAllByActiveTrueAndPublicVisibleTrueOrderBySortOrderAscIdAsc().stream()
                .map(CompanyBankAccountService::toDto).toList();
    }

    /** Active accounts in display order — what a payer picks from. */
    public List<CompanyBankAccountDto> listActive() {
        return repository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(CompanyBankAccountService::toDto).toList();
    }

    public CompanyBankAccountDto get(UUID uuid) {
        return toDto(find(uuid));
    }

    @Transactional
    @Auditable(entity = "company_bank_account", action = AuditAction.CREATE)
    public CompanyBankAccountDto create(CompanyBankAccountRequest req) {
        CompanyBankAccount account = new CompanyBankAccount();
        apply(account, req);
        return toDto(repository.save(account));
    }

    @Transactional
    @Auditable(entity = "company_bank_account", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CompanyBankAccountDto update(UUID uuid, CompanyBankAccountRequest req) {
        CompanyBankAccount account = find(uuid);
        apply(account, req);
        if (req.active() != null) {
            account.setActive(req.active());
        }
        return toDto(repository.save(account));
    }

    @Transactional
    @Auditable(entity = "company_bank_account", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        CompanyBankAccount account = find(uuid);
        account.setActive(false);
        repository.save(account);
    }

    /** Resolves an active account for a payment line; {@code null} uuid means "not indicated". */
    public CompanyBankAccount resolveActive(UUID uuid) {
        if (uuid == null) return null;
        return repository.findByUuid(uuid)
                .filter(CompanyBankAccount::isActive)
                .orElseThrow(() -> new NoSuchElementException("company_bank_account.not_found"));
    }

    private void apply(CompanyBankAccount account, CompanyBankAccountRequest req) {
        Set<PaymentMethod> methods = resolveMethods(req.paymentMethodUuids());
        Bank bank = req.bankUuid() == null ? null : bankRepository.findByUuid(req.bankUuid())
                .filter(Bank::isActive)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
        Currency currency = currencyRepository.findByCode(req.currency())
                .filter(Currency::isActive)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
        String accountType = blankToNull(req.accountType());
        String accountNumber = blankToNull(req.accountNumber());
        String holderDocument = blankToNull(req.holderDocument());
        String phone = blankToNull(req.phone());
        String email = blankToNull(req.email());

        // The account must hold what EACH method it accepts asks for (the is_mandatory_* checks of the catalog, same
        // rule as the advisor payout accounts): a Pago Móvil asks for bank, document and phone; a transfer for account
        // type and number; Zelle for an email. An account accepting several methods needs the sum of them.
        for (PaymentMethod method : methods) {
            if (method.isMandatoryBank() && bank == null)
                throw new IllegalArgumentException("company_bank_account.bank.required");
            if (method.isMandatoryIdentification() && holderDocument == null)
                throw new IllegalArgumentException("company_bank_account.holder_document.required");
            if (method.isMandatoryAccountType() && accountType == null)
                throw new IllegalArgumentException("company_bank_account.account_type.required");
            if ((method.isMandatoryAccountCode() || method.isMandatoryBankAccount()) && accountNumber == null)
                throw new IllegalArgumentException("company_bank_account.account_number.required");
            if (method.isMandatoryPhone() && phone == null)
                throw new IllegalArgumentException("company_bank_account.phone.required");
            if (method.isMandatoryEmail() && email == null)
                throw new IllegalArgumentException("company_bank_account.email.required");
        }

        account.getPaymentMethods().clear();
        account.getPaymentMethods().addAll(methods);
        account.setBank(bank);
        account.setCurrency(currency);
        account.setAccountType(accountType);
        account.setAccountNumber(accountNumber);
        account.setHolderName(req.holderName());
        account.setHolderDocument(holderDocument);
        account.setNotes(req.notes());
        account.setPhone(phone);
        account.setEmail(email);
        if (req.sortOrder() != null) {
            account.setSortOrder(req.sortOrder());
        }
        if (req.publicVisible() != null) {
            account.setPublicVisible(req.publicVisible());
        }
    }

    /** Active methods for the given uuids (at least one); an unknown or inactive one is a 404 like elsewhere. */
    private Set<PaymentMethod> resolveMethods(List<UUID> uuids) {
        Set<PaymentMethod> methods = new LinkedHashSet<>();
        for (UUID uuid : new LinkedHashSet<>(uuids)) {
            methods.add(paymentMethodRepository.findByUuid(uuid)
                    .filter(PaymentMethod::isActive)
                    .orElseThrow(() -> new NoSuchElementException("payment_method.not_found")));
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("company_bank_account.payment_methods.required");
        }
        return methods;
    }

    /** {@code true} when the account accepts the method — a payment line may only use an accepted one. */
    public static boolean accepts(CompanyBankAccount account, PaymentMethod method) {
        return method != null && account.getPaymentMethods().stream().anyMatch(m -> Objects.equals(m.getUuid(), method.getUuid()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CompanyBankAccount find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("company_bank_account.not_found"));
    }

    public static CompanyBankAccountDto toDto(CompanyBankAccount a) {
        var methods = AccountMethodDto.listOf(a.getPaymentMethods());
        return new CompanyBankAccountDto(a.getUuid(),
                methods,
                methods.stream().map(AccountMethodDto::code).toList(),
                DisplayRefs.ref(a.getBank()),
                a.getBank() == null ? null : a.getBank().getCode(),
                DisplayRefs.ref(a.getCurrency()),
                a.getCurrency().getCode(), a.getAccountType(), a.getAccountNumber(), a.getHolderName(),
                a.getHolderDocument(), a.getNotes(), a.getPhone(), a.getEmail(), a.getSortOrder(), a.isPublicVisible(), a.isActive());
    }
}
