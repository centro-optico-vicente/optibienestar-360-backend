package com.fenixcore.optibienestar360.modules.system.dto;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigDto(
        UUID uuid,
        String reportFooter,
        Instant updatedAt
) {}
