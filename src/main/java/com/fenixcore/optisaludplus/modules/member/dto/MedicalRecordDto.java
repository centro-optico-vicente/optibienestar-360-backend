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
 * <p><b>Empty-record contract:</b> {@code GET} always returns 200 with a
 * DTO. When the member has no filled-in medical record yet (or the row was
 * soft-deleted), the service returns an <i>empty</i> instance:
 * {@code uuid=null}, {@code personUuid=<known>}, all clinical fields
 * {@code null}, {@code active=true}, timestamps {@code null}, and the
 * {@code exists} flag set to {@code false}. Frontends should key their
 * "empty state vs populated form" branch on {@code exists} rather than on
 * HTTP status — 404 is reserved for a member UUID that does not resolve.</p>
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
        Instant updatedAt,

        boolean exists
) {}
