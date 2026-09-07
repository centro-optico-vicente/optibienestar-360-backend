package com.fenixcore.optibienestar360.modules.organization.entity;

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
 * The organization/company master (V86, ADR 0015 §4). Single row today
 * (OptiBienestar 360 / Centro Óptico Vicente — V99), shaped to survive an
 * eventual multi-tenant future without rework.
 *
 * <p>{@link #officialCurrency} is the country's legal tender (VES) —
 * facturación/reportes fiscales; {@link #referenceCurrency} is the
 * pricing/quoting currency (USD, per ADR 0008) plans/commissions/prizes are
 * denominated in. Callers resolving "the other currency" when none is given
 * (e.g. converting a USD bonus for display) fall back to
 * {@link #officialCurrency}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organizations")
@AttributeOverride(name = "id", column = @Column(name = "organizations_id", nullable = false, updatable = false))
public class Organization extends BaseEntity {

    @Column(length = 150, nullable = false)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "tax_identifier", length = 20)
    private String taxIdentifier;

    @Column(name = "logo_key", length = 255)
    private String logoKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "official_currency_id", nullable = false)
    private Currency officialCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reference_currency_id", nullable = false)
    private Currency referenceCurrency;
}
