package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PersonBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PersonBankAccountRepository extends JpaRepository<PersonBankAccount, Long> {

    List<PersonBankAccount> findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(Long personId);

    Optional<PersonBankAccount> findByUuidAndPersonIdAndActiveTrue(UUID uuid, Long personId);

    Optional<PersonBankAccount> findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(
            Long personId, Long bankId, String accountIdentifier);
}
