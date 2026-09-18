package com.fenixcore.optibienestar360.modules.bank.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bank master catalog (V116, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Prerequisite of
 * {@code payment_lines.bank_id} for any line whose method requires a bank
 * account (transfer, international transfer, check, bank deposit).
 *
 * <p>{@code code} is the SUDEBAN clearing-house code (e.g. "0102"), the
 * stable natural key. {@code taxDocumentType}/{@code taxDocumentNumber}
 * follow the same RIF split as {@code Person}/{@code Ally}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "banks")
@AttributeOverride(name = "id", column = @Column(name = "banks_id", nullable = false, updatable = false))
public class Bank extends BaseEntity {

    @Column(length = 10, unique = true, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "short_name", length = 60, nullable = false)
    private String shortName;

    @Column(name = "tax_document_type", length = 1, nullable = false)
    private String taxDocumentType;

    @Column(name = "tax_document_number", length = 20, nullable = false)
    private String taxDocumentNumber;
}
