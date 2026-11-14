package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of {@code PUT /v1/me/default-role}: the role the caller starts every session with. */
public record DefaultRoleRequest(@NotNull UUID roleUuid) {}
