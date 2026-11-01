package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.AccountMethodDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PayerBankAccount;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PayerBankAccountRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The origin accounts a person chose to remember (V204). One account accepts several payment methods (V217) and what
 * it needs is the sum of their is_mandatory_* checks — Pago Móvil asks for bank, holder document and phone, a transfer
 * for account type and number, Zelle for an email. Everything is scoped to the caller's own person; the payment flow
 * asks to remember an account with a checkbox, so nothing is saved without that decision.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayerBankAccountService {

    private final PayerBankAccountRepository repository;
    private final BankRepository bankRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public List<PayerBankAccountDto> listMine(UUID userUuid) {
        return repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(personOf(userUuid).getId()).stream()
                .map(PayerBankAccountService::toDto).toList();
    }

    public PayerBankAccountDto getMine(UUID userUuid, UUID accountUuid) {
        return toDto(findMine(userUuid, accountUuid));
    }

    /** Adds one of the caller's own accounts; the same bank + number / phone / email twice is rejected. */
    @Transactional
    public PayerBankAccountDto createMine(UUID userUuid, PayerBankAccountRequest request) {
        Person person = personOf(userUuid);
        Set<PaymentMethod> methods = activeMethods(request.paymentMethodUuids());
        Bank bank = optionalBank(request.bankUuid());
        Fields fields = Fields.of(request);
        validate(methods, bank, fields);
        if (findSame(person.getId(), bank, fields, null).isPresent()) {
            throw new IllegalArgumentException("payer_bank_account.duplicate");
        }
        PayerBankAccount account = new PayerBankAccount();
        account.setPerson(person);
        apply(account, methods, bank, fields, request.alias());
        return toDto(repository.save(account));
    }

    @Transactional
    public PayerBankAccountDto updateMine(UUID userUuid, UUID accountUuid, PayerBankAccountRequest request) {
        PayerBankAccount account = findMine(userUuid, accountUuid);
        Set<PaymentMethod> methods = activeMethods(request.paymentMethodUuids());
        Bank bank = optionalBank(request.bankUuid());
        Fields fields = Fields.of(request);
        validate(methods, bank, fields);
        if (findSame(account.getPerson().getId(), bank, fields, account.getId()).isPresent()) {
            throw new IllegalArgumentException("payer_bank_account.duplicate");
        }
        apply(account, methods, bank, fields, request.alias());
        return toDto(account);
    }

    @Transactional
    public void deleteMine(UUID userUuid, UUID accountUuid) {
        findMine(userUuid, accountUuid).setActive(false);
    }

    /**
     * Remembers (or refreshes) the origin account of a payment line for {@code person}. The payment itself is
     * validated elsewhere, so here an account that does not have what its method requires — or has nothing that
     * identifies it, like cash — is simply not remembered (no-op). The same account (bank + number / phone / email)
     * is never stored twice: when it already exists the method is added to the ones it accepts and any data it was
     * missing is filled in, as long as it then still holds what all its methods ask for.
     */
    @Transactional
    public void remember(Person person, UUID methodUuid, UUID bankUuid, String accountType, String accountCode,
                         String accountIdentifier, String identification, String phone, String email) {
        if (methodUuid == null) {
            return;
        }
        PaymentMethod method = paymentMethodRepository.findByUuid(methodUuid).filter(PaymentMethod::isActive).orElse(null);
        Bank bank = bankUuid == null ? null : bankRepository.findByUuid(bankUuid).filter(Bank::isActive).orElse(null);
        if (method == null || (bankUuid != null && bank == null)) {
            return;
        }
        Fields fields = new Fields(blankToNull(accountType), blankToNull(accountCode), blankToNull(accountIdentifier),
                blankToNull(identification), blankToNull(phone), blankToNull(email));
        if (fields.key() == null) {
            return;
        }
        PayerBankAccount existing = findSame(person.getId(), bank, fields, null).orElse(null);
        if (existing == null) {
            if (missingRequired(Set.of(method), bank, fields) != null) {
                return;
            }
            PayerBankAccount created = new PayerBankAccount();
            created.setPerson(person);
            apply(created, Set.of(method), bank, fields, null);
            created.setLastUsedAt(Instant.now());
            repository.save(created);
            return;
        }
        Set<PaymentMethod> methods = new HashSet<>(existing.getPaymentMethods());
        methods.add(method);
        Fields merged = fields.fillingGapsFrom(existing);
        if (missingRequired(methods, bank, merged) != null) {
            return;
        }
        apply(existing, methods, bank, merged, existing.getAlias());
        existing.setLastUsedAt(Instant.now());
        repository.save(existing);
    }

    // ─── Rules ──────────────────────────────────────────────────────────────

    private static void validate(Set<PaymentMethod> methods, Bank bank, Fields fields) {
        String missing = missingRequired(methods, bank, fields);
        if (missing != null) {
            throw new IllegalArgumentException("payer_bank_account." + missing + ".required");
        }
        if (fields.key() == null) {
            throw new IllegalArgumentException("payer_bank_account.account_number.required");
        }
    }

    /**
     * The first required field that any accepted method asks for and is missing, as a message-code fragment;
     * {@code null} when complete.
     */
    private static String missingRequired(Set<PaymentMethod> methods, Bank bank, Fields f) {
        for (PaymentMethod method : methods) {
            if (method.isMandatoryBank() && bank == null) return "bank";
            if (method.isMandatoryIdentification() && f.identification() == null) return "holder_document";
            if (method.isMandatoryAccountType() && f.accountType() == null) return "account_type";
            if ((method.isMandatoryAccountCode() || method.isMandatoryBankAccount()) && f.accountIdentifier() == null)
                return "account_number";
            if (method.isMandatoryPhone() && f.phone() == null) return "phone";
            if (method.isMandatoryEmail() && f.email() == null) return "email";
        }
        return null;
    }

    /** An account with the same bank and number / phone / email, other than {@code exceptId}. */
    private java.util.Optional<PayerBankAccount> findSame(Long personId, Bank bank, Fields fields, Long exceptId) {
        Long bankId = bank == null ? null : bank.getId();
        return repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(personId).stream()
                .filter(a -> !Objects.equals(a.getId(), exceptId))
                .filter(a -> Objects.equals(a.getBank() == null ? null : a.getBank().getId(), bankId))
                .filter(a -> Objects.equals(firstNonNull(a.getAccountIdentifier(), a.getPhone(), a.getEmail()), fields.key()))
                .findFirst();
    }

    private static void apply(PayerBankAccount account, Set<PaymentMethod> methods, Bank bank, Fields f, String alias) {
        account.getPaymentMethods().clear();
        account.getPaymentMethods().addAll(methods);
        account.setBank(bank);
        account.setAccountType(f.accountType());
        account.setAccountCode(f.accountCode());
        account.setAccountIdentifier(f.accountIdentifier());
        account.setIdentification(f.identification());
        account.setPhone(f.phone());
        account.setEmail(f.email());
        account.setAlias(blankToNull(alias));
    }

    private Set<PaymentMethod> activeMethods(List<UUID> methodUuids) {
        Set<PaymentMethod> methods = new LinkedHashSet<>();
        for (UUID uuid : new LinkedHashSet<>(methodUuids)) {
            methods.add(paymentMethodRepository.findByUuid(uuid).filter(PaymentMethod::isActive)
                    .orElseThrow(() -> new NoSuchElementException("payment_method.not_found")));
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("payer_bank_account.payment_methods.required");
        }
        return methods;
    }

    private Bank optionalBank(UUID bankUuid) {
        return bankUuid == null ? null : bankRepository.findByUuid(bankUuid).filter(Bank::isActive)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
    }

    private PayerBankAccount findMine(UUID userUuid, UUID accountUuid) {
        return repository.findByUuidAndPersonIdAndActiveTrue(accountUuid, personOf(userUuid).getId())
                .orElseThrow(() -> new NoSuchElementException("payer_bank_account.not_found"));
    }

    private Person personOf(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        if (user.getPerson() == null) {
            throw new NoSuchElementException("person.not_found");
        }
        return user.getPerson();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** Trimmed, blank-as-null account fields; {@link #key()} is what makes two accounts the same one. */
    private record Fields(String accountType, String accountCode, String accountIdentifier, String identification,
                          String phone, String email) {
        static Fields of(PayerBankAccountRequest r) {
            return new Fields(blankToNull(r.accountType()), blankToNull(r.accountCode()), blankToNull(r.accountIdentifier()),
                    blankToNull(r.identification()), blankToNull(r.phone()), blankToNull(r.email()));
        }

        String key() {
            return firstNonNull(accountIdentifier, phone, email);
        }

        /** These fields, with whatever they leave empty taken from the account already stored. */
        Fields fillingGapsFrom(PayerBankAccount a) {
            return new Fields(firstNonNull(accountType, a.getAccountType()), firstNonNull(accountCode, a.getAccountCode()),
                    firstNonNull(accountIdentifier, a.getAccountIdentifier()),
                    firstNonNull(identification, a.getIdentification()),
                    firstNonNull(phone, a.getPhone()), firstNonNull(email, a.getEmail()));
        }
    }

    static PayerBankAccountDto toDto(PayerBankAccount a) {
        List<AccountMethodDto> methods = AccountMethodDto.listOf(a.getPaymentMethods());
        return new PayerBankAccountDto(a.getUuid(), methods, methods.stream().map(AccountMethodDto::code).toList(),
                DisplayRefs.ref(a.getBank()), a.getAccountType(), a.getAccountCode(), a.getAccountIdentifier(),
                a.getIdentification(), a.getPhone(), a.getEmail(), a.getAlias());
    }
}
