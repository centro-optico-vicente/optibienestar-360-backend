package com.fenixcore.optibienestar360.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepts every {@code @Auditable}-annotated service method (spec
 * 16-audit.md §Aspecto AOP). Never blocks the business operation: any
 * failure while resolving config, snapshots, or persisting the audit row is
 * caught and logged, not propagated.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataChangeAuditAspect {

    /** Tried in order against {@code joinPoint.getTarget()} to snapshot "before" state. */
    private static final List<String> SNAPSHOT_METHOD_NAMES = List.of("getDetail", "get", "findDetail");

    private final AuditEntityConfigService auditEntityConfigService;
    private final AuditContextResolver auditContextResolver;
    private final DataChangeAuditWriter dataChangeAuditWriter;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        boolean shouldAudit = safely(() -> auditEntityConfigService.isEnabled(auditable.entity(), auditable.action()), false);

        UUID entityUuid = auditable.uuidArgIndex() >= 0
                ? (UUID) joinPoint.getArgs()[auditable.uuidArgIndex()]
                : null;

        Object beforeSnapshot = null;
        if (shouldAudit && auditable.action() != AuditAction.CREATE && entityUuid != null
                && safely(() -> auditEntityConfigService.captureBeforeAfter(auditable.entity()), false)) {
            beforeSnapshot = safely(() -> snapshot(joinPoint.getTarget(), entityUuid), null);
        }

        Object result = joinPoint.proceed();

        if (shouldAudit) {
            try {
                persist(auditable, entityUuid, beforeSnapshot, result);
            } catch (Exception e) {
                log.warn("Failed to persist data_change_audit_log for entity='{}' action={} — business operation already completed",
                        auditable.entity(), auditable.action(), e);
            }
        }

        return result;
    }

    private void persist(Auditable auditable, UUID entityUuid, Object beforeSnapshot, Object result) {
        UUID resolvedUuid = entityUuid != null ? entityUuid : extractUuid(result);
        Object afterSnapshot = auditable.action() == AuditAction.DELETE ? null : result;

        boolean captureBeforeAfter = safely(() -> auditEntityConfigService.captureBeforeAfter(auditable.entity()), true);

        dataChangeAuditWriter.write(
                auditable.entity(),
                resolvedUuid,
                auditable.action(),
                captureBeforeAfter ? toJsonMap(beforeSnapshot) : null,
                captureBeforeAfter ? toJsonMap(afterSnapshot) : null,
                auditContextResolver.resolveActorId().orElse(null),
                null // login_audit_log_id — pending AuthService login hook (spec §Login)
        );
    }

    private Object snapshot(Object target, UUID uuid) {
        for (String methodName : SNAPSHOT_METHOD_NAMES) {
            try {
                Method method = target.getClass().getMethod(methodName, UUID.class);
                return method.invoke(target, uuid);
            } catch (NoSuchMethodException ignored) {
                // try the next candidate name
            } catch (Exception e) {
                log.debug("Snapshot method '{}' failed for {}", methodName, target.getClass().getSimpleName(), e);
                return null;
            }
        }
        return null;
    }

    private UUID extractUuid(Object dto) {
        if (dto == null) {
            return null;
        }
        try {
            Method getUuid = dto.getClass().getMethod("getUuid");
            Object value = getUuid.invoke(dto);
            return value instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            // record-style DTOs expose the accessor as uuid() instead of getUuid()
            try {
                Method uuidAccessor = dto.getClass().getMethod("uuid");
                Object value = uuidAccessor.invoke(dto);
                return value instanceof UUID uuid ? uuid : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private <T> T safely(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Audit precondition check failed — falling back to {}", fallback, e);
            return fallback;
        }
    }
}
