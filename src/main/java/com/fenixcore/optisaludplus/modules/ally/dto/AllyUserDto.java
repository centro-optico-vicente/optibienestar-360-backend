package com.fenixcore.optisaludplus.modules.ally.dto;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyUser.AllyRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for the membership listings under
 * {@code /v1/admin/allies/{allyUuid}/users}. Flattens the linked
 * {@link com.fenixcore.optisaludplus.modules.auth.entity.User} +
 * {@link com.fenixcore.optisaludplus.modules.person.entity.Person} into
 * a few read-only fields (uuid, email, fullName) so the table renders
 * without round-trips. Editable fields ({@code allyRole},
 * {@code primary}) go through the dedicated PUT.
 */
public record AllyUserDto(
        UUID uuid,
        UUID allyUuid,

        // User + Person flattened for display
        UUID userUuid,
        String userEmail,
        String userFullName,

        AllyRole allyRole,
        boolean primary,
        LocalDate joinedAt,

        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
