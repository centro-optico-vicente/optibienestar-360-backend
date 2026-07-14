package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;

import java.time.LocalDate;

/**
 * Payload for {@code PUT /v1/admin/allies/{allyUuid}/users/{uuid}}. PATCH
 * semantics — non-null fields applied. The user binding itself
 * ({@code userUuid}) is immutable — to move a membership, delete + create.
 */
public record AllyUserUpdateRequest(
        AllyRole allyRole,
        Boolean primary,
        LocalDate joinedAt,
        Boolean active
) {}
