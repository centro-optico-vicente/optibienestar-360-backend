package com.fenixcore.optibienestar360.modules.currency.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Currency master (V84, ADR 0015). Replaces the free {@code VARCHAR(3)}
 * currency columns scattered across payments/commissions/plans/etc. with a
 * real catalog — other tables' FKs point at {@code currencies_id}, never at
 * {@code code} directly.
 *
 * <p>{@code code} is the stable natural key ({@code USD}, {@code VES},
 * {@code EUR}) used by service-level lookups resolving a hardcoded ISO
 * currency string into the entity.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "currencies")
@AttributeOverride(name = "id", column = @Column(name = "currencies_id", nullable = false, updatable = false))
public class Currency extends BaseEntity {

    @Column(length = 4, unique = true, nullable = false)
    private String code;

    @Column(length = 60, nullable = false)
    private String name;

    @Column(length = 6, nullable = false)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private Short decimalPlaces;
}
