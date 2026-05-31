package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record AdminUpdateUserRequest(
        String fullName,
        @Pattern(regexp = "^[VE]$", message = "documentType must be 'V' or 'E'") String documentType,
        String documentNumber,
        String phone,
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|LOCKED)$",
                 message = "status must be 'ACTIVE', 'SUSPENDED' or 'LOCKED'") String status,
        Boolean active,
        List<UUID> roleIds
) {}
