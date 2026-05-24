package com.fenixcore.optisaludplus.modules.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID uuid,
        String email,
        String fullName,
        String documentType,
        String documentNumber,
        String phone,
        String status,
        boolean active,
        Instant lastLoginAt,
        List<RoleDto> roles
) {}
