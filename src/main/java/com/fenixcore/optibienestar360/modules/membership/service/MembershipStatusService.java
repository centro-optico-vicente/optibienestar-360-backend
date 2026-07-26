package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Date-driven lifecycle engine. Given a {@link Membership} and today's date,
 * decides which of {@link LifecycleStatus#ACTIVE},
 * {@link LifecycleStatus#SUSPENDED}, {@link LifecycleStatus#EXPIRED} should
 * be in the {@code status} column based on {@code next_due_date} +
 * {@code grace_period_days}.
 *
 * <p>State machine:</p>
 * <pre>
 *   today &lt;= next_due_date                       → ACTIVE
 *   next_due_date &lt; today &lt;= next_due_date+grace → SUSPENDED
 *   today &gt; next_due_date + grace                → EXPIRED
 * </pre>
 *
 * <p>{@link LifecycleStatus#CANCELED} is terminal and untouched. Soft-deleted
 * rows ({@code is_active = false}) are also untouched — they're historical.</p>
 *
 * <p>This service handles the <b>date-driven</b> half of the lifecycle. The
 * payment-driven half (recording a payment moves the membership back to
 * ACTIVE and advances {@code next_due_date}) lands with V22 payments — at
 * which point {@code PaymentService.approve} will call a sibling
 * {@code recordPayment(...)} method here.</p>
 *
 * <p>Daily job ({@code @Scheduled}) is its own bullet — it just calls
 * {@link #applyDueTransitions(LocalDate)}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipStatusService {

    private static final String REASON_SUSPENDED      = "Past due date";
    private static final String REASON_EXPIRED        = "Past grace period";
    private static final String REASON_ACTIVE_SUBSIDY = "Active by full subsidy";

    private final MembershipRepository repository;
    private final SubsidyResolver subsidyResolver;

    /**
     * Evaluator — no DB writes to the membership, safe to call from read-only
     * paths. Returns {@code null} when the row is outside the date-driven sweep
     * (soft-deleted or already {@code CANCELED}).
     *
     * <p>Subsidy-aware (vertical-5 #1 / PDF #1.b): a member with a full monthly
     * exoneration active today stays {@code ACTIVE} regardless of
     * {@code next_due_date} — no payment row required. A partial subsidy
     * (&lt; 100%) still owes the reduced amount, so the date logic applies
     * unchanged (#2).</p>
     */
    public LifecycleStatus evaluate(Membership membership, LocalDate today) {
        if (!membership.isActive()) return null;
        if (LifecycleStatus.CANCELED.name().equals(membership.getStatus())) return null;
        boolean fullyExonerated =
                subsidyResolver.fullMonthlyExoneration(membership.getMember().getId(), today);
        return evaluateCore(membership, today, fullyExonerated);
    }

    /**
     * Core state machine with the full-exoneration flag supplied by the caller —
     * lets the batch sweep pass a value pre-loaded in one query (no N+1).
     */
    private LifecycleStatus evaluateCore(Membership membership, LocalDate today, boolean fullyExonerated) {
        if (!membership.isActive()) return null;
        if (LifecycleStatus.CANCELED.name().equals(membership.getStatus())) return null;

        if (fullyExonerated) {
            return LifecycleStatus.ACTIVE;
        }

        LocalDate nextDue = membership.getNextDueDate();
        if (!today.isAfter(nextDue)) {
            return LifecycleStatus.ACTIVE;
        }

        LocalDate graceCutoff = nextDue.plusDays(membership.getGracePeriodDays());
        if (today.isAfter(graceCutoff)) {
            return LifecycleStatus.EXPIRED;
        }
        return LifecycleStatus.SUSPENDED;
    }

    /**
     * Apply the computed transition to a single membership. Returns
     * {@code true} when the status column actually changed, {@code false}
     * when it was already correct (or out of scope).
     *
     * <p>Caller is responsible for the surrounding transaction — typically
     * the batch driver or the unit-of-work that owns the membership.</p>
     */
    @Transactional
    public boolean applyTransition(Membership membership, LocalDate today) {
        LifecycleStatus target = evaluate(membership, today);
        if (target == null) return false;
        if (target.name().equals(membership.getStatus())) return false;

        membership.setStatus(target.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason(reasonFor(target));
        return true;
    }

    /**
     * Batch sweep — load every {@code ACTIVE}/{@code SUSPENDED} membership
     * whose {@code next_due_date} has already passed and apply the
     * resulting transition. Driven by the daily {@code @Scheduled} job
     * (separate bullet) and exposed for ad-hoc admin replays.
     */
    @Transactional
    public BatchResult applyDueTransitions(LocalDate today) {
        List<Membership> candidates = repository.findStatusEvaluationCandidates(today);

        // One query for the whole batch: which candidate members have a full
        // monthly exoneration today (vertical-5 #3 — subsidy check before EXPIRED).
        Set<Long> exonerated = subsidyResolver.memberIdsWithFullMonthlyExoneration(
                candidates.stream().map(m -> m.getMember().getId()).toList(), today);

        int suspended = 0;
        int expired   = 0;
        for (Membership membership : candidates) {
            boolean fullyExonerated = exonerated.contains(membership.getMember().getId());
            LifecycleStatus target = evaluateCore(membership, today, fullyExonerated);
            if (target == null || target.name().equals(membership.getStatus())) continue;

            membership.setStatus(target.name());
            membership.setLastStatusChangeAt(Instant.now());
            membership.setLastStatusChangeReason(reasonFor(target));

            if (target == LifecycleStatus.SUSPENDED) suspended++;
            else if (target == LifecycleStatus.EXPIRED) expired++;
        }
        return new BatchResult(candidates.size(), suspended, expired);
    }

    private static String reasonFor(LifecycleStatus target) {
        return switch (target) {
            case SUSPENDED -> REASON_SUSPENDED;
            case EXPIRED   -> REASON_EXPIRED;
            case ACTIVE    -> REASON_ACTIVE_SUBSIDY;   // only reached via subsidy short-circuit
            default        -> null;
        };
    }

    /** Summary returned by {@link #applyDueTransitions(LocalDate)}. */
    public record BatchResult(int scanned, int suspended, int expired) {}
}
