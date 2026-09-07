package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily job (vertical-9): "vencidos en período de gracia". Finds SUSPENDED
 * memberships (past due, still inside their grace window) and enqueues a
 * {@code payment-overdue} nudge a few days before the grace period expires —
 * distinct from the one-shot {@code membership-suspended} notice the status
 * sweep already sent on the transition day. Seeded as {@code MEMBERSHIP_GRACE}
 * in {@code scheduled_jobs} (V40).
 *
 * <p>The grace length is a per-membership snapshot, so the nudge day is
 * computed per row: {@code next_due_date + max(1, gracePeriodDays - 3)} —
 * roughly three days before expiry, clamped so short grace windows still fire.
 * The SUSPENDED set is small (only rows currently in grace), so filtering in
 * memory is cheap.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipGraceJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_GRACE";

    private static final String TEMPLATE = "payment-overdue";
    private static final String SUBJECT_KEY = "email.payment.overdue.subject";
    private static final int DAYS_BEFORE_EXPIRY = 3;

    private final ScheduledJobRepository jobRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipReminderEnqueuer enqueuer;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @Transactional
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<Membership> suspended = membershipRepository
                .findByActiveTrueAndStatus(LifecycleStatus.SUSPENDED.name());

        int notified = 0;
        int skipped = 0;
        for (Membership membership : suspended) {
            if (!isNudgeDay(membership, today)) {
                continue;
            }
            if (enqueuer.enqueue(membership, TEMPLATE, SUBJECT_KEY)) {
                notified++;
            } else {
                skipped++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("suspendedScanned", suspended.size());
        summary.put("notified", notified);
        summary.put("skipped", skipped);
        log.info("MEMBERSHIP_GRACE: today={} suspendedScanned={} notified={} skipped={}",
                today, suspended.size(), notified, skipped);
        return JobRunResult.success(summary);
    }

    /** The per-membership nudge day: ~{@value #DAYS_BEFORE_EXPIRY} days before grace expiry, clamped. */
    private static boolean isNudgeDay(Membership membership, LocalDate today) {
        int offset = Math.max(1, membership.getGracePeriodDays() - DAYS_BEFORE_EXPIRY);
        LocalDate nudgeDay = membership.getNextDueDate().plusDays(offset);
        return today.isEqual(nudgeDay);
    }

    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return AppTimeZone.ZONE;
                    }
                })
                .orElse(AppTimeZone.ZONE);
    }
}
