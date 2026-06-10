package com.fenixcore.optisaludplus.modules.scheduling.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.scheduling.config.DynamicScheduledJobsRegistry;
import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobCreateRequest;
import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobDto;
import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobRunDto;
import com.fenixcore.optisaludplus.modules.scheduling.dto.ScheduledJobUpdateRequest;
import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optisaludplus.modules.scheduling.entity.ScheduledJobRun;
import com.fenixcore.optisaludplus.modules.scheduling.mapper.ScheduledJobMapper;
import com.fenixcore.optisaludplus.modules.scheduling.mapper.ScheduledJobRunMapper;
import com.fenixcore.optisaludplus.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optisaludplus.modules.scheduling.repository.ScheduledJobRunRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD over {@link ScheduledJob} plus the read surface for
 * {@link ScheduledJobRun} history (single + paginated list).
 *
 * <p>Hot-reload coordination: after each mutation that affects scheduling
 * (cron, timezone, enabled, allowConcurrent, active, soft-delete), the
 * service registers a {@link TransactionSynchronization#afterCommit()}
 * hook to push the change into {@link DynamicScheduledJobsRegistry}. The
 * registry is injected via {@link ObjectProvider} because it's
 * conditional on {@code app.scheduler.enabled=true} — on a replica with
 * scheduler off, the provider returns empty and no push happens (admin
 * mutations still persist, the registry on the scheduler-enabled
 * replica's next refetch or re-deploy picks them up).</p>
 *
 * <p>Plural name (<i>ScheduledJobsService</i>) mirrors the convention used
 * by {@code PlansService}, {@code MembershipsService}, {@code AlliesService}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledJobsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "code", "displayName", "cronExpression", "timezone",
            "enabled", "allowConcurrent", "maxSyncSeconds", "lockHeld",
            "lastRunAt", "lastRunStatus", "nextRunAt",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"code", "displayName", "description"};

    private final ScheduledJobRepository jobRepository;
    private final ScheduledJobRunRepository runRepository;
    private final ScheduledJobMapper mapper;
    private final ScheduledJobRunMapper runMapper;
    private final ObjectProvider<DynamicScheduledJobsRegistry> registryProvider;

    // ─── Job read ──────────────────────────────────────────────────────────

    public ScheduledJobDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    public Page<ScheduledJobDto> list(Pageable pageable, String filter, String q) {
        Specification<ScheduledJob> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "scheduled_job.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return jobRepository.findAll(spec, pageable).map(mapper::toDto);
    }

    // ─── Job create ────────────────────────────────────────────────────────

    @Transactional
    public ScheduledJobDto create(ScheduledJobCreateRequest req) {
        if (jobRepository.existsByCode(req.code())) {
            throw new IllegalArgumentException("scheduled_job.code.duplicate");
        }
        validateCron(req.cronExpression());
        validateTimezone(req.timezone());

        ScheduledJob job = new ScheduledJob();
        job.setCode(req.code());
        job.setDisplayName(req.displayName());
        job.setDescription(req.description());
        job.setCronExpression(req.cronExpression());
        job.setTimezone(req.timezone());
        if (req.enabled() != null)          job.setEnabled(req.enabled());
        if (req.allowConcurrent() != null)  job.setAllowConcurrent(req.allowConcurrent());
        if (req.maxSyncSeconds() != null)   job.setMaxSyncSeconds(req.maxSyncSeconds());

        ScheduledJob saved = jobRepository.save(job);
        scheduleAfterCommit(saved.getUuid(), Action.REGISTER);
        return mapper.toDto(saved);
    }

    // ─── Job update ────────────────────────────────────────────────────────

    @Transactional
    public ScheduledJobDto update(UUID uuid, ScheduledJobUpdateRequest req) {
        ScheduledJob job = findManaged(uuid);
        boolean scheduleAffected = false;

        if (req.displayName() != null)     job.setDisplayName(req.displayName());
        if (req.description() != null)     job.setDescription(req.description());

        if (req.cronExpression() != null) {
            validateCron(req.cronExpression());
            job.setCronExpression(req.cronExpression());
            scheduleAffected = true;
        }
        if (req.timezone() != null) {
            validateTimezone(req.timezone());
            job.setTimezone(req.timezone());
            scheduleAffected = true;
        }
        if (req.enabled() != null) {
            job.setEnabled(req.enabled());
            scheduleAffected = true;
        }
        if (req.allowConcurrent() != null) job.setAllowConcurrent(req.allowConcurrent());
        if (req.maxSyncSeconds() != null)  job.setMaxSyncSeconds(req.maxSyncSeconds());
        if (req.active() != null) {
            job.setActive(req.active());
            scheduleAffected = true;
        }
        if (req.status() != null)          job.setStatus(req.status());

        if (scheduleAffected) {
            // Final state determines action: enabled && active → reschedule;
            // anything else → unregister.
            Action action = (job.isEnabled() && job.isActive()) ? Action.RESCHEDULE : Action.UNREGISTER;
            scheduleAfterCommit(job.getUuid(), action);
        }
        return mapper.toDto(job);  // managed → dirty-check flushes on commit
    }

    // ─── Job soft-delete ───────────────────────────────────────────────────

    @Transactional
    public void delete(UUID uuid) {
        ScheduledJob job = findManaged(uuid);
        job.setActive(false);
        job.setEnabled(false);
        scheduleAfterCommit(job.getUuid(), Action.UNREGISTER);
    }

    // ─── Runs (single + history) ───────────────────────────────────────────

    public ScheduledJobRunDto getRun(UUID jobUuid, UUID runUuid) {
        ScheduledJob job = findManaged(jobUuid);
        ScheduledJobRun run = runRepository.findByUuid(runUuid)
                .orElseThrow(() -> new NoSuchElementException("scheduled_job.run.not_found"));
        if (!run.getScheduledJob().getId().equals(job.getId())) {
            throw new NoSuchElementException("scheduled_job.run.not_found");
        }
        return runMapper.toDto(run);
    }

    public Page<ScheduledJobRunDto> listRuns(UUID jobUuid, Pageable pageable) {
        ScheduledJob job = findManaged(jobUuid);
        return runRepository.findByScheduledJobIdOrderByStartedAtDesc(job.getId(), pageable)
                .map(runMapper::toDto);
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    private ScheduledJob findManaged(UUID uuid) {
        return jobRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("scheduled_job.not_found"));
    }

    private static void validateCron(String expression) {
        try {
            CronExpression.parse(expression);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scheduled_job.cron.invalid");
        }
    }

    private static void validateTimezone(String zone) {
        try {
            ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("scheduled_job.timezone.invalid");
        }
    }

    private static Specification<ScheduledJob> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /**
     * Captures the registry action to perform after the current transaction
     * commits. Re-fetches the job from the DB inside the hook so the
     * registry always sees the post-commit state (avoids race with parallel
     * mutations and works even if Hibernate has not yet flushed when the
     * hook is scheduled).
     */
    private void scheduleAfterCommit(UUID jobUuid, Action action) {
        DynamicScheduledJobsRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) return;  // scheduler disabled on this replica

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobRepository.findByUuid(jobUuid).ifPresent(fresh -> {
                    switch (action) {
                        case REGISTER -> registry.register(fresh);
                        case RESCHEDULE -> registry.reschedule(fresh);
                        case UNREGISTER -> registry.unregister(fresh.getCode());
                    }
                });
            }
        });
    }

    private enum Action {
        REGISTER, RESCHEDULE, UNREGISTER
    }
}
