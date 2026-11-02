package com.fenixcore.optibienestar360.modules.patientaccess.dto;

import java.util.List;

/**
 * Response of {@code GET /v1/me/medical-record/access} and of the staff
 * audit view: who can read the member's medical history (links, active and
 * revoked) and who read it (latest reads first).
 */
public record MyMedicalRecordAccessDto(
        List<AllyPatientLinkDto> links,
        List<MedicalRecordAccessDto> reads
) {}
