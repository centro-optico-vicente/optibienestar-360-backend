package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense.VerificationStatus;
import jakarta.validation.constraints.NotNull;

/** {@code VERIFIED} or {@code REJECTED}; {@code PENDING} is only reached by editing the license. */
public record AllyLicenseVerifyRequest(@NotNull VerificationStatus status) {}
