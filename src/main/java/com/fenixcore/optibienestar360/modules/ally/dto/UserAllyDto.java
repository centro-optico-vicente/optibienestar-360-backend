package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;

import java.time.LocalDate;

/**
 * Output DTO for {@code GET /v1/admin/users/{userUuid}/allies} — "which
 * allies is this user staff of?". Mirrors {@link MyAllyDto} (same flattened
 * shape: identity from the parent {@link
 * com.fenixcore.optibienestar360.modules.ally.entity.Ally Ally}, membership
 * fields from the {@link com.fenixcore.optibienestar360.modules.ally.entity.AllyUser
 * AllyUser} pivot) but for the admin reverse-lookup instead of the
 * self-service {@code /v1/me/allies}.
 *
 * <p>{@code ally} serializes as {@code ally_Uuid} + {@code ally_Display};
 * membership scalars carry their {@code _Display} sibling (hub ADR 0014).</p>
 */
public record UserAllyDto(
        @Display DisplayRef ally,

        @Display(Display.Kind.ENUM) AllyRole allyRole,
        @Display(Display.Kind.BOOLEAN) boolean primary,
        @Display(Display.Kind.DATE) LocalDate joinedAt,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}
