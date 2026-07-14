package com.fenixcore.optibienestar360.modules.scheduling.config;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the registry — focuses on the JVM-local
 * {@code scheduledFutures} map invariant (see "Storage layers" in the
 * design plan).
 *
 * <p>What we assert: register adds an entry + schedules a task; unregister
 * cancels and removes it; reschedule replaces the entry with a different
 * ScheduledFuture instance; the Trigger lambda re-reads the cron from the
 * repository on each computation so live admin edits propagate.</p>
 *
 * <p>We do NOT test that cron actually fires at the right wall-clock time —
 * that is Spring's CronTrigger contract.</p>
 */
@ExtendWith(MockitoExtension.class)
class DynamicScheduledJobsRegistryTest {

    @Mock private ScheduledJobRepository jobRepository;
    @Mock private JobExecutionService executionService;
    @Mock private TaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> firstFuture;
    @Mock private ScheduledFuture<?> secondFuture;

    private DynamicScheduledJobsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DynamicScheduledJobsRegistry(jobRepository, executionService, taskScheduler);
    }

    @Test
    void register_storesScheduledFuture_andSchedulesTask() {
        ScheduledJob job = jobFixture("MEMBERSHIP_STATUS_SWEEP", "0 0 3 * * *", true, true);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture);

        registry.register(job);

        assertThat(registry.isRegistered("MEMBERSHIP_STATUS_SWEEP")).isTrue();
        assertThat(registry.registeredCount()).isEqualTo(1);
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void register_skipsWhenJobDisabledOrInactive() {
        // No stub needed — register returns early before reaching TaskScheduler
        ScheduledJob disabled = jobFixture("X", "0 0 3 * * *", false, true);
        registry.register(disabled);
        assertThat(registry.isRegistered("X")).isFalse();

        ScheduledJob softDeleted = jobFixture("Y", "0 0 3 * * *", true, false);
        registry.register(softDeleted);
        assertThat(registry.isRegistered("Y")).isFalse();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void register_isIdempotent_cancelsPreviousFuture() {
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture)
                .thenAnswer(inv -> secondFuture);

        ScheduledJob job = jobFixture("DUP", "0 0 3 * * *", true, true);
        registry.register(job);
        registry.register(job);

        verify(firstFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        assertThat(registry.registeredCount()).isEqualTo(1);
    }

    @Test
    void unregister_cancelsAndRemoves() {
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture);

        ScheduledJob job = jobFixture("BYE", "0 0 3 * * *", true, true);
        registry.register(job);
        registry.unregister("BYE");

        verify(firstFuture).cancel(false);
        assertThat(registry.isRegistered("BYE")).isFalse();
    }

    @Test
    void unregister_isNoop_whenCodeUnknown() {
        registry.unregister("DOES_NOT_EXIST");
        verify(firstFuture, never()).cancel(false);
    }

    @Test
    void reschedule_swapsTheFutureInstance() {
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture)
                .thenAnswer(inv -> secondFuture);

        ScheduledJob original = jobFixture("HOT", "0 0 3 * * *", true, true);
        registry.register(original);

        ScheduledJob edited = jobFixture("HOT", "0 0 5 * * *", true, true);
        registry.reschedule(edited);

        verify(firstFuture).cancel(false);
        assertThat(registry.registeredCount()).isEqualTo(1);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void trigger_relreads_cron_from_db_each_nextExecution() {
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture);

        ScheduledJob initial = jobFixture("LIVE", "0 0 3 * * *", true, true);
        registry.register(initial);

        // Capture the Trigger lambda that was registered
        ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
        Trigger trigger = captor.getValue();

        // Stub the repo to return an updated row when the trigger runs
        ScheduledJob updated = jobFixture("LIVE", "0 0 5 * * *", true, true);
        when(jobRepository.findByCode("LIVE")).thenReturn(Optional.of(updated));

        // Calling nextExecution should hit the repository — proving the
        // cron is not cached in memory. Return value is the next firing
        // instant from CronTrigger; only what matters here is the repo hit.
        trigger.nextExecution(new EmptyTriggerContext());
        verify(jobRepository).findByCode("LIVE");
    }

    @Test
    void trigger_returnsNull_when_job_disabled_at_fire_time() {
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(inv -> firstFuture);

        ScheduledJob initial = jobFixture("LIVE", "0 0 3 * * *", true, true);
        registry.register(initial);
        ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());

        // Job got disabled in the DB after registration; trigger should
        // return null (no more fires) without throwing
        ScheduledJob disabled = jobFixture("LIVE", "0 0 3 * * *", false, true);
        when(jobRepository.findByCode("LIVE")).thenReturn(Optional.of(disabled));

        assertThat(captor.getValue().nextExecution(new EmptyTriggerContext())).isNull();
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private static ScheduledJob jobFixture(String code, String cron, boolean enabled, boolean active) {
        ScheduledJob job = new ScheduledJob();
        job.setUuid(UUID.randomUUID());
        job.setCode(code);
        job.setDisplayName(code);
        job.setCronExpression(cron);
        job.setTimezone("America/Caracas");
        job.setEnabled(enabled);
        job.setActive(active);
        return job;
    }

    /** Minimal {@code TriggerContext} for invoking the trigger directly. */
    private static class EmptyTriggerContext implements org.springframework.scheduling.TriggerContext {
        @Override public java.time.Clock getClock() { return java.time.Clock.systemDefaultZone(); }
        @Override public java.time.Instant lastScheduledExecution() { return null; }
        @Override public java.time.Instant lastActualExecution() { return null; }
        @Override public java.time.Instant lastCompletion() { return null; }
    }
}
