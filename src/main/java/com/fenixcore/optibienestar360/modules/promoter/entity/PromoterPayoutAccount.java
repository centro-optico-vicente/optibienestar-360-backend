package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
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
 * One payout destination of an advisor (V184, dossier section V). N per
 * {@link Promoter}; the account kind comes from the configurable
 * {@link PaymentMethod} catalog and its {@code is_mandatory_*} flags decide
 * which fields are required (validated in the service). At most one active
 * row per promoter is the default (V184 partial unique index).
 *
 * <p>Used to prefill OUT payouts — the payment lines keep their own
 * snapshot, so editing an account never rewrites history.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_payout_accounts")
@AttributeOverride(name = "id", column = @Column(name = "promoter_payout_accounts_id", nullable = false, updatable = false))
public class PromoterPayoutAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    /** The methods this account accepts (V217); its required fields are the sum of their is_mandatory_* checks. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promoter_payout_account_payment_methods",
            joinColumns = @JoinColumn(name = "promoter_payout_account_id"),
            inverseJoinColumns = @JoinColumn(name = "payment_method_id"))
    private Set<PaymentMethod> paymentMethods = new HashSet<>();

    @Column(length = 60)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    /** {@code CORRIENTE} / {@code AHORRO} (V184 CHECK). */
    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "account_number", length = 34)
    private String accountNumber;

    @Column(name = "account_identifier", length = 120)
    private String accountIdentifier;

    @Column(name = "holder_name", length = 150)
    private String holderName;

    @Column(name = "holder_document_type", length = 1)
    private String holderDocumentType;

    @Column(name = "holder_document_number", length = 20)
    private String holderDocumentNumber;

    @Column(length = 30)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount = false;
}
