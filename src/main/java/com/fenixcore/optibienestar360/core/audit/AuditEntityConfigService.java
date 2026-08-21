package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.entity.AuditEntityConfig;
import com.fenixcore.optibienestar360.core.audit.repository.AuditEntityConfigRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves whether a given entity/action should be audited (spec 16-audit.md
 * §Aspecto AOP, paso 1). Two layers, in order:
 *
 * <ol>
 *   <li>{@code system_configs}' global {@link AuditMode} override
 *       (Decisión 8) — {@code FORCE_ENABLED}/{@code FORCE_DISABLED} short-circuit
 *       everything below; only {@code PER_ENTITY} falls through.</li>
 *   <li>{@code audit_entity_config} (V60), cached under {@code "audit-config"}
 *       (60s TTL in prod, see {@code RedisCacheConfig}) — per-entity
 *       {@code enabled} + per-action flag.</li>
 * </ol>
 *
 * <p>Fail-safe: a missing {@code audit_entity_config} row behaves like
 * {@code enabled=false} (skip + warn), never blocks the business operation
 * (Decisión 6).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEntityConfigService {

    private final AuditEntityConfigRepository auditEntityConfigRepository;
    private final AuditEntityConfigCache auditEntityConfigCache;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public boolean isEnabled(String entityKey, AuditAction action) {
        AuditMode globalMode = systemConfigService.getDataChangeAuditMode();
        if (globalMode == AuditMode.FORCE_DISABLED) {
            return false;
        }
        if (globalMode == AuditMode.FORCE_ENABLED) {
            return true;
        }

        AuditEntityConfig config = findConfig(entityKey);
        if (config == null) {
            log.warn("audit_entity_config has no row for entity_key='{}' — skipping audit for {} (fail-safe)",
                    entityKey, action);
            return false;
        }
        if (!config.isEnabled()) {
            return false;
        }
        return switch (action) {
            case CREATE -> config.isAuditCreate();
            case UPDATE -> config.isAuditUpdate();
            case DELETE -> config.isAuditDelete();
        };
    }

    @Transactional(readOnly = true)
    public boolean captureBeforeAfter(String entityKey) {
        AuditEntityConfig config = findConfig(entityKey);
        return config != null && config.isCaptureBeforeAfter();
    }

    public AuditEntityConfig findConfig(String entityKey) {
        return auditEntityConfigCache.get(entityKey);
    }

    @Transactional
    public AuditEntityConfig updateConfig(String entityKey, AuditEntityConfig changes) {
        AuditEntityConfig config = auditEntityConfigRepository.findByEntityKey(entityKey)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "audit_entity_config.entity_key not found: " + entityKey));

        config.setEnabled(changes.isEnabled());
        config.setAuditCreate(changes.isAuditCreate());
        config.setAuditUpdate(changes.isAuditUpdate());
        config.setAuditDelete(changes.isAuditDelete());
        config.setAuditReport(changes.isAuditReport());
        config.setCaptureBeforeAfter(changes.isCaptureBeforeAfter());
        config.setNotes(changes.getNotes());

        AuditEntityConfig saved = auditEntityConfigRepository.save(config);
        auditEntityConfigCache.evict(entityKey);
        return saved;
    }
}
