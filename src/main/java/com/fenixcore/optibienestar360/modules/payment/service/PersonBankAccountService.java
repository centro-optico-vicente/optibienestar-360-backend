package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.PersonBankAccountDto;
import com.fenixcore.optibienestar360.modules.payment.entity.PersonBankAccount;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonBankAccountRepository;
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
public class PersonBankAccountService {

    private final PersonBankAccountRepository repository;
    private final BankRepository bankRepository;
    private final UserRepository userRepository;

    public List<PersonBankAccountDto> listMine(UUID userUuid) {
        return repository.findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(personOf(userUuid).getId()).stream()
                .map(PersonBankAccountService::toDto).toList();
    }

    @Transactional
    public void deleteMine(UUID userUuid, UUID accountUuid) {
        PersonBankAccount account = repository.findByUuidAndPersonIdAndActiveTrue(accountUuid, personOf(userUuid).getId())
                .orElseThrow(() -> new NoSuchElementException("person_bank_account.not_found"));
        account.setActive(false);
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
        PersonBankAccount account = repository
                .findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(person.getId(), bank.getId(), identifier)
                .orElseGet(() -> {
                    PersonBankAccount created = new PersonBankAccount();
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

    static PersonBankAccountDto toDto(PersonBankAccount a) {
        return new PersonBankAccountDto(a.getUuid(), DisplayRefs.ref(a.getBank()), a.getAccountType(),
                a.getAccountCode(), a.getAccountIdentifier(), a.getIdentification(), a.getAlias());
    }
}
