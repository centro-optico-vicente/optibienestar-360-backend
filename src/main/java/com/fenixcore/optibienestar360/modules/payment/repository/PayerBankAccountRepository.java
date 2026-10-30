package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PayerBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PayerBankAccountRepository extends JpaRepository<PayerBankAccount, Long> {

    List<PayerBankAccount> findByPersonIdAndActiveTrueOrderByLastUsedAtDesc(Long personId);

    Optional<PayerBankAccount> findByUuidAndPersonIdAndActiveTrue(UUID uuid, Long personId);

    Optional<PayerBankAccount> findByPersonIdAndBankIdAndAccountIdentifierAndActiveTrue(
            Long personId, Long bankId, String accountIdentifier);
}
