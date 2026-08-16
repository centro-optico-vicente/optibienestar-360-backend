package com.fenixcore.optibienestar360.modules.membership.repository;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MembershipRepository extends JpaRepository<Membership, Long>,
        JpaSpecificationExecutor<Membership> {

    Optional<Membership> findByUuid(UUID uuid);

    /** Pre-check before INSERT — the V21 partial UNIQUE on (member_id) WHERE is_active=TRUE only allows one. */
    boolean existsByMemberIdAndActiveTrue(Long memberId);

    /** Currently-active subscription of the member. */
    Optional<Membership> findFirstByMemberIdAndActiveTrue(Long memberId);

    /** Usage check for {@code MembersService.countUsages} — ALL rows (active + inactive). */
    long countByMemberId(Long memberId);

    /** Usage check for {@code PlansService.countUsages} — ALL rows (active + inactive). */
    long countByPlanId(Long planId);

    /** History of subscriptions for a member, newest enrollment first. */
    List<Membership> findByMemberIdOrderByEnrolledAtDesc(Long memberId);

    /**
     * Candidates the daily status job needs to look at: live (is_active=TRUE)
     * memberships in ACTIVE or SUSPENDED that have already crossed their
     * next_due_date. EXPIRED + CANCELED are excluded (terminal for this
     * sweep), as are not-yet-due rows.
     *
     * <p>The V21 partial composite index {@code (status, next_due_date)
     * WHERE is_active = TRUE} covers this query.</p>
     */
    @Query("""
            SELECT m FROM Membership m
            WHERE m.active = true
              AND m.status IN ('ACTIVE', 'SUSPENDED')
              AND m.nextDueDate < :today
            """)
    List<Membership> findStatusEvaluationCandidates(@Param("today") LocalDate today);

    /**
     * Payment-reminder job (vertical-9): live ACTIVE memberships whose
     * {@code next_due_date} falls exactly on {@code dueDate} (the job passes
     * {@code today + 3} so the reminder fires once, three days ahead of the
     * due date). Backed by the V21 partial composite index.
     */
    List<Membership> findByActiveTrueAndStatusAndNextDueDate(String status, LocalDate dueDate);

    /**
     * Grace-period job (vertical-9): all live SUSPENDED memberships (past due,
     * still inside the grace window). The set is small — the runner filters in
     * memory to the per-membership "N days before expiry" nudge day, since the
     * grace length is a per-row snapshot.
     */
    List<Membership> findByActiveTrueAndStatus(String status);
}
