package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * An origin bank account a person chose to remember (V204), so the next payment can reuse it instead of
 * asking for every origin field again. Private to its person.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payer_bank_accounts")
@AttributeOverride(name = "id", column = @Column(name = "payer_bank_accounts_id", nullable = false, updatable = false))
public class PayerBankAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /** The methods this account accepts (V218); its required fields are the sum of their is_mandatory_* checks. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "payer_bank_account_payment_methods",
            joinColumns = @JoinColumn(name = "payer_bank_account_id"),
            inverseJoinColumns = @JoinColumn(name = "payment_method_id"))
    private Set<PaymentMethod> paymentMethods = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(name = "account_type", length = 40)
    private String accountType;

    @Column(name = "account_code", length = 40)
    private String accountCode;

    @Column(name = "account_identifier", length = 120)
    private String accountIdentifier;

    /** Phone of the account, e.g. Pago Móvil (V207). */
    @Column(length = 30)
    private String phone;

    /** Email of the account, e.g. Zelle (V207). */
    @Column(length = 160)
    private String email;

    /** Document of the account holder, as typed on the payment line. */
    @Column(length = 80)
    private String identification;

    @Column(length = 80)
    private String alias;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();
}
