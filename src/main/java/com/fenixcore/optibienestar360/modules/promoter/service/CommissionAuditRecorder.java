package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.mapper.CommissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Explicit (non-AOP) audit recorder for {@code Commission} mutations.
 *
 * <p>{@link com.fenixcore.optibienestar360.core.audit.DataChangeAuditAspect}
 * is built for a single-record, uuid-arg CRUD method (same shape as
 * {@code CommissionTiersService}) — it doesn't fit any of the three places a
 * {@link Commission} row actually changes:</p>
 * <ul>
 *   <li>{@link CommissionService#calculateAndPersistFor} — a fire-and-forget
 *       side effect of payment approval, returning {@code Optional<Commission>}
 *       (not a DTO the aspect's reflective {@code extractUuid} can read).</li>
 *   <li>{@link CommissionPayoutService#execute} / {@link CommissionReRatingService#execute}
 *       — one call mutates a whole period's worth of rows across many
 *       promoters; the aspect only ever snapshots one entity per invocation.</li>
 * </ul>
 * <p>Same explicit-call precedent as {@code ReportAuditService} for the
 * analogous "doesn't fit the generic CRUD shape" reason. Never blocks the
 * business operation — every failure here is caught and logged, same
 * fail-safe policy as the aspect (spec 16-audit.md §Aspecto AOP).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionAuditRecorder {

    private static final String ENTITY_KEY = "commission";

    private final EntityConfigService entityConfigService;
    private final AuditContextResolver auditContextResolver;
    private final DataChangeAuditWriter dataChangeAuditWriter;
    private final CommissionMapper commissionMapper;
    private final ObjectMapper objectMapper;

    /** Snapshot of a commission's current state, for a caller that needs a "before" ahead of mutating it. */
    public Map<String, Object> snapshot(Commission commission) {
        return toJsonMap(commissionMapper.toDto(commission));
    }

    /** Records the creation of {@code commission} (no "before" — it didn't exist). */
    public void recordCreate(Commission commission) {
        if (!safely(() -> entityConfigService.isEnabled(ENTITY_KEY, AuditAction.CREATE), false)) {
            return;
        }
        try {
            Map<String, Object> after = captureBeforeAfter() ? snapshot(commission) : null;
            dataChangeAuditWriter.write(ENTITY_KEY, commission.getUuid(), AuditAction.CREATE, null, after, actorId(), null);
        }
        catch (Exception e) {
            log.warn("Failed to persist data_change_audit_log for commission {} (CREATE) — business operation already completed",
                    commission.getUuid(), e);
        }
    }

    /**
     * Records an update to the commission {@code uuid}. {@code before}/{@code after}
     * should come from {@link #snapshot} taken right before and right after the
     * mutation — the caller owns that timing since this class never touches the
     * entity itself.
     */
    public void recordUpdate(UUID uuid, Map<String, Object> before, Map<String, Object> after) {
        if (!safely(() -> entityConfigService.isEnabled(ENTITY_KEY, AuditAction.UPDATE), false)) {
            return;
        }
        try {
            boolean capture = captureBeforeAfter();
            dataChangeAuditWriter.write(ENTITY_KEY, uuid, AuditAction.UPDATE,
                    capture ? before : null, capture ? after : null, actorId(), null);
        }
        catch (Exception e) {
            log.warn("Failed to persist data_change_audit_log for commission {} (UPDATE) — business operation already completed",
                    uuid, e);
        }
    }

    private boolean captureBeforeAfter() {
        return safely(() -> entityConfigService.captureBeforeAfter(ENTITY_KEY), true);
    }

    private Long actorId() {
        return auditContextResolver.resolveActorId().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object value) {
        return value == null ? null : objectMapper.convertValue(value, Map.class);
    }

    private <T> T safely(Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        }
        catch (Exception e) {
            log.warn("Audit precondition check failed for commission — falling back to {}", fallback, e);
            return fallback;
        }
    }
}
