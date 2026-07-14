package com.fenixcore.optibienestar360.modules.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flattened view of User + its Person. Returned by all admin and /me
 * endpoints. The four name parts are exposed alongside the derived
 * {@code fullName} so the frontend can use either depending on the surface
 * (form fields vs label).
 */
public record UserDto(
        UUID uuid,
        String email,

        // Person — name parts + composed derivative
        String firstName,
        String middleName,
        String lastName,
        String secondLastName,
        String fullName,

        // Person — documents
        String documentType,
        String documentNumber,
        String taxDocumentType,
        String taxDocumentNumber,

        // Person — contact
        String phone,
        String locale,

        // Auth
        String status,
        boolean active,
        Instant lastLoginAt,

        List<RoleDto> roles
) {}
