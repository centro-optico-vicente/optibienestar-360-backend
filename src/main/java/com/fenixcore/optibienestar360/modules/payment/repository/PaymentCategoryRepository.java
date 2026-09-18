package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentCategoryRepository extends JpaRepository<PaymentCategory, Long>, JpaSpecificationExecutor<PaymentCategory> {

    Optional<PaymentCategory> findByUuid(UUID uuid);

    /** Natural-key lookup — services resolving a hardcoded reason code (e.g. "MEMBERSHIP_FEE") into the entity. */
    Optional<PaymentCategory> findByCode(String code);

    List<PaymentCategory> findAllByActiveTrueOrderByName();
}
