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
        // Credited to the beneficiary when somebody else paid for them (V203), otherwise to the payer.
        entry.setPerson(ownerOf(payment));
        entry.setPayment(payment);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.CREDIT.name());
        entry.setAmount(amount);
        entry.setNote("Approved collection without membership");
        repository.save(entry);
    }

    /** Whether the payment ever held credit (has any ledger row). */
    public boolean hasLedger(Payment payment) {
        return payment.getId() != null && repository.existsByPaymentId(payment.getId());
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
        entry.setPerson(ownerOf(payment));
        entry.setPayment(payment);
        entry.setMembership(membership);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.APPLIED.name());
        entry.setAmount(remaining.negate());
        entry.setNote("Assigned to membership");
        repository.save(entry);
        return remaining;
    }

    /** Whom a payment's credit belongs to: the beneficiary when somebody else paid, otherwise the payer. */
    public static com.fenixcore.optibienestar360.modules.person.entity.Person ownerOf(Payment payment) {
        return payment.getBeneficiaryPerson() != null ? payment.getBeneficiaryPerson() : payment.getPerson();
    }

    /**
     * Books the credit one automatic application consumed: a negative {@code APPLIED} row in the payment's own
     * currency, tied to the covered charge, with the conversion snapshot (charge amount in its currency, rate used,
     * rate date) when the charge is in another currency (ADR 0015 section 5).
     */
    @Transactional
    public void applyForCharge(Payment payment, Membership membership,
                               com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge charge,
                               BigDecimal creditCost, java.math.BigDecimal rate, java.time.LocalDate rateDate) {
        PersonCreditEntry entry = new PersonCreditEntry();
        entry.setPerson(ownerOf(payment));
        entry.setPayment(payment);
        entry.setMembership(membership);
        entry.setCharge(charge);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.APPLIED.name());
        entry.setAmount(creditCost.negate());
        entry.setConvertedAmount(charge.getAmount());
        entry.setConvertedCurrency(charge.getCurrency());
        entry.setExchangeRateUsed(rate);
        entry.setExchangeRateDate(rateDate);
        entry.setNote("Applied automatically to the charge of " + charge.getPeriodStart());
        repository.save(entry);
    }

    /** Books a refund agreed on a claim: a negative {@code REVERSED} row. The transfer itself is finance's, outside the system. */
    @Transactional
    public void recordReversal(Payment payment, BigDecimal amount, String note) {
        PersonCreditEntry entry = new PersonCreditEntry();
        entry.setPerson(ownerOf(payment));
        entry.setPayment(payment);
        entry.setCurrency(payment.getCurrency());
        entry.setEntryType(EntryType.REVERSED.name());
        entry.setAmount(amount.negate());
        entry.setNote(note);
        repository.save(entry);
    }

    public CreditBalanceDto balanceOf(Long personId) {
        List<CreditBalanceDto.Line> lines = repository.balancesByPerson(personId).stream()
                .map(row -> new CreditBalanceDto.Line((String) row[0], (BigDecimal) row[1]))
                .toList();
        return new CreditBalanceDto(lines);
    }
}
