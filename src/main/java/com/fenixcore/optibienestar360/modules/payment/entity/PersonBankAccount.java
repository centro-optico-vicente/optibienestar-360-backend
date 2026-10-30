package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
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

import java.time.Instant;

/**
 * An origin bank account a person chose to remember (V204), so the next payment can reuse it instead of
 * asking for every origin field again. Private to its person.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_bank_accounts")
@AttributeOverride(name = "id", column = @Column(name = "person_bank_accounts_id", nullable = false, updatable = false))
public class PersonBankAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "account_type", length = 40)
    private String accountType;

    @Column(name = "account_code", length = 40)
    private String accountCode;

    @Column(name = "account_identifier", length = 120, nullable = false)
    private String accountIdentifier;

    /** Document of the account holder, as typed on the payment line. */
    @Column(length = 80)
    private String identification;

    @Column(length = 80)
    private String alias;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();
}
