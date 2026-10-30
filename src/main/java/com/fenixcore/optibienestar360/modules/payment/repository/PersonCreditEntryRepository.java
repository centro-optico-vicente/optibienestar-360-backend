package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PersonCreditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
public interface PersonCreditEntryRepository extends JpaRepository<PersonCreditEntry, Long> {

    /** Balance per currency: {@code [currency code, SUM(amount)]} rows. */
    @Query("""
            SELECT e.currency.code, SUM(e.amount) FROM PersonCreditEntry e
            WHERE e.person.id = :personId AND e.active = true
            GROUP BY e.currency.code ORDER BY e.currency.code
            """)
    List<Object[]> balancesByPerson(@Param("personId") Long personId);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM PersonCreditEntry e
            WHERE e.person.id = :personId AND e.currency.id = :currencyId AND e.active = true
            """)
    BigDecimal balanceOf(@Param("personId") Long personId, @Param("currencyId") Long currencyId);

    boolean existsByPaymentIdAndEntryType(Long paymentId, String entryType);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM PersonCreditEntry e
            WHERE e.payment.id = :paymentId AND e.active = true
            """)
    BigDecimal remainingOfPayment(@Param("paymentId") Long paymentId);
}
