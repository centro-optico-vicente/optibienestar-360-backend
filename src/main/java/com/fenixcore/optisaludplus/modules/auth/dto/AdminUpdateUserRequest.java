package com.fenixcore.optisaludplus.modules.auth.dto;

import java.util.List;
import java.util.UUID;

public record AdminUpdateUserRequest(
        String fullName,
        String documentType,
        String documentNumber,
        String phone,
        String status,
        Boolean active,
        List<UUID> roleIds
) {}
