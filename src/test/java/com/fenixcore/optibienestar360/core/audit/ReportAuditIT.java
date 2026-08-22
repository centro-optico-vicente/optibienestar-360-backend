package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.dto.ReportAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.entity.ReportAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.ReportAuditLogRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that {@link ReportAuditService#recordGeneration} actually
 * persists a {@code report_audit_log} row (V63, spec 16-audit.md §Reportes)
 * and that {@link ReportAuditQueryService} can read it back — same
 * real-Postgres, skip-if-unreachable pattern as {@code DataChangeAuditIT},
 * since {@code report_audit_log} doesn't exist in the H2 "test" profile.
 *
 * <p>{@code storage.r2.enabled=false} in this profile, so every test here
 * exercises the fail-safe path where the upload is skipped and
 * {@code attachedFileId} stays {@code null} — that's the realistic path for
 * this environment, not a workaround.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class ReportAuditIT {

    @Autowired
    private ReportAuditService reportAuditService;

    @Autowired
    private ReportAuditLogRepository reportAuditLogRepository;

    @Autowired
    private ReportAuditQueryService reportAuditQueryService;

    @BeforeAll
    static void assumePostgresReachable() {
        String host = System.getenv().getOrDefault("DATABASE_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("DATABASE_PORT", "5432"));
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            reachable = true;
        } catch (IOException ex) {
            reachable = false;
        }
        Assumptions.assumeTrue(reachable,
                "No Postgres reachable at " + host + ":" + port + " — skipping report audit validation");
    }

    @Test
    void recordGenerationPersistsRowWithNullAttachedFileWhenStorageDisabled() {
        String marker = "test-marker-" + System.nanoTime();
        byte[] content = "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        try {
            reportAuditService.recordGeneration("RECORD", "ally_type", null, marker,
                    "PDF", Map.of("k", "v"), content, "report.pdf", "application/pdf");

            Optional<ReportAuditLog> saved = reportAuditLogRepository.findAll().stream()
                    .filter(r -> marker.equals(r.getEntityIdentifier()))
                    .findFirst();
            assertTrue(saved.isPresent(), "expected a report_audit_log row for marker " + marker);
            assertNull(saved.get().getAttachedFileId(), "R2 disabled in this profile — attachedFileId must stay null");
            assertEquals("report.pdf", saved.get().getFileName());
            assertEquals((long) content.length, saved.get().getSizeBytes());
        } finally {
            reportAuditLogRepository.findAll().stream()
                    .filter(r -> marker.equals(r.getEntityIdentifier()))
                    .forEach(reportAuditLogRepository::delete);
        }
    }

    @Test
    void queryServiceFiltersByEntityKeyAndFormat() {
        String marker = "test-marker-" + System.nanoTime();
        try {
            reportAuditService.recordGeneration("RECORD", "plan", null, marker,
                    "XLSX", null, "xlsx-bytes".getBytes(StandardCharsets.UTF_8), "plan.xlsx", "application/vnd.ms-excel");

            Page<ReportAuditLogDto> page = reportAuditQueryService.list(
                    PageRequest.of(0, 20), null, "plan", null, null, "XLSX", null, null, null);
            assertTrue(page.getContent().stream().anyMatch(dto -> marker.equals(dto.entityIdentifier())));

            Page<ReportAuditLogDto> mismatched = reportAuditQueryService.list(
                    PageRequest.of(0, 20), null, "plan", null, null, "PDF", null, null, null);
            assertTrue(mismatched.getContent().stream().noneMatch(dto -> marker.equals(dto.entityIdentifier())));
        } finally {
            reportAuditLogRepository.findAll().stream()
                    .filter(r -> marker.equals(r.getEntityIdentifier()))
                    .forEach(reportAuditLogRepository::delete);
        }
    }

    @Test
    void downloadUrlThrowsWhenAttachedFileMissing() {
        String marker = "test-marker-" + System.nanoTime();
        reportAuditService.recordGeneration("RECORD", "member", null, marker,
                "PDF", null, "bytes".getBytes(StandardCharsets.UTF_8), "member.pdf", "application/pdf");
        try {
            UUID reportUuid = reportAuditLogRepository.findAll().stream()
                    .filter(r -> marker.equals(r.getEntityIdentifier()))
                    .findFirst()
                    .orElseThrow()
                    .getUuid();
            assertThrows(NoSuchElementException.class, () -> reportAuditQueryService.downloadUrl(reportUuid));
        } finally {
            reportAuditLogRepository.findAll().stream()
                    .filter(r -> marker.equals(r.getEntityIdentifier()))
                    .forEach(reportAuditLogRepository::delete);
        }
    }
}
