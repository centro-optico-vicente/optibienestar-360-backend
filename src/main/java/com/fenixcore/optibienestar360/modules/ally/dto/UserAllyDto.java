package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/users/{userUuid}/allies} — "which
 * allies is this user staff of?". Mirrors {@link MyAllyDto} (same flattened
 * shape: identity from the parent {@link
 * com.fenixcore.optibienestar360.modules.ally.entity.Ally Ally}, membership
 * fields from the {@link com.fenixcore.optibienestar360.modules.ally.entity.AllyUser
 * AllyUser} pivot) but for the admin reverse-lookup instead of the
 * self-service {@code /v1/me/allies}.
 *
 * <p>Exists so the user detail page in the admin panel can show a user's
 * ally memberships without an N+1 walk over every ally's staff list — the
 * reverse of {@link AllyUserDto}, which flattens user info onto the ally
 * side.</p>
 */
public record UserAllyDto(
        UUID allyUuid,
        String allyName,

        AllyRole allyRole,
        boolean primary,
        LocalDate joinedAt,
        boolean active
) {}
