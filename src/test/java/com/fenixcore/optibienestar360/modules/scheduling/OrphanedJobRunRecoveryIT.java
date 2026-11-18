package com.fenixcore.optibienestar360.modules.scheduling;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.Outcome;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.TriggerSource;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup recovery of runs left {@code RUNNING} by a process that died
 * mid-run, against real Postgres: the orphan is closed as {@code FAILED}
 * with a duration, finished runs are untouched, and the job's concurrency
 * gate opens again. Same skip-if-unreachable pattern as {@code LinkStatusIT}.
 */
@SpringBootTest
@ActiveProfiles("dev")
class OrphanedJobRunRecoveryIT {

    @Autowired private ScheduledJobRepository jobRepository;
    @Autowired private ScheduledJobRunRepository runRepository;

    private final List<Long> created = new ArrayList<>();

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
        Assumptions.assumeTrue(reachable, "No Postgres reachable at " + host + ":" + port + " — skipping orphaned run recovery validation");
    }

    @AfterEach
    void cleanUp() {
        runRepository.deleteAllById(created);
    }

    private ScheduledJob seededJob() {
        return jobRepository.findByCode("FETCH_EXCHANGE_RATES").orElseThrow();
    }

    private ScheduledJobRun save(ScheduledJob job, Outcome outcome, Instant startedAt) {
        ScheduledJobRun run = new ScheduledJobRun();
        run.setScheduledJob(job);
        run.setStartedAt(startedAt);
        run.setOutcome(outcome.name());
        run.setTriggeredBy(TriggerSource.SCHEDULED.name());
        if (outcome != Outcome.RUNNING) {
            run.setFinishedAt(startedAt.plusSeconds(1));
            run.setDurationMs(1000L);
        }
        ScheduledJobRun saved = runRepository.save(run);
        created.add(saved.getId());
        return saved;
    }

    @Test
    void anOrphanedRunIsClosedAsFailedAndUnblocksItsJob() {
        ScheduledJob job = seededJob();
        Instant startedAt = Instant.now().minus(Duration.ofHours(1));
        ScheduledJobRun orphan = save(job, Outcome.RUNNING, startedAt);
        assertThat(runRepository.hasRunningFor(job.getId())).isTrue();

        int closed = runRepository.failAllRunning(Instant.now(), "Interrupted by application restart");

        assertThat(closed).isGreaterThanOrEqualTo(1);
        ScheduledJobRun reloaded = runRepository.findById(orphan.getId()).orElseThrow();
        assertThat(reloaded.getOutcome()).isEqualTo(Outcome.FAILED.name());
        assertThat(reloaded.getFinishedAt()).isNotNull();
        assertThat(reloaded.getDurationMs()).isBetween(3_500_000L, 3_700_000L);
        assertThat(reloaded.getErrorMessage()).isEqualTo("Interrupted by application restart");
        assertThat(runRepository.hasRunningFor(job.getId())).isFalse();
    }

    @Test
    void finishedRunsAreLeftUntouched() {
        ScheduledJob job = seededJob();
        ScheduledJobRun done = save(job, Outcome.SUCCESS, Instant.now().minus(Duration.ofMinutes(5)));

        runRepository.failAllRunning(Instant.now(), "Interrupted by application restart");

        ScheduledJobRun reloaded = runRepository.findById(done.getId()).orElseThrow();
        assertThat(reloaded.getOutcome()).isEqualTo(Outcome.SUCCESS.name());
        assertThat(reloaded.getDurationMs()).isEqualTo(1000L);
        assertThat(reloaded.getErrorMessage()).isNull();
    }
}
