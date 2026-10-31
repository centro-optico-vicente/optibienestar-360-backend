package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Professional license of the ally's holder person (V213), served at
 * {@code /v1/admin/allies/{allyUuid}/licenses}. {@code licensingBody} follows
 * the FK convention (ADR 0014): {@code licensingBody_Uuid} + {@code _Display} + {@code _Code}.
 */
public record AllyLicenseDto(
        UUID uuid,
        UUID personUuid,
        @Display DisplayRef licensingBody,
        String licenseNumber,
        @Display(value = Display.Kind.ENUM, enumScope = "person_license.verification_status") VerificationStatus verificationStatus,
        UUID verifiedBy,
        @Display(Display.Kind.DATETIME) Instant verifiedAt,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
