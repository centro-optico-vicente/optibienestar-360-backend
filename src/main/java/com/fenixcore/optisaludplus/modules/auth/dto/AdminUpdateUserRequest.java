package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record AdminUpdateUserRequest(
        String fullName,
        @Pattern(regexp = "^[VE]$", message = "documentType must be 'V' or 'E'") String documentType,
        String documentNumber,
        String phone,
        String status,
        Boolean active,
        List<UUID> roleIds
) {}
