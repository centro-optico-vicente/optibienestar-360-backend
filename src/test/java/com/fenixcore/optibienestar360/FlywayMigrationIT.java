package com.fenixcore.optibienestar360;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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

}
