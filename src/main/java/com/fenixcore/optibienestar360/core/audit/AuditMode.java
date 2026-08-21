package com.fenixcore.optibienestar360.core.audit;

/**
 * Global override for the future {@code DataChangeAuditAspect}/{@code ReportAuditService}
 * (spec 16-audit.md, Decisión 8), stored per-field on the {@code system_configs} singleton.
 * Takes precedence over the per-entity {@code audit_entity_config} table (V60):
 * only {@link #PER_ENTITY} defers to it.
 */
public enum AuditMode {
    PER_ENTITY,
    FORCE_ENABLED,
    FORCE_DISABLED
}
