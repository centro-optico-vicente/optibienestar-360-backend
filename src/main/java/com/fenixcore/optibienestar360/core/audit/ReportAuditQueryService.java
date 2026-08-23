package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.core.audit.dto.ReportAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.entity.ReportAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.ReportAuditLogRepository;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Backs {@code GET /v1/admin/audit/reports} (spec 16-audit.md §Endpoints
 * admin) — the cross-entity read side of {@code report_audit_log}: "who
 * generated what report, for which record, when" — plus the presigned
 * download link for a given row's attached file.
 */
@Service
@RequiredArgsConstructor
public class ReportAuditQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "reportType", "entityKey", "entityId", "entityUuid", "format", "generatedAt", "createdAt"
    );

    private final ReportAuditLogRepository reportAuditLogRepository;
    private final AttachedFileRepository attachedFileRepository;
    private final UserRepository userRepository;
    private final AuditDisplayResolver auditDisplayResolver;
    private final ObjectProvider<StorageService> storageProvider;

    @Transactional(readOnly = true)
    public Page<ReportAuditLogDto> list(Pageable pageable, String reportType, String entityKey, UUID entityUuid,
                                         UUID actorUuid, String format, Instant from, Instant to, String filter) {
        if (pageable.isPaged() && pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("audit.page.size.exceeded");
        }

        Specification<ReportAuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (reportType != null && !reportType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reportType"), reportType));
        }
        if (entityKey != null && !entityKey.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityKey"), entityKey));
        }
        if (entityUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityUuid"), entityUuid));
        }
        if (format != null && !format.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("format"), format.toUpperCase()));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("generatedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("generatedAt"), to));
        }
        if (actorUuid != null) {
            Long actorId = userRepository.findByUuid(actorUuid).map(User::getId).orElse(-1L);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "audit.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }

        return reportAuditLogRepository.findAll(spec, pageable).map(this::toDto);
    }

    /** Presigned URL for one report's stored file — 404 if the row has no {@code attachedFileId} (upload failed at generation time). */
    @Transactional(readOnly = true)
    public String downloadUrl(UUID reportUuid) {
        ReportAuditLog entry = reportAuditLogRepository.findByUuid(reportUuid)
                .orElseThrow(() -> new NoSuchElementException("audit.report.not_found"));
        if (entry.getAttachedFileId() == null) {
            throw new NoSuchElementException("audit.report.file_not_found");
        }
        AttachedFile file = attachedFileRepository.findById(entry.getAttachedFileId())
                .orElseThrow(() -> new NoSuchElementException("audit.report.file_not_found"));
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("file.storage_unavailable");
        }
        return storage.generatePresignedUrl(storage.getBucket(), file.getFileKey(), Duration.ofMinutes(15));
    }

    private ReportAuditLogDto toDto(ReportAuditLog log) {
        UUID actorUuid = log.getActorId() != null
                ? userRepository.findById(log.getActorId()).map(User::getUuid).orElse(null)
                : null;
        UUID fileUuid = log.getAttachedFileId() != null
                ? attachedFileRepository.findById(log.getAttachedFileId()).map(AttachedFile::getUuid).orElse(null)
                : null;

        return new ReportAuditLogDto(
                log.getUuid(),
                log.getReportType(),
                log.getEntityKey(),
                log.getEntityUuid(),
                log.getEntityKey() != null && log.getEntityUuid() != null
                        ? auditDisplayResolver.resolveEntityDisplay(log.getEntityKey(), log.getEntityUuid()).orElse(null)
                        : null,
                log.getEntityIdentifier(),
                log.getFormat(),
                log.getParametersJson(),
                actorUuid,
                auditDisplayResolver.resolveActorName(log.getActorId()).orElse(null),
                fileUuid,
                log.getFileName(),
                log.getSizeBytes(),
                log.getGeneratedAt()
        );
    }
}
