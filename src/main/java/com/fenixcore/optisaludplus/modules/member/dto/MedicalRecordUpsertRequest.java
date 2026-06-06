package com.fenixcore.optisaludplus.modules.member.dto;

import com.fenixcore.optisaludplus.modules.member.entity.MedicalRecord.EmergencyContactRelationship;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /v1/admin/members/{memberUuid}/medical-record}.
 *
 * <p>Upsert semantics — the same payload creates the record when none
 * exists for the person and updates it otherwise. PATCH within update:
 * non-null fields are applied; null fields keep their current value (or
 * stay {@code null} on create).</p>
 *
 * <p>{@code bloodType} is validated against the same canonical short forms
 * as the V19 CHECK constraint ('A+'/'A-'/'B+'/'B-'/'AB+'/'AB-'/'O+'/'O-').
 * Empty strings would otherwise sneak past — explicit pattern keeps the
 * server from persisting blank or whitespace strings.</p>
 */
public record MedicalRecordUpsertRequest(
        @Pattern(regexp = "^(A|B|AB|O)[+\\-]$", message = "{validation.blood_type.format}")
        @Size(max = 5)
        String bloodType,

        String allergies,
        String chronicConditions,
        String currentMedications,

        @Size(max = 200) String emergencyContactName,
        @Size(max = 30) String emergencyContactPhone,
        EmergencyContactRelationship emergencyContactRelationship,

        String notes
) {}
