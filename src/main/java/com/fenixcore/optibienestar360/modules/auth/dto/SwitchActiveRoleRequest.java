package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchActiveRoleRequest(
        @NotNull UUID roleUuid,
        @NotBlank String refreshToken
) {}
