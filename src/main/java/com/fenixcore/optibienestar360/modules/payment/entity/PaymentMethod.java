package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payment method catalog (V115, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). FK target of
 * {@code payment_lines.payment_type_id} — the HOW money moved (cash,
 * transfer, Zelle, ...), never to be confused with {@link PaymentCategory}
 * (the header-level WHY). No {@code direction}: a method never belongs to a
 * single direction.
 *
 * <p>The {@code is_mandatory_*} flags tell the UI/backend which extra fields
 * to demand per method (bank account for transfers, phone for pago móvil,
 * email for Zelle/crypto, reference number as proof of payment) — inspired
 * by the legacy {@code tglo_METODO_PAGO} catalog, see V115 header.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_methods")
@AttributeOverride(name = "id", column = @Column(name = "payment_methods_id", nullable = false, updatable = false))
public class PaymentMethod extends BaseEntity {

    @Column(length = 40, unique = true, nullable = false)
    private String code;

    @Column(length = 80, nullable = false)
    private String name;

	@Column(length = 255)
	private String description;

	@Column(name = "is_mandatory_identification", nullable = false)
	private boolean mandatoryIdentification;

	@Column(name = "is_mandatory_bank", nullable = false)
	private boolean mandatoryBank;

    @Column(name = "is_mandatory_bank_account", nullable = false)
    private boolean mandatoryBankAccount;

	@Column(name = "is_mandatory_account_type", nullable = false)
	private boolean mandatoryAccountType;

	@Column(name = "is_mandatory_account_code", nullable = false)
	private boolean mandatoryAccountCode;

    @Column(name = "is_mandatory_phone", nullable = false)
    private boolean mandatoryPhone;

    @Column(name = "is_mandatory_email", nullable = false)
    private boolean mandatoryEmail;

    @Column(name = "is_mandatory_reference_number", nullable = false)
    private boolean mandatoryReferenceNumber;
}
