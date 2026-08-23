package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.AttachedFile;
import com.fenixcore.optibienestar360.common.storage.AttachedFileRepository;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.core.audit.entity.ReportAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.ReportAuditLogRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Records one report-generation event (V63, spec 16-audit.md §Reportes),
 * called explicitly from {@code GenericDocumentController}'s 3 methods —
 * unlike {@code DataChangeAuditAspect}, this isn't AOP-driven because report
 * generation isn't a CRUD method on a per-entity service with a uniform
 * signature; the controller already has everything needed (payload, format,
 * actor) at the call site.
 *
 * <p>Fail-safe (same philosophy as {@code DataChangeAuditAspect}, Decisión 6):
 * this never throws back to the caller. If the R2 upload fails, the response
 * still returns the rendered bytes — the log row is written with
 * {@code attachedFileId=null} (spec §Reportes). If persisting the log row
 * itself fails, that's logged and swallowed too.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAuditService {

    private static final String OWNER_TABLE = "report_audit_log";

    private final ReportAuditLogRepository reportAuditLogRepository;
    private final AttachedFileRepository attachedFileRepository;
    private final ObjectProvider<StorageService> storageProvider;
    private final AuditEntityConfigService auditEntityConfigService;
    private final SystemConfigService systemConfigService;
    private final AuditContextResolver auditContextResolver;

    /**
     * @param entityKey lower_snake_case entity key (e.g. {@code "ally"}), or
     *                  {@code null} for reports not scoped to one entity
     *                  (e.g. a table listing) — {@code audit_entity_config}'s
     *                  {@code audit_report} flag is only consulted when this
     *                  is present; an unscoped report is governed solely by
     *                  the global {@code report_audit_mode}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGeneration(String reportType, String entityKey, UUID entityUuid, String entityIdentifier,
                                  String format, Map<String, Object> parametersJson,
                                  byte[] content, String fileName, String contentType) {
        try {
            if (!shouldAudit(entityKey)) {
                return;
            }

            ReportAuditLog entry = new ReportAuditLog();
            entry.setUuid(UUID.randomUUID());
            entry.setReportType(reportType);
            entry.setEntityKey(entityKey);
            entry.setEntityUuid(entityUuid);
            entry.setEntityIdentifier(entityIdentifier);
            entry.setFormat(format);
            entry.setParametersJson(parametersJson);
            entry.setActorId(auditContextResolver.resolveActorId().orElse(null));
            entry.setFileName(fileName);
            entry.setSizeBytes(content != null ? (long) content.length : null);

            AttachedFile attachedFile = tryUpload(entry.getUuid(), content, fileName, contentType);
            if (attachedFile != null) {
                entry.setAttachedFileId(attachedFile.getId());
            }

            reportAuditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to record report audit for reportType='{}' entityKey='{}' — response still returns the file",
                    reportType, entityKey, ex);
        }
    }

    private boolean shouldAudit(String entityKey) {
        AuditMode globalMode = systemConfigService.getReportAuditMode();
        if (globalMode == AuditMode.FORCE_DISABLED) {
            return false;
        }
        if (globalMode == AuditMode.FORCE_ENABLED) {
            return true;
        }
        if (entityKey == null || entityKey.isBlank()) {
            // PER_ENTITY with no entity to check against — default to auditing
            // (same fail-open-toward-recording stance as an unscoped report).
            return true;
        }
        var config = auditEntityConfigService.findConfig(entityKey);
        return config == null || config.isAuditReport();
    }

    private AttachedFile tryUpload(UUID reportUuid, byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            return null;
        }
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.warn("R2 storage disabled — report_audit_log row for {} will have attachedFileId=null", reportUuid);
            return null;
        }
        try {
            String key = StorageKeyBuilder.build(FileVisibility.TEMPORARY, OWNER_TABLE, reportUuid, fileName);
            storage.upload(storage.getBucket(), key, new ByteArrayInputStream(content), content.length, contentType);

            AttachedFile file = new AttachedFile();
            file.setOwnerTable(OWNER_TABLE);
            file.setOwnerUuid(reportUuid);
            file.setVisibility(FileVisibility.TEMPORARY);
            file.setCategory("GENERATED_REPORT");
            file.setFileKey(key);
            file.setFileName(StorageKeyBuilder.safeName(fileName));
            file.setMimeType(contentType);
            file.setSizeBytes((long) content.length);
            file.setUploadedAt(java.time.Instant.now());
            return attachedFileRepository.save(file);
        } catch (Exception ex) {
            log.warn("R2 upload failed for generated report {} — response still returns the file", reportUuid, ex);
            return null;
        }
    }
}
