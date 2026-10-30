package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One movement of a person's credit balance ("saldo a favor", V201). Append-only: an approved
 * IN payment with no membership adds a {@code CREDIT}; assigning it to a membership adds a
 * negative {@code APPLIED}. The balance is {@code SUM(amount)} per person and currency.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_credit_entries")
@AttributeOverride(name = "id", column = @Column(name = "person_credit_entries_id", nullable = false, updatable = false))
public class PersonCreditEntry extends BaseEntity {

    public enum EntryType { CREDIT, APPLIED, REVERSED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "entry_type", length = 20, nullable = false)
    private String entryType;

    /** Signed: positive for {@code CREDIT}, negative otherwise (DB CHECK). */
    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 255)
    private String note;
}
