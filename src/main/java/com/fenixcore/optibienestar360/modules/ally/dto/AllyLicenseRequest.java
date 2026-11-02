package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create / full-update payload of a holder person's license (V213). Changing
 * the body or the number sends the license back to {@code PENDING}.
 */
public record AllyLicenseRequest(
        @NotNull UUID licensingBodyUuid,
        @NotBlank @Size(max = 40) String licenseNumber
) {}
