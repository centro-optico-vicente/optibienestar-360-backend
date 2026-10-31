package com.fenixcore.optibienestar360.modules.patientaccess.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/**
 * A doctor–patient link (hub ADR 0032), as both sides see it: the ally lists
 * its patients, the member lists who can read their history.
 */
public record AllyPatientLinkDto(
        UUID uuid,
        UUID memberUuid,
        String memberFullName,
        UUID doctorPersonUuid,
        String doctorFullName,
        UUID allyUuid,
        String allyName,
        @Display(value = Display.Kind.ENUM, enumScope = "patient_link.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant revokedAt
) {}
