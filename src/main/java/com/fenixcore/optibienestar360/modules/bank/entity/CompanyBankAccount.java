package com.fenixcore.optibienestar360.modules.bank.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
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

import java.util.HashSet;
import java.util.Set;

/**
 * A bank account owned by the company (V201): where affiliates deposit. A payer
 * picks one on each payment line to say where they paid.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_bank_accounts")
@AttributeOverride(name = "id", column = @Column(name = "company_bank_accounts_id", nullable = false, updatable = false))
public class CompanyBankAccount extends BaseEntity {

    /**
     * The methods this account accepts (V217). What it must hold is the sum of the is_mandatory_* checks of all of
     * them, validated by the service.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_bank_account_payment_methods",
            joinColumns = @JoinColumn(name = "company_bank_account_id"),
            inverseJoinColumns = @JoinColumn(name = "payment_method_id"))
    private Set<PaymentMethod> paymentMethods = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "account_type", length = 40)
    private String accountType;

    @Column(name = "account_number", length = 40)
    private String accountNumber;

    @Column(name = "holder_name", length = 160, nullable = false)
    private String holderName;

    @Column(name = "holder_document", length = 30)
    private String holderDocument;

    @Column(length = 255)
    private String notes;

    /** Phone of the account, e.g. Pago Móvil (V206). */
    @Column(length = 30)
    private String phone;

    /** Email of the account, e.g. Zelle (V206). */
    @Column(length = 160)
    private String email;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** {@code true} = shown without authentication on {@code GET /v1/public/company-bank-accounts} (V204). */
    @Column(name = "is_public", nullable = false)
    private boolean publicVisible;
}
