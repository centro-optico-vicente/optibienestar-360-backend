package com.fenixcore.optibienestar360;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full Spring context under the "dev" profile (real Postgres, Flyway
 * enabled) instead of the "test" profile every other *IT/*Test class here uses
 * (H2, Flyway disabled). Context startup fails if any migration under
 * src/main/resources/db/migration/** doesn't apply cleanly, or if the JPA schema
 * doesn't match what Flyway produced — this is the only test in the suite that
 * actually exercises the migrations against real Postgres.
 *
 * Skips itself instead of failing when no Postgres is reachable at
 * DATABASE_HOST:DATABASE_PORT (defaults to localhost:5432, matching ci.yaml's
 * services.postgres) so `./gradlew build` on a machine without a local Postgres
 * running doesn't break on this test specifically.
 */
@SpringBootTest
@ActiveProfiles("dev")
class FlywayMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

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
                "No Postgres reachable at " + host + ":" + port + " — skipping Flyway migration validation");
    }

    @Test
    void contextLoadsAndMigrationsApplyCleanly() {
        // Intentionally empty: reaching this point means the Spring context started
        // successfully, which means Flyway (enabled under the "dev" profile) applied
        // every migration in src/main/resources/db/migration/** without error.
    }

    @Test
    void commissionsTierForeignKeyIsValidatedAndRestrictsDelete() {
        // fk_commissions_tier (V42) must be validated (so unknown tier ids are
        // rejected) and NO ACTION ('a'): a referenced tier cannot be hard-deleted.
        var row = jdbc.queryForMap(
                "SELECT convalidated, confdeltype::text AS del, confrelid::regclass::text AS target "
                        + "FROM pg_constraint WHERE conname = 'fk_commissions_tier' "
                        + "AND conrelid = 'app.commissions'::regclass");
        assertEquals(Boolean.TRUE, row.get("convalidated"));
        assertEquals("a", row.get("del"));
        assertTrue(row.get("target").toString().endsWith("commission_tiers"));
    }

    @Test
    void commissionsTierForeignKeyColumnIsIndexed() {
        // V193: FK columns are not indexed automatically in PostgreSQL.
        String index = jdbc.queryForObject(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'app' AND tablename = 'commissions' "
                        + "AND indexname = 'idx_commissions_tier'", String.class);
        assertNotNull(index);
    }

    @Test
    void finalContractTemplatesAreSeededPerSegment() {
        // V217: one ALIADO template per segment, plus the final AFILIADO / ASESOR texts.
        Integer segmentStreams = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT segment) FROM app.terms_versions WHERE term_type = 'ALIADO' "
                        + "AND segment IN ('B2B', 'B2P') AND content_markdown LIKE '%{{org.phones}}%'", Integer.class);
        assertEquals(2, segmentStreams);
        Integer finalTexts = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT term_type) FROM app.terms_versions WHERE term_type IN ('AFILIADO', 'ASESOR') "
                        + "AND segment IS NULL AND content_markdown LIKE '%{{org.address}}%'", Integer.class);
        assertEquals(2, finalTexts);
    }

    @Test
    void membershipTermColumnsAndCoherenceConstraintsExist() {
        // V220 (hub ADR 0034): the term rule lives on plans and is snapshotted onto memberships.
        for (String table : new String[] {"plans", "memberships"}) {
            Integer columns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'app' AND table_name = ? "
                            + "AND column_name IN ('reinscription_required', 'term_mode', 'term_fixed_month', "
                            + "'term_fixed_day', 'term_period_months')", Integer.class, table);
            assertEquals(5, columns, table);
        }
        // Integer, not SMALLINT: the JPA entities map these as Integer and schema validation rejects a mismatch.
        Integer integerTermColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'app' "
                        + "AND table_name IN ('plans', 'memberships') AND data_type = 'integer' "
                        + "AND column_name IN ('term_fixed_month', 'term_fixed_day', 'term_period_months')", Integer.class);
        assertEquals(6, integerTermColumns);
        Integer coherence = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname IN "
                        + "('chk_plans_term_coherence', 'chk_memberships_term_coherence', 'chk_memberships_inscription_due')",
                Integer.class);
        assertEquals(3, coherence);
    }

    @Test
    void termCoherenceConstraintCoversBothModes() {
        // Read-only check (no writes to the shared dev database): the rule must require month + day for
        // FIXED_DATE and the period length for PERIOD.
        String def = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'chk_plans_term_coherence' "
                        + "AND conrelid = 'app.plans'::regclass", String.class);
        assertTrue(def.contains("FIXED_DATE") && def.contains("PERIOD"), def);
        assertTrue(def.contains("term_fixed_month IS NOT NULL") && def.contains("term_period_months IS NOT NULL"), def);
    }

    @Test
    void membershipTermRenewalJobAndPermissionAreSeeded() {
        Integer job = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.scheduled_jobs WHERE code = 'MEMBERSHIP_TERM_RENEWAL' "
                        + "AND timezone = 'America/Caracas'", Integer.class);
        assertEquals(1, job);
        Integer grants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.role_permissions rp "
                        + "JOIN app.permissions p ON p.permissions_id = rp.permission_id "
                        + "JOIN app.roles r ON r.roles_id = rp.role_id "
                        + "WHERE p.name = 'MEMBERSHIP_TERM_UPDATE' AND r.name IN ('SYSTEM', 'ADMINISTRADOR')", Integer.class);
        assertEquals(2, grants);
    }

}
