package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentLineRepository extends JpaRepository<PaymentLine, Long> {

    List<PaymentLine> findByPaymentUuid(UUID paymentUuid);
}
