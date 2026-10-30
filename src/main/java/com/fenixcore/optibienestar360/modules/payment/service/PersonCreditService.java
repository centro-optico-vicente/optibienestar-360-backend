package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.payment.dto.CreditBalanceDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PersonCreditEntry;
import com.fenixcore.optibienestar360.modules.payment.entity.PersonCreditEntry.EntryType;
import com.fenixcore.optibienestar360.modules.payment.repository.PersonCreditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-person credit ledger ("saldo a favor", V201). An approved collection with no membership
 * becomes a {@code CREDIT}; assigning it to a membership books the remaining amount as a
 * negative {@code APPLIED}. Nothing here is ever updated or deleted — corrections are new rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonCreditService {

    private final PersonCreditEntryRepository repository;

    /** Books the payment's net amount as credit; a no-op when it already has a membership or a credit row. */
    @Transactional
    public void recordCreditIfUnassigned(Payment payment) {
        if (payment.getMembership() != null || !"IN".equals(payment.getDirection())) {
            return;
        }
        if (repository.existsByPaymentIdAndEntryType(payment.getId(), EntryType.CREDIT.name())) {
            return;
        }
        BigDecimal amount = payment.netAmount();
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PersonCreditEntry entry = new PersonCreditEntry();
        entry.setPerson(payment.getPerson());
        entry.setPayment(payment);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.CREDIT.name());
        entry.setAmount(amount);
        entry.setNote("Approved collection without membership");
        repository.save(entry);
    }

    /** Remaining credit of one payment (CREDIT minus what was already applied). */
    public BigDecimal remaining(Payment payment) {
        return repository.remainingOfPayment(payment.getId());
    }

    /**
     * Books the whole remaining credit of {@code payment} against {@code membership}.
     *
     * @return the amount applied
     */
    @Transactional
    public BigDecimal applyRemaining(Payment payment, Membership membership) {
        BigDecimal remaining = remaining(payment);
        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException("payment.credit.nothing_to_apply");
        }
        PersonCreditEntry entry = new PersonCreditEntry();
        entry.setPerson(payment.getPerson());
        entry.setPayment(payment);
        entry.setMembership(membership);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.APPLIED.name());
        entry.setAmount(remaining.negate());
        entry.setNote("Assigned to membership");
        repository.save(entry);
        return remaining;
    }

    public CreditBalanceDto balanceOf(Long personId) {
        List<CreditBalanceDto.Line> lines = repository.balancesByPerson(personId).stream()
                .map(row -> new CreditBalanceDto.Line((String) row[0], (BigDecimal) row[1]))
                .toList();
        return new CreditBalanceDto(lines);
    }
}
