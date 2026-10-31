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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin CRUD over {@code company_bank_accounts} (V201) plus the read-only list payers see when
 * they register a payment. Soft-delete only — {@code payment_lines.company_bank_account_id} points here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyBankAccountService {

    /** Account type whose payments go to a phone number instead of an account number (V206). */
    static final String PAGO_MOVIL = "PAGO_MOVIL";

    private static final String[] SEARCHABLE_FIELDS = {"accountNumber", "holderName", "holderDocument"};
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CompanyBankAccount.class, Map.of());

    private final CompanyBankAccountRepository repository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
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
        // A Pago Móvil account is paid to a phone number, so the phone is not optional for that type.
        if (PAGO_MOVIL.equalsIgnoreCase(req.accountType()) && blankToNull(req.mobilePaymentPhone()) == null) {
            throw new IllegalArgumentException("company_bank_account.mobile_phone.required");
        }
        Bank bank = bankRepository.findByUuid(req.bankUuid())
                .filter(Bank::isActive)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
        Currency currency = currencyRepository.findByCode(req.currency())
                .filter(Currency::isActive)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
        account.setBank(bank);
        account.setCurrency(currency);
        account.setAccountType(req.accountType());
        account.setAccountNumber(req.accountNumber());
        account.setHolderName(req.holderName());
        account.setHolderDocument(req.holderDocument());
        account.setNotes(req.notes());
        account.setMobilePaymentPhone(blankToNull(req.mobilePaymentPhone()));
        if (req.sortOrder() != null) {
            account.setSortOrder(req.sortOrder());
        }
        if (req.publicVisible() != null) {
            account.setPublicVisible(req.publicVisible());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CompanyBankAccount find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("company_bank_account.not_found"));
    }

    public static CompanyBankAccountDto toDto(CompanyBankAccount a) {
        return new CompanyBankAccountDto(a.getUuid(), DisplayRefs.ref(a.getBank()), DisplayRefs.ref(a.getCurrency()),
                a.getCurrency().getCode(), a.getAccountType(), a.getAccountNumber(), a.getHolderName(),
                a.getHolderDocument(), a.getNotes(), a.getMobilePaymentPhone(), a.getSortOrder(), a.isPublicVisible(), a.isActive());
    }
}
