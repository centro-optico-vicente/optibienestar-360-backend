package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long>, JpaSpecificationExecutor<PaymentMethod> {

    Optional<PaymentMethod> findByUuid(UUID uuid);

    /** Natural-key lookup — services resolving a hardcoded method code (e.g. "CASH") into the entity. */
    Optional<PaymentMethod> findByCode(String code);

    List<PaymentMethod> findAllByActiveTrueOrderByName();
}
