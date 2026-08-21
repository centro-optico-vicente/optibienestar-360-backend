package com.fenixcore.optibienestar360.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code *Service} create/update/delete method for interception by
 * {@link DataChangeAuditAspect} (spec 16-audit.md §Aspecto AOP). {@code entity}
 * must match an {@code entity_key} row in {@code audit_entity_config} (V60) —
 * if none exists, the aspect skips auditing and logs a warning; it never
 * blocks the business operation either way (fail-safe, Decisión 6).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** e.g. {@code "ally"} — must match {@code audit_entity_config.entity_key}. */
    String entity();

    AuditAction action();

    /**
     * Index (into the method's arguments) of the {@link java.util.UUID} that
     * identifies the entity being changed. Use {@code -1} (default) for
     * CREATE, where the uuid is resolved from the returned DTO instead.
     */
    int uuidArgIndex() default -1;
}
