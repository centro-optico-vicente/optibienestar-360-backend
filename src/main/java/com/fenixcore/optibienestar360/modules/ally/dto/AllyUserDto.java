package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for the membership listings under
 * {@code /v1/admin/allies/{allyUuid}/users}. Flattens the linked
 * {@link com.fenixcore.optibienestar360.modules.auth.entity.User} +
 * {@link com.fenixcore.optibienestar360.modules.person.entity.Person} into
 * a few read-only fields (uuid, email, fullName) so the table renders
 * without round-trips. Editable fields ({@code allyRole},
 * {@code primary}) go through the dedicated PUT.
 *
 * <p>Presentational scalars carry a localized {@code _Display} sibling
 * (hub ADR 0014).</p>
 */
public record AllyUserDto(
        UUID uuid,
        UUID allyUuid,

        // User + Person flattened for display
        UUID userUuid,
        String userEmail,
        String userFullName,

        @Display(Display.Kind.ENUM) AllyRole allyRole,
        @Display(Display.Kind.BOOLEAN) boolean primary,
        @Display(Display.Kind.DATE) LocalDate joinedAt,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "user.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
