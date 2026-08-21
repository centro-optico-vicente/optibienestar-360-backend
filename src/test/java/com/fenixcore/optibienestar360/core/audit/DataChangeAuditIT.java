package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.dto.DataChangeAuditLogPageDto;
import com.fenixcore.optibienestar360.core.audit.entity.DataChangeAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.DataChangeAuditLogRepository;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeCreateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.service.AllyTypeService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that {@link DataChangeAuditAspect} actually intercepts a
 * real {@code @Auditable}-annotated service call and persists a row (spec
 * 16-audit.md §Aspecto AOP) — not just that the annotation/aspect classes
 * compile. Same real-Postgres, skip-if-unreachable pattern as
 * {@code FlywayMigrationIT}, since {@code data_change_audit_log} (V62) and
 * {@code audit_entity_config} (V60, seeded with {@code ally_type}) don't
 * exist in the H2 "test" profile (Flyway disabled there).
 *
 * <p>Deliberately NOT {@code @Transactional} — {@link DataChangeAuditWriter}
 * commits in its own {@code REQUIRES_NEW} transaction regardless (that's the
 * point, see spec §Aspecto AOP paso 6), so a rollback-at-end-of-test wrapper
 * would only hide the business row while leaving the audit row committed
 * anyway. Each test cleans up its own audit rows in a {@code finally} block
 * instead (see {@link #cleanUp} for why the {@code ally_types} row itself is
 * left behind).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataChangeAuditIT {

    @Autowired
    private AllyTypeService allyTypeService;

    @Autowired
    private DataChangeAuditLogRepository dataChangeAuditLogRepository;

    @Autowired
    private DataChangeAuditQueryService dataChangeAuditQueryService;

    @Autowired
    private AuditDisplayResolver auditDisplayResolver;

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
                "No Postgres reachable at " + host + ":" + port + " — skipping data-change audit validation");
    }

    @Test
    void createIsAudited() {
        String suffix = String.valueOf(System.nanoTime());
        AllyTypeDto created = allyTypeService.create(
                new AllyTypeCreateRequest("AUDIT_IT_" + suffix, "Audit IT ally type " + suffix, null));

        try {
            Optional<DataChangeAuditLog> entry = findAuditEntry(created.uuid(), AuditAction.CREATE);

            assertTrue(entry.isPresent(), "expected a data_change_audit_log row for the ally_type create");
            assertNotNull(entry.get().getAfterJson());
        } finally {
            cleanUp(created.uuid());
        }
    }

    @Test
    void updateIsAuditedWithBeforeAndAfterSnapshots() {
        String suffix = String.valueOf(System.nanoTime());
        AllyTypeDto created = allyTypeService.create(
                new AllyTypeCreateRequest("AUDIT_IT_U_" + suffix, "Before update " + suffix, null));

        AllyTypeDto updated = allyTypeService.update(created.uuid(),
                new AllyTypeUpdateRequest("After update " + suffix, null, null));

        try {
            Optional<DataChangeAuditLog> entry = findAuditEntry(updated.uuid(), AuditAction.UPDATE);

            assertTrue(entry.isPresent(), "expected a data_change_audit_log UPDATE row for the ally_type update");
            assertNotNull(entry.get().getBeforeJson(), "before snapshot should be captured via the getDetail/get(UUID) convention");
            assertNotNull(entry.get().getAfterJson());
        } finally {
            cleanUp(updated.uuid());
        }
    }

    @Test
    void queryServiceFiltersByEntityActionAndDateRange() {
        String suffix = String.valueOf(System.nanoTime());
        Instant before = Instant.now().minus(1, ChronoUnit.MINUTES);
        AllyTypeDto created = allyTypeService.create(
                new AllyTypeCreateRequest("AUDIT_IT_Q_" + suffix, "Query IT ally type " + suffix, null));

        try {
            // entityKey + entityUuid: "history of one record" use case.
            DataChangeAuditLogPageDto byEntity = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, null, null, null, null);
            assertTrue(byEntity.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())),
                    "expected the create to show up when filtering by entityKey+entityUuid");

            // action filter: CREATE should match, DELETE should not.
            DataChangeAuditLogPageDto byAction = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, AuditAction.CREATE, null, null, null);
            assertTrue(byAction.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())));

            DataChangeAuditLogPageDto wrongAction = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, AuditAction.DELETE, null, null, null);
            assertFalse(wrongAction.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())));

            // date range: occurredAt must fall within [before, now+1m].
            DataChangeAuditLogPageDto byDateRange = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, null,
                    before, Instant.now().plus(1, ChronoUnit.MINUTES), null);
            assertTrue(byDateRange.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())));

            DataChangeAuditLogPageDto outsideDateRange = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, null,
                    before.minus(1, ChronoUnit.DAYS), before, null);
            assertFalse(outsideDateRange.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())));

            // RSQL filter on an allow-listed field.
            DataChangeAuditLogPageDto byRsql = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), null, null, null, null, null, null,
                    "entityKey==ally_type;entityUuid==" + created.uuid());
            assertTrue(byRsql.content().stream().anyMatch(dto -> dto.entityUuid().equals(created.uuid())));

            // RSQL filter referencing a non-allow-listed field must be rejected.
            assertThrowsIllegalArgument(() -> dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), null, null, null, null, null, null, "actorId==1"));

            // page size cap.
            assertThrowsIllegalArgument(() -> dataChangeAuditQueryService.list(
                    PageRequest.of(0, 201), null, null, null, null, null, null, null));
        } finally {
            cleanUp(created.uuid());
        }
    }

    @Test
    void dtoCarriesDisplayValuesAndFirstChangeIsPinnable() {
        String suffix = String.valueOf(System.nanoTime());
        AllyTypeDto created = allyTypeService.create(
                new AllyTypeCreateRequest("AUDIT_IT_D_" + suffix, "Display IT ally type " + suffix, null));
        AllyTypeDto updated = allyTypeService.update(created.uuid(),
                new AllyTypeUpdateRequest("Display IT renamed " + suffix, null, null));

        try {
            DataChangeAuditLogPageDto page = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 20), "ally_type", created.uuid(), null, null, null, null, null);

            DataChangeAuditLogDto createDto = page.content().stream()
                    .filter(dto -> dto.action() == AuditAction.CREATE).findFirst()
                    .orElseThrow(() -> new AssertionError("expected a CREATE row"));
            DataChangeAuditLogDto updateDto = page.content().stream()
                    .filter(dto -> dto.action() == AuditAction.UPDATE).findFirst()
                    .orElseThrow(() -> new AssertionError("expected an UPDATE row"));

            // entity_display resolves to the record's own current label (registered for "ally_type").
            assertNotNull(createDto.entityDisplay(), "entityDisplay should resolve for a registered entity_key");
            assertTrue(createDto.entityDisplay().contains("Display IT renamed " + suffix),
                    "entityDisplay should reflect the CURRENT ally_type row, not the historical snapshot");

            // action_Display is localized, not the bare enum name.
            assertNotNull(createDto.action_Display());
            assertFalse(createDto.action_Display().equals("CREATE"), "action_Display should be a human label, not the raw enum");

            // actor_uuid/actor_name travel together — never one populated without the other.
            assertTrue((createDto.actorUuid() == null) == (createDto.actor_Display() == null),
                    "actor_Display should be present iff actorUuid resolved");

            // first-change: pinned independent of pagination/order, and matches the actual CREATE row.
            Optional<DataChangeAuditLogDto> firstChange = dataChangeAuditQueryService.firstChange("ally_type", created.uuid());
            assertTrue(firstChange.isPresent());
            assertTrue(firstChange.get().uuid().equals(createDto.uuid()),
                    "first_change should be the same row as the CREATE entry, regardless of how the paginated list is filtered/sorted");

            // same value travels embedded in list()'s response — one request, no round-trip
            // (mirrors ListEntityLogsResponse.first_entity_log).
            assertNotNull(page.firstChange(), "firstChange should be embedded when the query is scoped to one record");
            assertEquals(createDto.uuid(), page.firstChange().uuid());

            // unscoped (cross-entity) queries have no single "first change" — must stay null.
            DataChangeAuditLogPageDto unscoped = dataChangeAuditQueryService.list(
                    PageRequest.of(0, 5), null, null, null, null, null, null, null);
            assertNull(unscoped.firstChange(), "firstChange only makes sense when entityKey+entityUuid scope the query");
        } finally {
            cleanUp(updated.uuid());
        }
    }

    @Test
    void withFieldDisplaysFormatsEveryValueShape() {
        Map<String, Object> snapshot = Map.of(
                "active", true,
                "published", false,
                "publishedAt", "2026-08-20T09:15:00Z", // 05:15 America/Caracas (UTC-4, no DST — ADR 0010)
                "joinedAt", "2026-08-20",
                "monthlyFee", new BigDecimal("12.50"),
                "includedBeneficiaries", 1234,
                "status", "IN_REVIEW",
                "freeTextNote", "just a plain note"
        );

        Map<String, Object> es = auditDisplayResolver.withFieldDisplays(snapshot, Locale.of("es"));
        assertEquals("Sí", es.get("active_Display"));
        assertEquals("No", es.get("published_Display"));
        assertEquals("20-08-2026 05:15", es.get("publishedAt_Display"));
        assertEquals("20-08-2026", es.get("joinedAt_Display"));
        assertEquals("12,50", es.get("monthlyFee_Display"), "scale (2 decimals) from the original BigDecimal should be preserved");
        assertEquals("1.234", es.get("includedBeneficiaries_Display"));
        assertEquals("En revisión", es.get("status_Display"));
        assertNull(es.get("freeTextNote_Display"), "plain free-text should not get a fabricated _Display");

        Map<String, Object> en = auditDisplayResolver.withFieldDisplays(snapshot, Locale.US);
        assertEquals("Yes", en.get("active_Display"));
        assertEquals("08-20-2026 05:15", en.get("publishedAt_Display"));
        assertEquals("08-20-2026", en.get("joinedAt_Display"));
        assertEquals("12.50", en.get("monthlyFee_Display"));
        assertEquals("1,234", en.get("includedBeneficiaries_Display"));
        assertEquals("In review", en.get("status_Display"));

        // originals are always untouched.
        assertEquals(true, es.get("active"));
        assertEquals("2026-08-20T09:15:00Z", es.get("publishedAt"));
        assertEquals(new BigDecimal("12.50"), es.get("monthlyFee"));
    }

    private static void assertThrowsIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private Optional<DataChangeAuditLog> findAuditEntry(java.util.UUID entityUuid, AuditAction action) {
        return dataChangeAuditLogRepository.findAll().stream()
                .filter(e -> "ally_type".equals(e.getEntityKey()) && entityUuid.equals(e.getEntityUuid()) && e.getAction() == action)
                .findFirst();
    }

    /**
     * Cleans up the audit rows this test produced. Deliberately does NOT try
     * to hard-delete the {@code ally_types} row itself: on some local
     * Postgres setups the FK-check lock against {@code allies} evaluates an
     * unrelated {@code idx_allies_name_unaccent} expression index that can
     * fail depending on the connection's {@code search_path} — a local setup
     * gap, not something this test is meant to catch. Leaving a handful of
     * {@code AUDIT_IT_*}-coded rows behind in a disposable dev database is
     * harmless; the audit assertions above already ran either way.
     */
    private void cleanUp(java.util.UUID allyTypeUuid) {
        dataChangeAuditLogRepository.findAll().stream()
                .filter(e -> "ally_type".equals(e.getEntityKey()) && allyTypeUuid.equals(e.getEntityUuid()))
                .forEach(dataChangeAuditLogRepository::delete);
    }
}
