package com.fenixcore.optibienestar360.modules.member.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent medical profile of a {@link Person}: blood type, allergies,
 * chronic conditions, current medications, emergency contact. 1:1 with
 * Person regardless of which program role they hold — same record covers
 * them whether they're a {@link Member} titular or a {@link Beneficiary}
 * in another titular's plan.
 *
 * <p><b>PRIVACY (V19 comment + vertical-4 rule):</b> ally users (clinics,
 * pharmacies, validator) must NEVER receive this entity in an API response.
 * Enforce at the service / controller layer via {@code @PreAuthorize} or
 * dedicated DTOs that exclude it.</p>
 *
 * <p>{@code bloodType} is left as a plain String (matching the V19 CHECK
 * values 'A+'/'A-'/…) instead of an enum: the values contain {@code +}
 * and {@code -}, which are not legal Java enum identifiers, and the
 * round-trip through a {@code @Converter} would only add ceremony for no
 * real benefit. Validation lives at the DTO with a {@code @Pattern}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "medical_records",
       uniqueConstraints = @UniqueConstraint(columnNames = "person_id"))
@AttributeOverride(name = "id", column = @Column(name = "medical_records_id", nullable = false, updatable = false))
public class MedicalRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "blood_type", length = 5)
    private String bloodType;

    @Column(columnDefinition = "text")
    private String allergies;

    @Column(name = "chronic_conditions", columnDefinition = "text")
    private String chronicConditions;

    @Column(name = "current_medications", columnDefinition = "text")
    private String currentMedications;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_contact_relationship", length = 20)
    private EmergencyContactRelationship emergencyContactRelationship;

    @Column(columnDefinition = "text")
    private String notes;

    public enum EmergencyContactRelationship {
        SPOUSE, CHILD, PARENT, SIBLING, FRIEND, OTHER
    }
}
