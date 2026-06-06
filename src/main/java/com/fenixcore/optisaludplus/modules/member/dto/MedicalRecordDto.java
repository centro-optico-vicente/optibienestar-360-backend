package com.fenixcore.optisaludplus.modules.member.dto;

import com.fenixcore.optisaludplus.modules.member.entity.MedicalRecord.EmergencyContactRelationship;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/members/{memberUuid}/medical-record}.
 *
 * <p>Carries the Person's medical profile (the record is 1:1 with persons,
 * not members — same data covers them whether they're a titular or a
 * beneficiary). {@code personUuid} is exposed so the frontend can correlate
 * with other person-scoped surfaces if needed.</p>
 *
 * <p><b>PRIVACY:</b> never serialize this DTO from ally-facing
 * controllers — enforce at the route layer via the
 * {@code MEDICAL_RECORD_VIEW} permission, which the V6 seed grants only to
 * SYSTEM / ADMINISTRADOR / OPERADOR_MEDICO roles.</p>
 */
public record MedicalRecordDto(
        UUID uuid,
        UUID personUuid,

        String bloodType,
        String allergies,
        String chronicConditions,
        String currentMedications,

        String emergencyContactName,
        String emergencyContactPhone,
        EmergencyContactRelationship emergencyContactRelationship,

        String notes,

        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
