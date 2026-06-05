package com.fenixcore.optisaludplus.modules.ally.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Formal contract between the platform and an {@link Ally}. Captures
 * commercial terms, exclusivity, validity period and the signed PDF.
 *
 * <p>The {@code status} (DRAFT / ACTIVE / EXPIRED / TERMINATED) is provided
 * by {@link BaseEntity#getStatus()} — it's the same {@code status
 * VARCHAR(50)} column with a CHECK constraint on the DB side, so the
 * entity does not redeclare it.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_agreements")
@AttributeOverride(name = "id", column = @Column(name = "ally_agreements_id", nullable = false, updatable = false))
public class AllyAgreement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", length = 50, nullable = false)
    private AgreementType agreementType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String terms;

    @Column(name = "signed_pdf_url", length = 500)
    private String signedPdfUrl;

    public enum AgreementType {
        COMMERCIAL, MEDICAL, EXCLUSIVITY, SUPPLY
    }
}
