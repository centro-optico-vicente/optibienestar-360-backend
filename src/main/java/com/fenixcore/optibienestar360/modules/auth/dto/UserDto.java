package com.fenixcore.optibienestar360.modules.auth.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Flattened view of User + its Person. Returned by all admin and /me
 * endpoints — an edit-response shape, so the person name parts and the
 * nested {@code roles} stay as-is. Only the presentational scalars gain a
 * localized {@code _Display} sibling (hub ADR 0014).
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
        @Display(value = Display.Kind.ENUM, enumScope = "user.status") String status,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant lastLoginAt,

        List<RoleDto> roles
) {}
