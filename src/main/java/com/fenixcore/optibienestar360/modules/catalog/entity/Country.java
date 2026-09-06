package com.fenixcore.optibienestar360.modules.catalog.entity;

import com.fenixcore.optibienestar360.core.entity.BaseAuditEntity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "countries")
@AttributeOverride(name = "id", column = @Column(name = "countries_id", nullable = false, updatable = false))
public class Country extends BaseAuditEntity {

    @Column(name = "iso_code", length = 2, unique = true, nullable = false)
    private String isoCode;

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Column(length = 10)
    private String locale;

    /**
     * Metadata describing the country's own official currency (V96) — nullable,
     * seeded only for Venezuela. Does NOT replace
     * {@code organizations.official_currency_id}, which stays the single-tenant
     * conversion target for the rest of the platform.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "official_currency_id")
    private Currency officialCurrency;
}
