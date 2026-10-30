package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PayerBankAccountRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.PayerBankAccount;
import com.fenixcore.optibienestar360.modules.payment.repository.PayerBankAccountRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The origin accounts a person chose to remember (V204). Everything is scoped to the caller's own person; the
 * payment flow asks to remember an account with a checkbox, so nothing is saved without that decision.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayerBankAccountService {

    private final PayerBankAccountRepository repository;
    private final BankRepository bankRepository;
    private final UserRepository userRepository;

    public List<PayerBankAccountDto> listMine(UUID userUuid) {
        return repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(personOf(userUuid).getId()).stream()
                .map(PayerBankAccountService::toDto).toList();
    }

    public PayerBankAccountDto getMine(UUID userUuid, UUID accountUuid) {
        return toDto(findMine(userUuid, accountUuid));
    }

    /** Adds one of the caller's own accounts; the same bank + number twice is rejected, not duplicated. */
    @Transactional
    public PayerBankAccountDto createMine(UUID userUuid, PayerBankAccountRequest request) {
        Person person = personOf(userUuid);
        Bank bank = activeBank(request.bankUuid());
        String identifier = request.accountIdentifier().trim();
        if (repository.findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(person.getId(), bank.getId(), identifier).isPresent()) {
            throw new IllegalArgumentException("payer_bank_account.duplicate");
        }
        PayerBankAccount account = new PayerBankAccount();
        account.setPerson(person);
        apply(account, bank, identifier, request);
        return toDto(repository.save(account));
    }

    @Transactional
    public PayerBankAccountDto updateMine(UUID userUuid, UUID accountUuid, PayerBankAccountRequest request) {
        PayerBankAccount account = findMine(userUuid, accountUuid);
        Bank bank = activeBank(request.bankUuid());
        String identifier = request.accountIdentifier().trim();
        repository.findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(account.getPerson().getId(), bank.getId(), identifier)
                .filter(other -> !other.getId().equals(account.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("payer_bank_account.duplicate");
                });
        apply(account, bank, identifier, request);
        return toDto(account);
    }

    private static void apply(PayerBankAccount account, Bank bank, String identifier, PayerBankAccountRequest request) {
        account.setBank(bank);
        account.setAccountIdentifier(identifier);
        account.setAccountType(request.accountType());
        account.setAccountCode(request.accountCode());
        account.setIdentification(request.identification());
        account.setAlias(request.alias());
    }

    private Bank activeBank(UUID bankUuid) {
        return bankRepository.findByUuid(bankUuid).filter(Bank::isActive)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
    }

    private PayerBankAccount findMine(UUID userUuid, UUID accountUuid) {
        return repository.findByUuidAndPersonIdAndActiveTrue(accountUuid, personOf(userUuid).getId())
                .orElseThrow(() -> new NoSuchElementException("payer_bank_account.not_found"));
    }

    @Transactional
    public void deleteMine(UUID userUuid, UUID accountUuid) {
        findMine(userUuid, accountUuid).setActive(false);
    }

    /**
     * Remembers (or refreshes) the origin account of a payment line for {@code person}. Needs a bank and an account
     * number; otherwise there is nothing worth remembering and it is a no-op. The same bank + number is never stored twice.
     */
    @Transactional
    public void remember(Person person, java.util.UUID bankUuid, String accountType, String accountCode,
                         String accountIdentifier, String identification) {
        if (bankUuid == null || accountIdentifier == null || accountIdentifier.isBlank()) {
            return;
        }
        Bank bank = bankRepository.findByUuid(bankUuid).filter(Bank::isActive)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
        String identifier = accountIdentifier.trim();
        PayerBankAccount account = repository
                .findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(person.getId(), bank.getId(), identifier)
                .orElseGet(() -> {
                    PayerBankAccount created = new PayerBankAccount();
                    created.setPerson(person);
                    created.setBank(bank);
                    created.setAccountIdentifier(identifier);
                    return created;
                });
        account.setAccountType(accountType);
        account.setAccountCode(accountCode);
        account.setIdentification(identification);
        account.setLastUsedAt(Instant.now());
        repository.save(account);
    }

    private Person personOf(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        if (user.getPerson() == null) {
            throw new NoSuchElementException("person.not_found");
        }
        return user.getPerson();
    }

    static PayerBankAccountDto toDto(PayerBankAccount a) {
        return new PayerBankAccountDto(a.getUuid(), DisplayRefs.ref(a.getBank()), a.getAccountType(),
                a.getAccountCode(), a.getAccountIdentifier(), a.getIdentification(), a.getAlias());
    }
}
