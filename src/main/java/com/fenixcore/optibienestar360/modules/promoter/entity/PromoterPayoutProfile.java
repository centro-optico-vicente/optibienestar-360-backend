package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Advisor payout destination (V180, dossier section V "Datos financieros
 * para liquidación"). 1:1 with {@link Promoter} via UNIQUE(promoter_id).
 * Used as the default source to prefill OUT payouts (commission payouts)
 * instead of re-typing the bank data on every payment — the payment lines
 * keep their own snapshot, so editing this profile never rewrites history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_payout_profiles")
@AttributeOverride(name = "id", column = @Column(name = "promoter_payout_profiles_id", nullable = false, updatable = false))
public class PromoterPayoutProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false, unique = true)
    private Promoter promoter;

    // ─── National bank account ─────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    /** 20-digit Venezuelan account number (V180 CHECK {@code ^[0-9]{20}$}). */
    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "account_holder_document_type", length = 1)
    private String accountHolderDocumentType;

    @Column(name = "account_holder_document_number", length = 20)
    private String accountHolderDocumentNumber;

    // ─── Pago Móvil ────────────────────────────────────────────────────────

    @Column(name = "mobile_payment_phone", length = 30)
    private String mobilePaymentPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mobile_payment_bank_id")
    private Bank mobilePaymentBank;

    // ─── Ubii ──────────────────────────────────────────────────────────────

    @Column(name = "ubii_account", length = 60)
    private String ubiiAccount;

    @Column(name = "ubii_holder_name", length = 150)
    private String ubiiHolderName;
}
