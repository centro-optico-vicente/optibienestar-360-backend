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

    /**
     * Payments of a person that still hold credit: {@code [payment id, remaining amount]} rows with a positive
     * remainder, oldest payment first. Keyed by the ledger (not by {@code payment.membership}) because a payment
     * may be partly applied to a membership and still keep a remainder.
     */
    @Query("""
            SELECT e.payment.id, SUM(e.amount) FROM PersonCreditEntry e
            WHERE e.person.id = :personId AND e.active = true
            GROUP BY e.payment.id HAVING SUM(e.amount) > 0
            ORDER BY e.payment.id
            """)
    List<Object[]> paymentsWithCredit(@Param("personId") Long personId);

    /** People with a positive balance in at least one currency (a person can repeat when they hold several). */
    @Query("""
            SELECT e.person.id FROM PersonCreditEntry e
            WHERE e.active = true
            GROUP BY e.person.id, e.currency.id HAVING SUM(e.amount) > 0
            """)
    List<Long> personIdsWithCredit();

    /** Whether the payment has any credit-ledger row at all (i.e. it was ever credit). */
    boolean existsByPaymentId(Long paymentId);

    boolean existsByPaymentIdAndEntryType(Long paymentId, String entryType);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM PersonCreditEntry e
            WHERE e.payment.id = :paymentId AND e.active = true
            """)
    BigDecimal remainingOfPayment(@Param("paymentId") Long paymentId);
}
