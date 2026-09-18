package com.fenixcore.optibienestar360.modules.bank.repository;

import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BankRepository extends JpaRepository<Bank, Long>, JpaSpecificationExecutor<Bank> {

    Optional<Bank> findByUuid(UUID uuid);

    /** Natural-key lookup — services resolving a SUDEBAN code into the entity. */
    Optional<Bank> findByCode(String code);

    List<Bank> findAllByActiveTrueOrderByName();
}
