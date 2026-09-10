package com.fenixcore.optibienestar360.core.audit.dto;

import com.fenixcore.optibienestar360.core.audit.AuditAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read model for one {@code data_change_audit_log} row (GET /v1/admin/audit/data-changes).
 * Internal BIGINT ids ({@code entityId}, {@code actorId}, {@code loginAuditLogId},
 * {@code restoredFromId}) never cross the API boundary (ADR 0006) — {@code actorUuid}
 * is resolved from {@code actorId} and {@code sessionUuid} from {@code loginAuditLogId}
 * at read time; the rest stay server-side. {@code sessionUuid} is {@code null} when the
 * change was system-generated (no login session, e.g. a scheduled job) — the frontend
 * only renders the "ver sesión" link when it's present, filtering
 * {@code GET /v1/admin/audit/logins} by it.
 *
 * <p>{@code entityDisplay}, {@code actor_Display} and {@code action_Display} exist so the
 * frontend doesn't have to show a wall of uuids/timestamps: they're the
 * human-readable counterparts of {@code entityUuid}, {@code actorUuid} and
 * {@code action}, resolved by {@link com.fenixcore.optibienestar360.core.audit.AuditDisplayResolver}
 * (best-effort — {@code null} when the referenced record was hard-deleted or the
 * entity type isn't wired into the resolver yet). Likewise, {@code beforeJson}/
 * {@code afterJson} get a {@code <field>_Display} sibling added next to every
 * value this can humanize (e.g. {@code cityUuid} → also {@code cityUuid_Display})
 * — the original key is untouched.</p>
 */
public record DataChangeAuditLogDto(
        UUID uuid,
        String entityKey,
        UUID entityUuid,
        String entityDisplay,
        AuditAction action,
        String action_Display,
        Map<String, Object> beforeJson,
        Map<String, Object> afterJson,
        UUID actorUuid,
        String actor_Display,
        UUID sessionUuid,
        String requestMethod,
        String requestPath,
        Instant occurredAt
) {}
