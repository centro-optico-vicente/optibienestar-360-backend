package com.fenixcore.optibienestar360.modules.scheduling.repository;

import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long>,
        JpaSpecificationExecutor<ScheduledJob> {

    Optional<ScheduledJob> findByUuid(UUID uuid);

    /** Lookup by natural key — used by the registry and runners to resolve their config row. */
    Optional<ScheduledJob> findByCode(String code);

    boolean existsByCode(String code);

    /** Used by the registry on {@code ApplicationReadyEvent} to load every job that should be wired up. */
    List<ScheduledJob> findAllByActiveTrueAndEnabledTrue();
}
