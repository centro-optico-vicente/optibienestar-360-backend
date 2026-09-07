package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterSupervisorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Insert-only audit log of supervisor reassignments (V101). No specification
 * executor — the only writes are appends from {@code
 * PromoterHierarchyService.assignSupervisor}; reads are the per-promoter
 * history and the "vigente at a given cut" lookup.
 */
@Transactional(readOnly = true)
public interface PromoterSupervisorAssignmentRepository extends JpaRepository<PromoterSupervisorAssignment, Long> {

    List<PromoterSupervisorAssignment> findByPromoterIdOrderByCreatedAtDesc(Long promoterId);

    List<PromoterSupervisorAssignment> findByPromoter_UuidOrderByCreatedAtDesc(UUID promoterUuid);

    /**
     * The row vigente as of {@code asOf} for a given promoter — most recent
     * assignment with {@code createdAt <= asOf}. Empty means "no supervisor
     * assignment had happened yet at that point in time" (not necessarily
     * "no supervisor today").
     */
    Optional<PromoterSupervisorAssignment> findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long promoterId, Instant asOf);

    /** Usage check for {@code PromotersService.countUsages} — as the assignment's source supervisor. */
    long countByFromSupervisorId(Long promoterId);

    /** Usage check for {@code PromotersService.countUsages} — as the assignment's target supervisor. */
    long countByToSupervisorId(Long promoterId);

    /**
     * Every promoter that was EVER assigned {@code supervisorId} as their
     * supervisor (candidate set for {@code
     * PromoterHierarchyService.resolveDirectSubordinatesAt}) — the caller
     * still must re-check "vigente as of asOf" per candidate, since a past
     * subordinate may have since moved to another supervisor.
     */
    @Query("SELECT DISTINCT a.promoter.id FROM PromoterSupervisorAssignment a WHERE a.toSupervisor.id = :supervisorId")
    List<Long> findDistinctPromoterIdByToSupervisorId(@Param("supervisorId") Long supervisorId);

    /** Same as above, from the "moved away from" side — covers promoters currently unassigned who once reported here. */
    @Query("SELECT DISTINCT a.promoter.id FROM PromoterSupervisorAssignment a WHERE a.fromSupervisor.id = :supervisorId")
    List<Long> findDistinctPromoterIdByFromSupervisorId(@Param("supervisorId") Long supervisorId);
}
