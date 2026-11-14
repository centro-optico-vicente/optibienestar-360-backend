package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Explicit audit of the manual actions on a campaign slot (V260, hub ADR
 * 0038 §9): confirm, release and payment reminders. The slot is addressed by
 * its own uuid while the endpoints take the campaign (and the payment for a
 * reminder), so the generic {@code @Auditable} aspect does not fit — same
 * explicit-call precedent as {@code CommissionAuditRecorder}. Never blocks
 * the business operation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionSlotAuditRecorder {

    static final String ENTITY_KEY = "promotion_redemption";

    private final EntityConfigService entityConfigService;
    private final AuditContextResolver auditContextResolver;
    private final DataChangeAuditWriter dataChangeAuditWriter;

    /** Records an update of the slot {@code slotUuid}; {@code before}/{@code after} are small field maps. */
    public void recordUpdate(UUID slotUuid, Map<String, Object> before, Map<String, Object> after) {
        try {
            if (!entityConfigService.isEnabled(ENTITY_KEY, AuditAction.UPDATE)) {
                return;
            }
            boolean capture = entityConfigService.captureBeforeAfter(ENTITY_KEY);
            dataChangeAuditWriter.write(ENTITY_KEY, slotUuid, AuditAction.UPDATE,
                    capture ? before : null, capture ? after : null,
                    auditContextResolver.resolveActorId().orElse(null), null);
        } catch (Exception e) {
            log.warn("Failed to persist data_change_audit_log for promotion slot {} — business operation already completed",
                    slotUuid, e);
        }
    }
}
