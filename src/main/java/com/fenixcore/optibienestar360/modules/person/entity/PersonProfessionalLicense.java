package com.fenixcore.optibienestar360.modules.person.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.catalog.entity.LicensingBody;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Professional license of a person (V213 / hub ADR 0031 §5): number issued by
 * a {@link LicensingBody}. It belongs to the PERSON, not to an ally, so a
 * professional with several practice sites (one ally per site) registers it
 * once. Staff verifies it before it is shown publicly.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_professional_licenses")
@AttributeOverride(name = "id", column = @Column(name = "person_professional_licenses_id", nullable = false, updatable = false))
public class PersonProfessionalLicense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "licensing_body_id", nullable = false)
    private LicensingBody licensingBody;

    @Column(name = "license_number", length = 40, nullable = false)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20, nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }
}
