package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record AdminUpdateUserRequest(
        String fullName,
        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        String documentNumber,
        String phone,
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|LOCKED)$",
                 message = "{validation.user_status.allowed_values}") String status,
        Boolean active,
        List<UUID> roleIds
) {}
