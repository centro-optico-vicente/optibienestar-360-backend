package com.fenixcore.optibienestar360.modules.system.dto;

import com.fenixcore.optibienestar360.core.audit.AuditMode;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigDto(
        UUID uuid,
        String reportFooter,
        AuditMode dataChangeAuditMode,
        AuditMode reportAuditMode,
        boolean loginAuditEnabled,
        Instant updatedAt
) {}
