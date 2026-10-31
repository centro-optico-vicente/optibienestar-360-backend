package com.fenixcore.optibienestar360.modules.patientaccess.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/** One read of the member's medical history by a medical ally (hub ADR 0032). */
public record MedicalRecordAccessDto(
        UUID uuid,
        String doctorFullName,
        UUID allyUuid,
        String allyName,
        @Display(Display.Kind.DATETIME) Instant accessedAt
) {}
