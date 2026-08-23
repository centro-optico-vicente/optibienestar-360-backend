package com.fenixcore.optibienestar360.core.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for one {@code report_audit_log} row (GET /v1/admin/audit/reports).
 * {@code entityId}/{@code actorId}/{@code loginAuditLogId}/{@code attachedFileId}
 * (internal BIGINT ids) never cross the API boundary (ADR 0006) — {@code actorUuid}
 * is resolved from {@code actorId} and {@code fileUuid} from {@code attachedFileId}
 * at read time.
 *
 * <p>{@code entityDisplay} and {@code actor_Display} follow the same
 * humanized-value convention as {@link DataChangeAuditLogDto}, resolved via
 * {@link com.fenixcore.optibienestar360.core.audit.AuditDisplayResolver}.</p>
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
        UUID fileUuid,
        String fileName,
        Long sizeBytes,
        Instant generatedAt
) {}
