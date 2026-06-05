package com.fenixcore.optisaludplus.modules.ally.dto;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyUser.AllyRole;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/allies/{allyUuid}/users}. Attaches an
 * existing User (resolved by {@code userUuid}) to the ally with the given
 * {@code allyRole} and optional {@code primary} flag.
 *
 * <p>If a membership already exists (active or inactive) for the same
 * (ally, user) pair, the service reactivates the existing row instead of
 * inserting a duplicate — required because V12 has UNIQUE(ally_id,
 * user_id).</p>
 *
 * <p>Setting {@code primary=true} is rejected if the user's {@code allyRole}
 * is not OWNER (DB CHECK {@code chk_ally_users_primary_is_owner}) and if
 * another active OWNER is already primary for the ally (partial unique
 * index {@code uniq_ally_users_one_primary_per_ally}). The service can
 * either reject the second primary or auto-transfer (TBD per UX call —
 * current implementation rejects with 422).</p>
 */
public record AllyUserCreateRequest(
        @NotNull UUID userUuid,
        @NotNull AllyRole allyRole,
        Boolean primary,
        LocalDate joinedAt
) {}
