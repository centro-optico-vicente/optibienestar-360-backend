package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogPageDto;
import com.fenixcore.optibienestar360.core.audit.entity.DataChangeAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.DataChangeAuditLogRepository;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backs {@code GET /v1/admin/audit/data-changes} (spec 16-audit.md §Endpoints
 * admin) — the cross-entity read side of {@code data_change_audit_log}.
 * Supports both use cases asked for: the full change history of one record
 * ({@code entityKey} + {@code entityUuid}) and free-form filtering by actor,
 * action, and date range, plus an RSQL {@code filter} for anything else.
 */
@Service
@RequiredArgsConstructor
public class DataChangeAuditQueryService {

    /** Max page size — same 200 cap documented in 06-rest-api.md. */
    public static final int MAX_PAGE_SIZE = 200;

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "entityKey", "entityId", "entityUuid", "action",
            "requestMethod", "requestPath", "occurredAt", "createdAt"
    );

    private final DataChangeAuditLogRepository dataChangeAuditLogRepository;
    private final UserRepository userRepository;
    private final AuditDisplayResolver auditDisplayResolver;

    @Transactional(readOnly = true)
    public DataChangeAuditLogPageDto list(Pageable pageable, String entityKey, UUID entityUuid,
                                           UUID actorUuid, AuditAction action,
                                           Instant from, Instant to, String filter) {
        if (pageable.isPaged() && pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("audit.page.size.exceeded");
        }

        Specification<DataChangeAuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (entityKey != null && !entityKey.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityKey"), entityKey));
        }
        if (entityUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityUuid"), entityUuid));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
        if (actorUuid != null) {
            // actorId is an internal BIGINT, never RSQL-filterable directly — resolved
            // here from the public actor uuid. An unknown uuid resolves to -1L (no
            // match) rather than silently ignoring the filter.
            Long actorId = userRepository.findByUuid(actorUuid).map(User::getId).orElse(-1L);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "audit.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }

        Page<DataChangeAuditLogDto> page = dataChangeAuditLogRepository.findAll(spec, pageable).map(this::toDto);

        // Only meaningful when the query is scoped to one record — "the first change
        // of what?" doesn't apply to the unscoped, cross-entity listing.
        DataChangeAuditLogDto firstChange = (entityKey != null && !entityKey.isBlank() && entityUuid != null)
                ? firstChange(entityKey, entityUuid).orElse(null)
                : null;

        return DataChangeAuditLogPageDto.of(page, firstChange);
    }

    /**
     * The pinned "when was this created" lookup — independent of pagination,
     * so a record with 500+ changes still lets the client show its origin
     * without having to page all the way to the end. Embedded in {@link #list}'s
     * response as {@code firstChange}; also exposed standalone for callers that
     * only need this value.
     */
    @Transactional(readOnly = true)
    public Optional<DataChangeAuditLogDto> firstChange(String entityKey, UUID entityUuid) {
        return dataChangeAuditLogRepository
                .findFirstByEntityKeyAndEntityUuidOrderByOccurredAtAsc(entityKey, entityUuid)
                .map(this::toDto);
    }

    private DataChangeAuditLogDto toDto(DataChangeAuditLog log) {
        UUID actorUuid = log.getActorId() != null
                ? userRepository.findById(log.getActorId()).map(User::getUuid).orElse(null)
                : null;

        return new DataChangeAuditLogDto(
                log.getUuid(),
                log.getEntityKey(),
                log.getEntityUuid(),
                auditDisplayResolver.resolveEntityDisplay(log.getEntityKey(), log.getEntityUuid()).orElse(null),
                log.getAction(),
                auditDisplayResolver.resolveActionLabel(log.getAction(), LocaleContextHolder.getLocale()),
                auditDisplayResolver.withFieldDisplays(log.getBeforeJson(), LocaleContextHolder.getLocale()),
                auditDisplayResolver.withFieldDisplays(log.getAfterJson(), LocaleContextHolder.getLocale()),
                actorUuid,
                auditDisplayResolver.resolveActorName(log.getActorId()).orElse(null),
                log.getRequestMethod(),
                log.getRequestPath(),
                log.getOccurredAt()
        );
    }
}
