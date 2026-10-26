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
        assertEquals("app.commission_tiers", row.get("target"));
    }

    @Test
    void commissionsTierForeignKeyColumnIsIndexed() {
        // V193: FK columns are not indexed automatically in PostgreSQL.
        String index = jdbc.queryForObject(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'app' AND tablename = 'commissions' "
                        + "AND indexname = 'idx_commissions_tier'", String.class);
        assertNotNull(index);
    }

}
