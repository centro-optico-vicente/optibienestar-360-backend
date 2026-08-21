package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.DataChangeAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.DataChangeAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Persists one {@link DataChangeAuditLog} row in its own transaction
 * ({@code REQUIRES_NEW}, spec 16-audit.md §Aspecto AOP paso 6) — a rollback of
 * the business transaction never takes the audit row with it (and vice
 * versa: an audit failure here never rolls back business changes, since
 * {@link DataChangeAuditAspect} calls this after the business method already
 * returned, wrapped in its own try/catch).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataChangeAuditWriter {

    private final DataChangeAuditLogRepository dataChangeAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String entityKey, UUID entityUuid, AuditAction action,
                       Map<String, Object> beforeJson, Map<String, Object> afterJson,
                       Long actorId, Long loginAuditLogId) {
        DataChangeAuditLog entry = new DataChangeAuditLog();
        entry.setEntityKey(entityKey);
        entry.setEntityUuid(entityUuid);
        entry.setAction(action);
        entry.setBeforeJson(beforeJson);
        entry.setAfterJson(afterJson);
        entry.setActorId(actorId);
        entry.setLoginAuditLogId(loginAuditLogId);
        dataChangeAuditLogRepository.save(entry);
    }
}
