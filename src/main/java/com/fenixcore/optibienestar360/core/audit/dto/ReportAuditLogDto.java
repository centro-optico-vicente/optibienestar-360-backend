package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for one {@code report_audit_log} row (GET /v1/admin/audit/reports).
 * {@code entityId}/{@code actorId}/{@code loginAuditLogId}/{@code attachedFileId}
 * (internal BIGINT ids) never cross the API boundary (ADR 0006) — {@code actorUuid}
 * is resolved from {@code actorId}, {@code fileUuid} from {@code attachedFileId} and
 * {@code sessionUuid} from {@code loginAuditLogId} at read time. {@code sessionUuid}
 * is {@code null} for reports generated outside a login session (e.g. a scheduled
 * job) — the frontend only renders the "ver sesión" link when it's present,
 * filtering {@code GET /v1/admin/audit/logins} by it.
 *
 * <p>{@code entityDisplay} and {@code actor_Display} are pre-resolved by
 * {@link com.fenixcore.optibienestar360.core.audit.AuditDisplayResolver};
 * {@code generatedAt} gets its {@code _Display} from the shared mechanism
 * (hub ADR 0014).</p>
 */
public record ReportAuditLogDto(
        UUID uuid,
        String reportType,
        String entityKey,
        UUID entityUuid,
        String entityDisplay,
        String entityIdentifier,
        String format,
        Map<String, Object> parametersJson,
        UUID actorUuid,
        String actor_Display,
        UUID sessionUuid,
        UUID fileUuid,
        String fileName,
        Long sizeBytes,
        @Display(Display.Kind.DATETIME) Instant generatedAt
) {}
