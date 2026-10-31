package com.fenixcore.optibienestar360.modules.bank.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "account_type", length = 40, nullable = false)
    private String accountType;

    @Column(name = "account_number", length = 40, nullable = false)
    private String accountNumber;

    @Column(name = "holder_name", length = 160, nullable = false)
    private String holderName;

    @Column(name = "holder_document", length = 30, nullable = false)
    private String holderDocument;

    @Column(length = 255)
    private String notes;

    /** Pago Móvil phone (V206); required when {@code accountType} is {@code PAGO_MOVIL}. */
    @Column(name = "mobile_payment_phone", length = 30)
    private String mobilePaymentPhone;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** {@code true} = shown without authentication on {@code GET /v1/public/company-bank-accounts} (V204). */
    @Column(name = "is_public", nullable = false)
    private boolean publicVisible;
}
