package com.fenixcore.optibienestar360.modules.bank.repository;

import com.fenixcore.optibienestar360.modules.bank.entity.CompanyBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CompanyBankAccountRepository
        extends JpaRepository<CompanyBankAccount, Long>, JpaSpecificationExecutor<CompanyBankAccount> {

    Optional<CompanyBankAccount> findByUuid(UUID uuid);

    List<CompanyBankAccount> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    List<CompanyBankAccount> findAllByActiveTrueAndPublicVisibleTrueOrderBySortOrderAscIdAsc();
}
