package com.fenixcore.optibienestar360.modules.scheduling.repository;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJobRun.Outcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, Long>, JpaSpecificationExecutor<ScheduledJobRun> {

    Optional<ScheduledJobRun> findByUuid(UUID uuid);

    Page<ScheduledJobRun> findByScheduledJobId(Long scheduledJobId, Pageable pageable);

    /**
     * Usage check for {@code ScheduledJobsService.countUsages}. {@code
     * ScheduledJobRun.scheduledJob} is a real {@code @ManyToOne} FK and
     * {@link com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob}
     * declares no reverse {@code @OneToMany} at all (no cascade to consider) —
     * so any run history genuinely blocks a hard delete of its job. Counts
     * ALL rows regardless of outcome.
     */
    long countByScheduledJobId(Long scheduledJobId);

    /**
     * Concurrency gate — used by {@code JobExecutionService} before starting
     * a new run when {@code allow_concurrent=false}. Backed by the partial
     * index {@code idx_scheduled_job_runs_running}.
     */
    boolean existsByScheduledJobIdAndOutcome(Long scheduledJobId, String outcome);

    default boolean hasRunningFor(Long scheduledJobId) {
        return existsByScheduledJobIdAndOutcome(scheduledJobId, Outcome.RUNNING.name());
    }
}
