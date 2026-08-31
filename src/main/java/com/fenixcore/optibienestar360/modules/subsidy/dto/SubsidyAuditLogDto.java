package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog.Action;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Output line for {@code GET /v1/admin/subsidies/{uuid}/log} — one audited
 * operation with its before/after snapshot. {@code at} is the entry's
 * {@code createdAt}.
 */
public record SubsidyAuditLogDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) Action action,
        UUID actorUuid,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason,
        @Display(Display.Kind.DATETIME) Instant at
) {
    public static SubsidyAuditLogDto from(SubsidyAuditLog log) {
        return new SubsidyAuditLogDto(
                log.getUuid(),
                log.getAction(),
                log.getActor() != null ? log.getActor().getUuid() : null,
                log.getBefore(),
                log.getAfter(),
                log.getReason(),
                log.getCreatedAt());
    }
}
